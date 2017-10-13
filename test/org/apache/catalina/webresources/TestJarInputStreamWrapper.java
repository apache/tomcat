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
package org.apache.catalina.webresources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.WebResource;
import org.apache.tomcat.util.compat.JreCompat;

public class TestJarInputStreamWrapper {

    @Test
    public void testReadAfterClose() throws Exception {
        Method m = InputStream.class.getMethod("read", (Class<?>[]) null);
        testMethodAfterClose(m, (Object[]) null);
    }


    @Test
    public void testSkipAfterClose() throws Exception {
        Method m = InputStream.class.getMethod("skip", long.class);
        testMethodAfterClose(m, Long.valueOf(1));
    }


    @Test
    public void testAvailableAfterClose() throws Exception {
        Method m = InputStream.class.getMethod("available", (Class<?>[]) null);
        testMethodAfterClose(m, (Object[]) null);
    }


    @Test
    public void testCloseAfterClose() throws Exception {
        Method m = InputStream.class.getMethod("close", (Class<?>[]) null);
        testMethodAfterClose(m, (Object[]) null);
    }


    @Test
    public void testMarkAfterClose() throws Exception {
        Method m = InputStream.class.getMethod("mark", int.class);
        testMethodAfterClose(m, Integer.valueOf(1));
    }


    @Test
    public void testResetAfterClose() throws Exception {
        Method m = InputStream.class.getMethod("reset", (Class<?>[]) null);
        testMethodAfterClose(m, (Object[]) null);
    }


    @Test
    public void testMarkSupportedAfterClose() throws Exception {
        Method m = InputStream.class.getMethod("markSupported", (Class<?>[]) null);
        testMethodAfterClose(m, (Object[]) null);
    }


    private void testMethodAfterClose(Method m, Object... params) throws IOException {
        InputStream unwrapped = getUnwrappedClosedInputStream();
        InputStream wrapped = getWrappedClosedInputStream();

        Object unwrappedReturn = null;
        Exception unwrappedException = null;
        Object wrappedReturn = null;
        Exception wrappedException = null;

        try {
            unwrappedReturn = m.invoke(unwrapped, params);
        } catch (Exception e) {
            unwrappedException = e;
        }

        try {
            wrappedReturn = m.invoke(wrapped, params);
        } catch (Exception e) {
            wrappedException = e;
        }

        if (unwrappedReturn == null) {
            Assert.assertNull(wrappedReturn);
        } else {
            Assert.assertNotNull(wrappedReturn);
            Assert.assertEquals(unwrappedReturn, wrappedReturn);
        }

        if (unwrappedException == null) {
            Assert.assertNull(wrappedException);
        } else {
            Assert.assertNotNull(wrappedException);
            Assert.assertEquals(unwrappedException.getClass(), wrappedException.getClass());
        }
    }


    private InputStream getUnwrappedClosedInputStream() throws IOException {
        File file = new File("test/webresources/non-static-resources.jar");
        JarFile jarFile = JreCompat.getInstance().jarFileNewInstance(file);
        ZipEntry jarEntry = jarFile.getEntry("META-INF/MANIFEST.MF");
        InputStream unwrapped = jarFile.getInputStream(jarEntry);
        unwrapped.close();
        jarFile.close();
        return unwrapped;
    }


    private InputStream getWrappedClosedInputStream() throws IOException {
        StandardRoot root = new StandardRoot();
        root.setCachingAllowed(false);
        JarResourceSet jarResourceSet =
                new JarResourceSet(root, "/", "test/webresources/non-static-resources.jar", "/");
        WebResource webResource = jarResourceSet.getResource("/META-INF/MANIFEST.MF");
        InputStream wrapped = webResource.getInputStream();
        wrapped.close();
        return wrapped;
    }
}
