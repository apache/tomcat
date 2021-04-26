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
import java.util.jar.JarFile;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.catalina.WebResourceSet;

/**
 * @author Kamnani, Jatin
 */
public class TestJarContents {

    private static File empty;
    private static File jar;
    private static TesterWebResourceRoot root;
    private static WebResourceSet webResourceSet;
    private static JarResourceSet test;
    private static JarContents testJarContentsObject;

    @BeforeClass
    public static void setup() {
        try {
        empty = new File("test/webresources/dir3");
        jar = new File("test/webresources/dir1.jar");

        root = new TesterWebResourceRoot();

        // Use empty dir for root of web app.
        webResourceSet = new DirResourceSet(root, "/", empty.getAbsolutePath(), "/");
        root.setMainResources(webResourceSet);

        // If this JAR was in a web application, this is equivalent to how it
        // would be added
        test = new JarResourceSet(root, "/", jar.getAbsolutePath(), "/META-INF/resources");
        test.setStaticOnly(true);
        root.addJarResources(test);

        testJarContentsObject = new JarContents(new JarFile("test/webresources/dir1.jar"));

        } catch (Exception e) {
            Assert.fail("Error happened while testing JarContents, " + e.getMessage());
        }
    }

    @Test
    public void testMightContainResource() {
        Assert.assertTrue(testJarContentsObject.mightContainResource(
                "/d1/d1-f1.txt", jar.getAbsolutePath()));

        Assert.assertTrue(testJarContentsObject.mightContainResource(
                "d1/d1-f1.txt", jar.getAbsolutePath()));

        Assert.assertFalse(testJarContentsObject.mightContainResource(
                "/d7/d1-f1.txt", jar.getAbsolutePath()));

        Assert.assertFalse(testJarContentsObject.mightContainResource(
                "/", jar.getAbsolutePath()));

        Assert.assertFalse(testJarContentsObject.mightContainResource(
                "/////", jar.getAbsolutePath()));

    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testStringOutOfBoundExceptions() {
        testJarContentsObject.mightContainResource("", jar.getAbsolutePath());
    }

    @Test(expected = NullPointerException.class)
    public void testNullPointerExceptions() {
        testJarContentsObject.mightContainResource(null, jar.getAbsolutePath());
    }
}
