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
package org.apache.catalina.util;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests the opt-in {@code ServerInfo.properties} merge behaviour enabled by the
 * {@code org.apache.catalina.util.LOAD_SERVER_INFO_OVERRIDE} system property.
 * <p>
 * The default behaviour (property unset) is unchanged and is covered by
 * {@code TestServerInfo}: only the first {@code ServerInfo.properties} on the
 * class path is read, with no merging. When the property is set to {@code true}
 * every {@code ServerInfo.properties} on the class path is merged so an override
 * file can replace individual properties while the rest keep their bundled
 * values.
 * <p>
 * {@link ServerInfo} reads its values in a static initializer that runs once per
 * class loader, and {@link org.apache.catalina.Globals} reads the controlling
 * system property once when it is initialized. To exercise the real code this
 * test loads a fresh copy of both classes in an isolated {@link URLClassLoader}
 * whose class path places an override {@code ServerInfo.properties} ahead of a
 * bundled one.
 */
public class TestServerInfoOverride {

    private static final String PROP = "org.apache.catalina.util.LOAD_SERVER_INFO_OVERRIDE";
    private static final String RESOURCE = "org/apache/catalina/util/ServerInfo.properties";

    private static final String BUNDLED =
            "server.info=Apache Tomcat/99.0.0\nserver.number=99.0.0\n" +
            "server.built=Jan 1 2026\nserver.built.iso=2026-01-01\n";

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    /**
     * With the feature enabled, values present in the override file win while
     * values it does not set fall back to the bundled defaults.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testPartialOverrideMergedWhenEnabled() throws Exception {
        File overrideDir = writeProps("override",
                "server.info=Custom Tomcat/1.2.3\nserver.number=1.2.3\n");
        File bundledDir = writeProps("bundled", BUNDLED);

        Properties result = loadServerInfo(true, overrideDir, bundledDir);

        // Set by the override file - the override wins.
        Assert.assertEquals("Custom Tomcat/1.2.3", result.getProperty("server.info"));
        Assert.assertEquals("1.2.3", result.getProperty("server.number"));
        // Not set by the override file - the bundled defaults are retained.
        Assert.assertEquals("Jan 1 2026", result.getProperty("server.built"));
        Assert.assertEquals("2026-01-01", result.getProperty("server.built.iso"));
    }

    /**
     * Create {@code <name>/org/apache/catalina/util/ServerInfo.properties} under
     * the temporary folder with the supplied content and return the root
     * directory so it can be added to a class path.
     */
    private File writeProps(String name, String content) throws Exception {
        File root = tmp.newFolder(name);
        File file = new File(root, RESOURCE);
        Files.createDirectories(file.getParentFile().toPath());
        try (OutputStream os = Files.newOutputStream(file.toPath())) {
            os.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }

    /**
     * Load a fresh {@link ServerInfo} in an isolated class loader with the given
     * override state and class path prefix, and return its resolved values.
     * <p>
     * The supplied directories are placed at the front of the class path in
     * order, so - once {@link ServerInfo} reverses the discovered resources when
     * the feature is enabled - the later directories act as defaults that the
     * earlier ones override.
     */
    private Properties loadServerInfo(boolean overrideEnabled, File... classpathPrefix)
            throws Exception {

        List<URL> urls = new ArrayList<>();
        for (File dir : classpathPrefix) {
            urls.add(dir.toURI().toURL());
        }
        // Include the current class path so the isolated loader can resolve the
        // Tomcat classes (and their dependencies) it needs to load fresh.
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(new File(entry).toURI().toURL());
        }

        String previous = System.getProperty(PROP);
        if (overrideEnabled) {
            System.setProperty(PROP, "true");
        } else {
            System.clearProperty(PROP);
        }

        // Parent is the platform class loader so that ServerInfo and Globals are
        // loaded fresh from our URLs (and thus re-run their static initializers)
        // rather than being delegated to the application class loader.
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]),
                ClassLoader.getPlatformClassLoader())) {

            Class<?> clazz = Class.forName("org.apache.catalina.util.ServerInfo", true, loader);

            Properties result = new Properties();
            result.setProperty("server.info", invoke(clazz, "getServerInfo"));
            result.setProperty("server.number", invoke(clazz, "getServerNumber"));
            result.setProperty("server.built", invoke(clazz, "getServerBuilt"));
            result.setProperty("server.built.iso", invoke(clazz, "getServerBuiltISO"));
            return result;
        } finally {
            if (previous == null) {
                System.clearProperty(PROP);
            } else {
                System.setProperty(PROP, previous);
            }
        }
    }

    private static String invoke(Class<?> clazz, String method) throws Exception {
        Method m = clazz.getMethod(method);
        return (String) m.invoke(null);
    }
}
