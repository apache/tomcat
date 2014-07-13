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
package org.apache.catalina.mapper;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.websocket.server.WsContextListener;

/**
 * Mapper tests that use real web applications on a running Tomcat.
 */
public class TestMapperWebapps extends TomcatBaseTest{

    @Test
    public void testContextReload_56658() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        // app dir is relative to server home
        org.apache.catalina.Context ctxt  = tomcat.addWebapp(
                null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());
        tomcat.start();

        // The tests are from TestTomcat#testSingleWebapp(), #testJsps()
        // We reload the context and verify that the pages are still accessible
        ByteChunk res;
        String text;

        res = getUrl("http://localhost:" + getPort()
                + "/examples/servlets/servlet/HelloWorldExample");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<h1>Hello World!</h1>"));

        res = getUrl("http://localhost:" + getPort()
                + "/examples/jsp/jsp2/el/basic-arithmetic.jsp");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<td>${(1==2) ? 3 : 4}</td>"));

        res = getUrl("http://localhost:" + getPort() + "/examples/index.html");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<title>Apache Tomcat Examples</title>"));

        ctxt.reload();

        res = getUrl("http://localhost:" + getPort()
                + "/examples/servlets/servlet/HelloWorldExample");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<h1>Hello World!</h1>"));

        res = getUrl("http://localhost:" + getPort()
                + "/examples/jsp/jsp2/el/basic-arithmetic.jsp");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<td>${(1==2) ? 3 : 4}</td>"));

        res = getUrl("http://localhost:" + getPort() + "/examples/index.html");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<title>Apache Tomcat Examples</title>"));
    }
}
