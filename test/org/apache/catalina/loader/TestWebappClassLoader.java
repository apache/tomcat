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
package org.apache.catalina.loader;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestWebappClassLoader extends TomcatBaseTest {

    @Test
    public void testGetURLs() throws Exception {
        File f = new File("test/webresources/war-url-connection.war");

        String[] expected = new String[2];
        String warUrl = f.toURI().toURL().toExternalForm();
        expected[0] = "jar:" + warUrl + "!/WEB-INF/classes/";
        expected[1] = "jar:" + warUrl + "!/WEB-INF/lib/test.jar";

        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        StandardContext ctx =
                (StandardContext)tomcat.addContext("",  f.getAbsolutePath());

        tomcat.start();

        ClassLoader cl = ctx.getLoader().getClassLoader();

        Assert.assertTrue(cl instanceof URLClassLoader);

        try (URLClassLoader ucl = (URLClassLoader) cl) {
            URL[] urls = ucl.getURLs();
            Assert.assertEquals(expected.length, urls.length);
            String[] actual = new String[urls.length];
            for (int i = 0; i < urls.length; i++) {
                actual[i] = urls[i].toExternalForm();
            }
            Assert.assertArrayEquals(expected, actual);
        }
    }
}
