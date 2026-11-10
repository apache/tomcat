/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.juli.logging;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.Assert;
import org.junit.Test;

import org.apache.juli.logging.provider.TesterLogFactoryProvider;

public class TestLogFactory {

    private static final String NO_STRING_CONSTRUCTOR = TesterLogNoStringConstructor.class.getName();
    private static final String STRING_CONSTRUCTOR = TesterLogStringConstructor.class.getName();
    private static final String MISSING = "org.apache.juli.logging.TesterMissingProvider";
    private static final String DIRECT_JDK_LOG = DirectJDKLog.class.getName();

    /*
     * A provider without the constructor taking the log name cannot be used. It must be
     * ignored, with the discovery falling back to DirectJDKLog. Before the fix a raw Error
     * was thrown from the singleton initialization, so all later calls failed with
     * NoClassDefFoundError.
     */
    @Test
    public void testProviderWithoutStringConstructorIsIgnored() throws Exception {
        Object log = getLogFromIsolatedFactory(NO_STRING_CONSTRUCTOR);
        Assert.assertEquals(DIRECT_JDK_LOG, log.getClass().getName());
    }


    /*
     * A provider that names a class that does not exist makes the ServiceLoader throw a
     * ServiceConfigurationError while iterating. That must not prevent discovery either.
     */
    @Test
    public void testMissingProviderClassIsIgnored() throws Exception {
        Object log = getLogFromIsolatedFactory(MISSING);
        Assert.assertEquals(DIRECT_JDK_LOG, log.getClass().getName());
    }


    /*
     * A syntactically invalid line in a services file also makes the ServiceLoader throw
     * a ServiceConfigurationError. The factory must fall back instead of failing. Note
     * that the JDK abandons the whole file in this case, so a usable provider in the same
     * file is not found and DirectJDKLog is used.
     */
    @Test
    public void testMalformedProviderLineIsIgnored() throws Exception {
        Object log = getLogFromIsolatedFactory("not a class name!!");
        Assert.assertEquals(DIRECT_JDK_LOG, log.getClass().getName());
    }


    /*
     * Discovery must continue past unusable providers and use the first usable one.
     */
    @Test
    public void testUsableProviderAfterUnusableOnesIsDiscovered() throws Exception {
        Object log = getLogFromIsolatedFactory(MISSING, NO_STRING_CONSTRUCTOR, STRING_CONSTRUCTOR);
        Assert.assertEquals(STRING_CONSTRUCTOR, log.getClass().getName());
    }


    /*
     * The regular case: a usable provider is discovered and used. Calling getLog() twice
     * also pins that the singleton initialization does not fail on first use.
     */
    @Test
    public void testUsableProviderIsDiscovered() throws Exception {
        Object log = getLogFromIsolatedFactory(STRING_CONSTRUCTOR);
        Assert.assertEquals(STRING_CONSTRUCTOR, log.getClass().getName());
    }


    /*
     * Without any provider on the class path the fallback to DirectJDKLog must work.
     */
    @Test
    public void testNoProviderFallsBackToDirectJDKLog() throws Exception {
        Object log = getLogFromIsolatedFactory();
        Assert.assertEquals(DIRECT_JDK_LOG, log.getClass().getName());
    }


    @Test
    public void testResourceLookupFailureStopsDiscovery() throws Exception {
        doTestResourceLookupFailureStopsDiscovery(false);
    }


    @Test
    public void testDeniedResourceLookupStopsDiscovery() throws Exception {
        doTestResourceLookupFailureStopsDiscovery(true);
    }


    private void doTestResourceLookupFailureStopsDiscovery(boolean denied) throws Exception {
        URL[] urls = { codeSourceOf(LogFactory.class), codeSourceOf(TestLogFactory.class) };
        int[] lookups = new int[1];
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals("META-INF/services/" + Log.class.getName())) {
                    // Fail a repeated lookup immediately rather than letting a broken implementation loop forever.
                    Assert.assertEquals("The failing resource enumeration was retried", 1, ++lookups[0]);
                    if (denied) {
                        throw new SecurityException("Test resource lookup denial");
                    }
                    throw new IOException("Test resource lookup failure");
                }
                return super.getResources(name);
            }
        }) {
            Assert.assertEquals(DIRECT_JDK_LOG, getLogFromFactory(loader).getClass().getName());
            Assert.assertEquals(1, lookups[0]);
        }
    }


    @Test
    public void testUnreadableServiceFilesDoNotHideUsableProvider() throws Exception {
        Path tempDir = Files.createTempDirectory("juli-logfactory-unreadable-test");
        try {
            Path service = tempDir.resolve("readable.services");
            Files.writeString(service, STRING_CONSTRUCTOR + System.lineSeparator(), StandardCharsets.UTF_8);
            List<URL> resources = List.of(tempDir.resolve("missing.services").toUri().toURL(),
                    tempDir.resolve("also-missing.services").toUri().toURL(), service.toUri().toURL());
            URL[] urls = { codeSourceOf(LogFactory.class), codeSourceOf(TestLogFactory.class) };
            try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
                @Override
                public Enumeration<URL> getResources(String name) throws IOException {
                    if (name.equals("META-INF/services/" + Log.class.getName())) {
                        return Collections.enumeration(resources);
                    }
                    return super.getResources(name);
                }
            }) {
                Assert.assertEquals(STRING_CONSTRUCTOR, getLogFromFactory(loader).getClass().getName());
            }
        } finally {
            deleteRecursively(tempDir.toFile());
        }
    }


    @Test
    public void testModuleFactoryProviderIsDiscovered() throws Exception {
        doTestModuleFactoryProvider(TesterLogFactoryProvider.class, true);
    }


    @Test
    public void testModuleFactoryProviderReturningBaseClassIsDiscovered() throws Exception {
        doTestModuleFactoryProvider(TesterLogFactoryProvider.BaseFactory.class, true);
    }


    /*
     * A provider whose package is not exported to the logging module cannot be instantiated by it, so it must be
     * ignored at discovery rather than make every later call to getLog() fail.
     */
    @Test
    public void testModuleProviderInUnexportedPackageIsIgnored() throws Exception {
        doTestModuleFactoryProvider(TesterLogFactoryProvider.class, false);
    }


    private void doTestModuleFactoryProvider(Class<?> providerClass, boolean exported) throws Exception {
        Path tempDir = Files.createTempDirectory("juli-logfactory-module-test");
        try {
            Path modules = tempDir.resolve("modules");
            Path juli = modules.resolve("test.juli");
            copyClasses(juli, Log.class, LogFactory.class, LogConfigurationException.class, DirectJDKLog.class,
                    TesterLogStringConstructor.class);
            copyClasses(juli, LogFactory.class.getDeclaredClasses());
            compileModule(tempDir.resolve("juli-src"), juli, "test.juli",
                    "module test.juli { requires java.logging; exports org.apache.juli.logging; " +
                            "uses org.apache.juli.logging.Log; }", modules);

            Path provider = modules.resolve("test.provider");
            copyClasses(provider, TesterLogFactoryProvider.class, TesterLogFactoryProvider.NamedLog.class,
                    TesterLogFactoryProvider.BaseFactory.class);
            compileModule(tempDir.resolve("provider-src"), provider, "test.provider",
                    "module test.provider { requires test.juli; " +
                            (exported ? "exports org.apache.juli.logging.provider; " : "") +
                            "provides org.apache.juli.logging.Log with " + providerClass.getCanonicalName() +
                            "; }", modules);

            ModuleFinder finder = ModuleFinder.of(modules);
            Configuration configuration = ModuleLayer.boot().configuration().resolve(finder, ModuleFinder.of(),
                    Set.of("test.provider"));
            ModuleLayer layer = ModuleLayer.boot().defineModulesWithOneLoader(configuration,
                    ClassLoader.getPlatformClassLoader());
            Object log = getLogFromFactory(layer.findLoader("test.provider"));
            Assert.assertEquals(exported ? TesterLogFactoryProvider.NamedLog.class.getName() : DIRECT_JDK_LOG,
                    log.getClass().getName());
        } finally {
            deleteRecursively(tempDir.toFile());
        }
    }


    private static void copyClasses(Path directory, Class<?>... classes) throws IOException {
        for (Class<?> clazz : classes) {
            String resource = clazz.getName().replace('.', '/') + ".class";
            Path target = directory.resolve(resource);
            Files.createDirectories(target.getParent());
            try (InputStream input = clazz.getResourceAsStream('/' + resource)) {
                Assert.assertNotNull(resource, input);
                Files.copy(input, target);
            }
        }
    }


    private static void compileModule(Path sources, Path output, String name, String descriptor, Path modules)
            throws IOException {
        Files.createDirectories(sources);
        Path source = sources.resolve("module-info.java");
        Files.writeString(source, descriptor, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assert.assertNotNull("A JDK compiler is required", compiler);
        Assert.assertEquals("Module compilation failed", 0, compiler.run(null, null, null,
                "--module-path", modules.toString(), "--patch-module", name + '=' + output,
                "-d", output.toString(), source.toString()));
    }


    /*
     * Loads a fresh copy of the LogFactory in an isolated class loader in which the given
     * names are registered as the only providers of the Log service, calls getLog() twice
     * and returns the class name of the resulting Log instance. The returned object is of
     * a Log type defined by the isolated class loader, so it can only be inspected as an
     * Object.
     */
    private Object getLogFromIsolatedFactory(String... providerNames) throws Exception {
        Path tempDir = Files.createTempDirectory("juli-logfactory-test");
        try {
            Path servicesDir = Files.createDirectories(tempDir.resolve("META-INF/services"));
            StringBuilder servicesFile = new StringBuilder();
            for (String providerName : providerNames) {
                servicesFile.append(providerName).append(System.lineSeparator());
            }
            Files.write(servicesDir.resolve(Log.class.getName()),
                    servicesFile.toString().getBytes(StandardCharsets.UTF_8));

            // The platform class loader as parent keeps the Log interface and the
            // LogFactory of this class loader out of the way, so the isolated copy
            // of the factory really runs its own discovery
            URL[] urls = { codeSourceOf(LogFactory.class), codeSourceOf(TestLogFactory.class),
                    tempDir.toUri().toURL() };
            try (URLClassLoader isolatedLoader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                return getLogFromFactory(isolatedLoader);
            }
        } finally {
            deleteRecursively(tempDir.toFile());
        }
    }


    private static Object getLogFromFactory(ClassLoader loader) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalLoader = currentThread.getContextClassLoader();
        // The ServiceLoader used by LogFactory looks at the context class loader.
        currentThread.setContextClassLoader(loader);
        try {
            Class<?> isolatedFactory = loader.loadClass(LogFactory.class.getName());
            Object result = null;
            // Call twice, the second call must not fail with NoClassDefFoundError.
            for (int i = 0; i < 2; i++) {
                result = isolatedFactory.getMethod("getLog", String.class).invoke(null, "tester");
                Assert.assertNotNull(result);
            }
            return result;
        } finally {
            currentThread.setContextClassLoader(originalLoader);
        }
    }


    private static URL codeSourceOf(Class<?> clazz) throws Exception {
        return clazz.getProtectionDomain().getCodeSource().getLocation();
    }


    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        Assert.assertTrue("Failed to delete [" + file + "]", file.delete());
    }
}
