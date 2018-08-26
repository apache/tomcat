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
package javax.el;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.compat.JreCompat;

public class TestImportHandlerStandardPackages {

    @Test
    public void testClassListsAreComplete() throws Exception {
        // Use reflection to get hold of the internal Map
        Class<?> clazz = ImportHandler.class;
        Field f = clazz.getDeclaredField("standardPackages");
        f.setAccessible(true);
        Object obj = f.get(null);

        Assert.assertTrue("Not Map", obj instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String,Set<String>> standardPackageName = (Map<String, Set<String>>) obj;

        for (Map.Entry<String,Set<String>> entry : standardPackageName.entrySet()) {
            checkPackageClassList(entry.getKey(), entry.getValue());
        }
    }


    private void checkPackageClassList(String packageName, Set<String> classNames) throws Exception {

        if ("java.lang".equals(packageName)) {
            // The code below is designed to run on Java 9 so skip this check
            // if running on Java 8. The test has previously been run with Java
            // 9 (and later) so it is not necessary that this is executed on
            // every test run. The intention is that it will catch new classes
            // when the tests are run on a newer JRE.
            // The latest version of the JRE where this test is known to pass is
            // Java 11, Early Access 21
            if (!JreCompat.isJre9Available()) {
                return;
            }
            getJavaBaseClasses().filter(c -> (c.startsWith("java/lang/")))
                    .filter(c -> c.lastIndexOf('/') == 9)             // Exclude sub-packages
                    .filter(c -> c.endsWith(".class"))                // Exclude non-class resources
                    .map(c -> c.substring(10, c.length() - 6))        // Extract class name
                    .map(c-> {
                        try {
                            return Class.forName("java.lang." + c);   // Get the class object
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException();
                        }
                    })
                    .filter(c -> Modifier.isPublic(c.getModifiers())) // Exclude non-public classes
                    .map(c -> c.getName().substring(10))              // Back to the class name
                    .map(c -> c.replace('$',  '.'))
                    .filter(c -> !classNames.contains(c))             // Skip classes already listed
                    .filter(c -> !c.startsWith("FdLibm."))            // Skip public inner class
                    .filter(c -> !c.startsWith("LiveStackFrame."))    // Skip public inner class
                    .filter(c -> !c.startsWith("WeakPairMap."))       // Skip public inner class
                    .forEach(c -> Assert.fail("java.lang." + c));     // Should have in list
        } else {
            // When this test runs, the class loader will be loading resources
            // from a directory for each of these packages.
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = cl.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                // Debugging for Gump failure
                System.out.println("Scanning: [" + resource + "]");
                URI uri = resource.toURI();
                // Gump includes some JARs on classpath - skip them
                if (!"file".equals(uri.getScheme())) {
                    continue;
                }
                File dir = new File(uri);

                String[] files = dir.list();
                Assert.assertNotNull(files);
                for (String file : files) {
                    if (!file.endsWith(".class")) {
                        // Skip non-class resoucres
                        continue;
                    }
                    if (file.startsWith("Test")) {
                        // Skip test resources
                        continue;
                    }
                    if (file.matches(".*\\$[0-9]?\\.class")) {
                        // Skip anonymous inner classes
                        continue;
                    }
                    String name = file.substring(0, file.length() - 6);
                    name = name.replace('$', '.');
                    if (classNames.contains(name)) {
                        // Skip classes already known
                        continue;
                    }
                    File f = new File (dir, file);
                    if (!f.isFile()) {
                        // Skip directories
                        continue;
                    }
                    Class<?> clazz = Class.forName(packageName + "." + name);
                    if (!Modifier.isPublic(clazz.getModifiers())) {
                        // Skip non-public classes
                        continue;
                    }

                    // There should be nothing left unless we missed something
                    Assert.fail(packageName + "." + name);
                }
            }
        }
    }


    private static Stream<String> getJavaBaseClasses() throws Exception {
        // While this code is only used on Java 9 and later, it needs to compile
        // with Java 8 so use reflection for now.
        Class<?> clazzModuleFinder = Class.forName("java.lang.module.ModuleFinder");
        Class<?> clazzModuleReference = Class.forName("java.lang.module.ModuleReference");
        Class<?> clazzModuleReader = Class.forName("java.lang.module.ModuleReader");

        // Returns ModuleFinder
        Object mf = clazzModuleFinder.getMethod("ofSystem").invoke(null);
        // Returns ModuleReference
        Object mRef = ((Optional<?>) clazzModuleFinder.getMethod(
                "find", String.class).invoke(mf, "java.base")).get();
        // Returns ModuleReader
        Object mr = clazzModuleReference.getMethod("open").invoke(mRef);
        // Returns the contents of the module
        @SuppressWarnings("unchecked")
        Stream<String> result = (Stream<String>) clazzModuleReader.getMethod("list").invoke(mr);
        return result;
    }
}
