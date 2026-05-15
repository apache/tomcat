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
package org.apache.tomcat.util.scan;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.Jar;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.unittest.TesterServletContext;

public class TestStandardJarScanner {

    /**
     * Tomcat should ignore URLs which do not have a file part and do not use the file scheme.
     */
    @Test
    public void skipsInvalidClasspathURLNoFilePartNoFileScheme() {
        StandardJarScanner scanner = new StandardJarScanner();
        LoggingCallback callback = new LoggingCallback();
        TesterServletContext context = new TesterServletContext() {
            @Override
            public ClassLoader getClassLoader() {
                URLClassLoader urlClassLoader;
                try {
                    urlClassLoader = new URLClassLoader(
                            new URL[] { URI.create("http://felix.extensions:9/").toURL() });
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
                return urlClassLoader;
            }
        };
        scanner.scan(JarScanType.PLUGGABILITY, context, callback);
    }

    @Test
    public void testScanManifestDefault() throws Exception {
        Assert.assertTrue("Referenced JAR from manifest Class-Path should be scanned",
                doTestScanManifest(true));
    }

    @Test
    public void testScanManifestDisabled() throws Exception {
        Assert.assertFalse("Referenced JAR from manifest Class-Path should not be scanned",
                doTestScanManifest(false));
    }

    private boolean doTestScanManifest(boolean scanManifest) throws Exception {
        File referencedJar = new File(System.getProperty("java.io.tmpdir"), "referenced.jar");
        referencedJar.deleteOnExit();
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(referencedJar), new Manifest());
        jarOutputStream.close();

        File testJar = new File(System.getProperty("java.io.tmpdir"), "manifest-test.jar");
        testJar.deleteOnExit();

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, "referenced.jar");

        jarOutputStream = new JarOutputStream(new FileOutputStream(testJar), manifest);
        jarOutputStream.close();

        StandardJarScanner scanner = new StandardJarScanner() {
            @Override
            protected void addClassPath(Deque<URL> classPathUrlsToProcess) {
                super.addClassPath(classPathUrlsToProcess);
                try {
                    classPathUrlsToProcess.add(testJar.toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        scanner.setScanManifest(scanManifest);

        LoggingCallback callback = new LoggingCallback();
        scanner.scan(JarScanType.PLUGGABILITY, new TesterServletContext(), callback);

        for (String cb : callback.callbacks) {
            if (cb.contains("referenced.jar")) {
                return true;
            }
        }
        return false;
    }

    private static class LoggingCallback implements JarScannerCallback {

        List<String> callbacks = new ArrayList<>();

        @Override
        public void scan(Jar jar, String webappPath,
                boolean isWebapp) throws IOException {
            callbacks.add(jar.getJarFileURL().toString() + "::" + webappPath + "::" + isWebapp);
        }

        @Override
        public void scan(File file, String webappPath, boolean isWebapp)
                throws IOException {
            callbacks.add(file.toString() + "::" + webappPath + "::" + isWebapp);
        }

        @Override
        public void scanWebInfClasses() throws IOException {
            callbacks.add("N/A::WEB-INF/classes::N/A");
        }
    }
}
