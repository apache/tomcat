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
import java.io.IOException;
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
        expected[0] = "war:" + warUrl + "*/WEB-INF/classes/";
        expected[1] = "war:" + warUrl + "*/WEB-INF/lib/test.jar";

        Tomcat tomcat = getTomcatInstance();

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

    @Test
    public void testFilter() throws IOException {

        String[] classSuffixes = new String[]{
            "",
            "some.package.Example"
        };

        String[] resourceSuffixes = new String[]{
            "",
            "some/path/test.properties",
            "some/path/test"
        };

        String[] prefixes = new String[]{
            "",
            "resources",
            "WEB-INF",
            "WEB-INF.classes",
            "WEB-INF.lib",
            "org",
            "org.apache",
            "javax",
            "com.mycorp"
        };

        String[] prefixesPermit = new String[]{
            "org.apache.tomcat.jdbc",
            "javax.servlet.jsp.jstl",
        };

        String[] prefixesDeny = new String[]{
            "org.apache.catalina",
            "org.apache.coyote",
            "org.apache.el",
            "org.apache.jasper",
            "org.apache.juli",
            "org.apache.naming",
            "org.apache.tomcat",
            "javax.el",
            "javax.servlet",
            "javax.websocket",
            "javax.security.auth.message"
        };

        try (WebappClassLoader loader = new WebappClassLoader()) {
            String name;

            for (String prefix : prefixes) {
                for (String suffix : classSuffixes) {
                    name = prefix + "." + suffix;
                    Assert.assertTrue("Class '" + name + "' failed permit filter",
                               !loader.filter(name, true));
                    if (prefix.equals("")) {
                        name = suffix;
                        Assert.assertTrue("Class '" + name + "' failed permit filter",
                                   !loader.filter(name, true));
                    }
                    if (suffix.equals("")) {
                        name = prefix;
                        Assert.assertTrue("Class '" + name + "' failed permit filter",
                                   !loader.filter(name, true));
                    }
                }
                prefix = prefix.replace('.', '/');
                for (String suffix : resourceSuffixes) {
                    name = prefix + "/" + suffix;
                    Assert.assertTrue("Resource '" + name + "' failed permit filter",
                               !loader.filter(name, false));
                    if (prefix.equals("")) {
                        name = suffix;
                        Assert.assertTrue("Resource '" + name + "' failed permit filter",
                                   !loader.filter(name, false));
                    }
                    if (suffix.equals("")) {
                        name = prefix;
                        Assert.assertTrue("Resource '" + name + "' failed permit filter",
                                   !loader.filter(name, false));
                    }
                }
            }

            for (String prefix : prefixesPermit) {
                for (String suffix : classSuffixes) {
                    name = prefix + "." + suffix;
                    Assert.assertTrue("Class '" + name + "' failed permit filter",
                               !loader.filter(name, true));
                }
                prefix = prefix.replace('.', '/');
                for (String suffix : resourceSuffixes) {
                    name = prefix + "/" + suffix;
                    Assert.assertTrue("Resource '" + name + "' failed permit filter",
                               !loader.filter(name, false));
                }
            }

            for (String prefix : prefixesDeny) {
                for (String suffix : classSuffixes) {
                    name = prefix + "." + suffix;
                    Assert.assertTrue("Class '" + name + "' failed deny filter",
                               loader.filter(name, true));
                }
                prefix = prefix.replace('.', '/');
                for (String suffix : resourceSuffixes) {
                    name = prefix + "/" + suffix;
                    Assert.assertTrue("Resource '" + name + "' failed deny filter",
                               loader.filter(name, false));
                }
            }
        }
    }
}
