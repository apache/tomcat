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
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.JarScanType;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.unittest.TesterServletContext;

public class TestStandardJarScanner {

    @Test
    public void testWebappClassPath() {
        StandardJarScanner scanner = new StandardJarScanner();

        scanner.setScanClassPath(true);
        // When running the test on Java 9, one or more URLs to jimage files may
        // be returned. By setting the scanAllFiles option, a callback will be
        // generated for these files which in turn will mean the number of URLs
        // and the number of call backs will agree and this test will pass.
        // There is a TODO in StandardJarScanner to add 'proper' Java 9 support.
        scanner.setScanAllFiles(true);

        LoggingCallback callback = new LoggingCallback();

        scanner.scan(JarScanType.PLUGGABILITY, new TesterServletContext(), callback);

        List<String> callbacks = callback.getCallbacks();

        ClassLoader cl = TesterServletContext.class.getClassLoader();
        if (cl instanceof URLClassLoader) {
            URL[] urls =  ((URLClassLoader) cl).getURLs();

            int size;
            if (urls == null) {
                size = 0;
            } else {
                size = urls.length;
            }
            Assert.assertEquals(size, callbacks.size());

        } else {
            Assert.fail("Unexpected class loader type: " + cl.getClass().getName());
        }
    }

    private static class LoggingCallback implements JarScannerCallback {

        List<String> callbacks = new ArrayList<>();

        @Override
        public void scan(JarURLConnection urlConn, String webappPath,
                boolean isWebapp) throws IOException {
            callbacks.add(urlConn.toString() + "::" + webappPath + "::" + isWebapp);
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

        public List<String> getCallbacks() {
            return callbacks;
        }
    }
}
