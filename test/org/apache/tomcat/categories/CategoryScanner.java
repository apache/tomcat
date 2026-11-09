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
package org.apache.tomcat.categories;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.experimental.categories.Category;

/**
 * Scans compiled test classes for a given JUnit {@link Category} annotation
 * and prints the matching source file paths as a comma-separated list suitable
 * for use as an Ant {@code test.name} include pattern.
 *
 * <p>Usage: {@code CategoryScanner <category-class-name> <test-classes-dir>}
 *
 * <p>Output is written to stdout as a single comma-separated line, e.g.:
 * {@code org/apache/catalina/core/TestFoo.java,org/apache/coyote/TestBar.java}
 */
public class CategoryScanner {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "Usage: CategoryScanner <category-class-name> <test-classes-dir> <test-src-dir>");
        }

        String categoryClassName = args[0];
        File testClassesDir = new File(args[1]);
        File testSrcDir = new File(args[2]);

        if (!testClassesDir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + testClassesDir);
        }
        if (!testSrcDir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + testSrcDir);
        }

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { testClassesDir.toURI().toURL() },
                CategoryScanner.class.getClassLoader())) {

            Class<?> categoryClass = loader.loadClass(categoryClassName);
            List<String> matches = new ArrayList<>();
            scanDir(testClassesDir, testClassesDir, testSrcDir, categoryClass, loader, matches);
            if (matches.isEmpty()) {
                throw new IllegalStateException(
                        "No tests found for category: " + categoryClassName);
            }
            Collections.sort(matches);
            System.out.print(String.join(",", matches));
        }
    }

    private static void scanDir(File root, File dir, File testSrcDir, Class<?> categoryClass,
            ClassLoader loader, List<String> results) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                scanDir(root, f, testSrcDir, categoryClass, loader, results);
            } else if (f.getName().startsWith("Test") && !f.getName().startsWith("Tester") &&
                       f.getName().endsWith(".class") && !f.getName().contains("$")) {
                String relative = root.toURI().relativize(f.toURI()).getPath();
                String javaPath = relative.replace(".class", ".java");
                // Skip classes whose source no longer exists (stale .class from prior build).
                if (!new File(testSrcDir, javaPath).exists()) {
                    continue;
                }
                String className = relative.replace('/', '.').replace(".class", "");
                try {
                    Class<?> cls = loader.loadClass(className);
                    if (java.lang.reflect.Modifier.isAbstract(cls.getModifiers())) {
                        continue;
                    }
                    Category cat = cls.getAnnotation(Category.class);
                    if (cat != null) {
                        for (Class<?> c : cat.value()) {
                            if (c.equals(categoryClass)) {
                                results.add(javaPath);
                                break;
                            }
                        }
                    }
                } catch (Exception | Error e) {
                    throw new RuntimeException("Failed to load class: " + className, e);
                }
            }
        }
    }
}
