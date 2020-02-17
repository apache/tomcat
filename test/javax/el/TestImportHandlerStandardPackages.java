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
import java.util.Set;

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
            // - OpenJDK 14 EA 27
            if (!JreCompat.isJre9Available()) {
                return;
            }
            for (String fileName : getJavaBaseClasses()) {
                if (!fileName.startsWith("java/lang/") ||          // Class not in java.lang
                        fileName.lastIndexOf('/') != 9 ||          // Class no in sub-package
                        !fileName.endsWith(".class")) {             // Exclude non-class resources
                    continue;
                }
                // Extract class name
                String className = fileName.substring(10, fileName.length() - 6);
                Class<?> clazz = Class.forName("java.lang." + className);
                if (!Modifier.isPublic(clazz.getModifiers())) {
                    // Exclude non-public classes
                    continue;
                }
                className = className.replace('$', '.');
                if (classNames.contains(className)) {
                    // Already listed
                    continue;
                }
                // Skip public inner classes of non-public classes
                if (className.startsWith("FdLibm.") ||
                        className.startsWith("LiveStackFrame.") ||
                        className.startsWith("WeakPairMap.")) {
                    continue;
                }

                // Anything left at this point is an error
                Assert.fail(fileName);
            }
        } else {
            // When this test runs, the class loader will be loading resources
            // from a directory for each of these packages.
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = cl.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
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
                        // Skip non-class resources
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
                    Class<?> clazz = Class.forName(packageName + "." + name.replaceAll("\\.", "\\$"));
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


    private static String[] getJavaBaseClasses() throws Exception {
        // While this code is only used on Java 9 and later, it needs to compile
        // with Java 8 so use reflection for now.
        Class<?> clazzModuleFinder = Class.forName("java.lang.module.ModuleFinder");
        Class<?> clazzModuleReference = Class.forName("java.lang.module.ModuleReference");
        Class<?> clazzOptional = Class.forName("java.util.Optional");
        Class<?> clazzModuleReader = Class.forName("java.lang.module.ModuleReader");
        Class<?> clazzStream = Class.forName("java.util.stream.Stream");

        // Returns ModuleFinder
        Object mf = clazzModuleFinder.getMethod("ofSystem").invoke(null);
        // Returns Optional containing a ModuleReference
        Object optMRef = clazzModuleFinder.getMethod("find", String.class).invoke(mf, "java.base");
        // Extract the ModuleReference
        Object mRef =  clazzOptional.getMethod("get").invoke(optMRef);
        // Returns ModuleReader
        Object mr = clazzModuleReference.getMethod("open").invoke(mRef);
        // Returns a Stream of class names
        Object stream = clazzModuleReader.getMethod("list").invoke(mr);
        // Convert to an array
        Object[] names = (Object[]) clazzStream.getMethod("toArray").invoke(stream);
        // Cast
        String [] result = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            result[i] = (String) names[i];
        }
        return result;
    }
}
