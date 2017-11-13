/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.loader;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.webresources.StandardRoot;

public class TestVirtualWebappLoader extends TomcatBaseTest {

    @Test
    public void testModified() throws Exception {
        WebappLoader loader = new WebappLoader();
        Assert.assertNull(loader.getClassLoader());
        Assert.assertFalse(loader.modified());
    }

    @Test
    public void testStartInternal() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        StandardContext ctx = (StandardContext) tomcat.addContext("",
                appDir.getAbsolutePath());


        WebappLoader loader = new WebappLoader();

        loader.setContext(ctx);
        ctx.setLoader(loader);

        ctx.setResources(new StandardRoot(ctx));
        ctx.resourcesStart();

        File f1 = new File("test/webapp-fragments/WEB-INF/lib");
        ctx.getResources().createWebResourceSet(
                WebResourceRoot.ResourceSetType.POST, "/WEB-INF/lib",
                f1.getAbsolutePath(), null, "/");

        loader.start();
        String[] repos = loader.getLoaderRepositories();
        Assert.assertEquals(4,repos.length);
        loader.stop();

        repos = loader.getLoaderRepositories();
        Assert.assertEquals(0, repos.length);

        // no leak
        loader.start();
        repos = loader.getLoaderRepositories();
        Assert.assertEquals(4,repos.length);

        // clear loader
        ctx.setLoader(null);
        // see tearDown()!
        tomcat.start();
    }
}
