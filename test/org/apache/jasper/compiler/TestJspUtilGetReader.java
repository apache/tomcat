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
package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.apache.jasper.JasperException;
import org.apache.tomcat.util.scan.ReferenceCountedJar;

public class TestJspUtilGetReader {

    private static final String TAG_NAME = "META-INF/tags/test.tag";

    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();

    @Test
    public void testUnsupportedEncodingClosesJar() throws Exception {
        ReferenceCountedJar jar = createJar();
        Field wrappedJar = ReferenceCountedJar.class.getDeclaredField("wrappedJar");
        wrappedJar.setAccessible(true);

        try {
            try (jar) {
                JasperException exception = Assert.assertThrows(JasperException.class,
                        () -> JspUtil.getReader('/' + TAG_NAME, "does-not-exist", jar, null, new ErrorDispatcher(false)));
                Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("does-not-exist"));
            }

            // The failed reader must release its stream reference so the owner's close can close the JAR.
            Assert.assertNull(wrappedJar.get(jar));
        } finally {
            // Release a leaked stream reference if the assertion fails.
            if (wrappedJar.get(jar) != null) {
                jar.close();
            }
        }
    }


    @Test
    public void testReaderRemainsOpenAfterJarClose() throws Exception {
        ReferenceCountedJar jar = createJar();
        InputStreamReader reader;
        try (jar) {
            reader = JspUtil.getReader('/' + TAG_NAME, "UTF-8", jar, null, new ErrorDispatcher(false));
        }
        try (reader) {
            Assert.assertEquals('h', reader.read());
        }
    }


    private ReferenceCountedJar createJar() throws Exception {
        File file = testDir.newFile("tags.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(file))) {
            jos.putNextEntry(new JarEntry(TAG_NAME));
            jos.write("hello".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return new ReferenceCountedJar(file.toURI().toURL());
    }
}
