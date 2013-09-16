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
package org.apache.jasper.tagplugins.jstl.core;

import java.io.File;

import org.junit.Assert;
import org.junit.Before;

import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.webresources.StandardRoot;

public abstract class AbstractTestTag extends TomcatBaseTest {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctx = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        ctx.setResources(new StandardRoot(ctx));

        // Add the JSTL (we need the TLD)
        File lib = new File("webapps/examples/WEB-INF/lib");
        ctx.getResources().createWebResourceSet(
                WebResourceRoot.ResourceSetType.POST, "/WEB-INF/lib",
                lib.getAbsolutePath(), null, "/");

        // Configure the use of the plug-in rather than the standard impl
        File plugin = new File(
                "java/org/apache/jasper/tagplugins/jstl/tagPlugins.xml");
        Assert.assertTrue(plugin.isFile());
        ctx.getResources().createWebResourceSet(
                WebResourceRoot.ResourceSetType.POST, "/WEB-INF/tagPlugins.xml",
                plugin.getAbsolutePath(), null, "/");

        tomcat.start();
    }
}
