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
package org.apache.tomcat.util.http.mapper;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestMapperWelcomeFiles extends TomcatBaseTest {

    @Test
    public void testWelcomeFileNotStrict() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");

        StandardContext ctxt = (StandardContext) tomcat.addWebapp(null, "/test",
                appDir.getAbsolutePath());
        ctxt.setReplaceWelcomeFiles(true);
        ctxt.addWelcomeFile("index.jsp");
        // Mapping for *.do is defined in web.xml
        ctxt.addWelcomeFile("index.do");

        tomcat.start();
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/test/welcome-files", bc, new HashMap<String,List<String>>());
        assertEquals(HttpServletResponse.SC_OK, rc);
        assertTrue(bc.toString().contains("JSP"));

        rc = getUrl("http://localhost:" + getPort() +
                "/test/welcome-files/sub", bc,
                new HashMap<String,List<String>>());
        assertEquals(HttpServletResponse.SC_OK, rc);
        assertTrue(bc.toString().contains("Servlet"));
    }

    @Test
    public void testWelcomeFileStrict() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");

        StandardContext ctxt = (StandardContext) tomcat.addWebapp(null, "/test",
                appDir.getAbsolutePath());
        ctxt.setReplaceWelcomeFiles(true);
        ctxt.addWelcomeFile("index.jsp");
        // Mapping for *.do is defined in web.xml
        ctxt.addWelcomeFile("index.do");

        // Simulate STRICT_SERVLET_COMPLIANCE
        ctxt.setResourceOnlyServlets("");

        tomcat.start();
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/test/welcome-files", bc, new HashMap<String,List<String>>());
        assertEquals(HttpServletResponse.SC_OK, rc);
        assertTrue(bc.toString().contains("JSP"));

        rc = getUrl("http://localhost:" + getPort() +
                "/test/welcome-files/sub", bc,
                new HashMap<String,List<String>>());
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
    }
}
