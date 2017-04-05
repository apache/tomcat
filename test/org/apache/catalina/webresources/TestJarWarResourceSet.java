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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestJarWarResourceSet extends TomcatBaseTest {

    @Before
    public void register() {
        TomcatURLStreamHandlerFactory.register();
    }


    @Test
    public void testJarWarMetaInf() throws LifecycleException  {
        Tomcat tomcat = getTomcatInstance();

        File warFile = new File("test/webresources/war-url-connection.war");
        Context ctx = tomcat.addContext("", warFile.getAbsolutePath());

        tomcat.start();

        StandardRoot root = (StandardRoot) ctx.getResources();

        WebResource[] results = root.getClassLoaderResources("/META-INF");

        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.length);
        Assert.assertNotNull(results[0].getURL());
    }
}
