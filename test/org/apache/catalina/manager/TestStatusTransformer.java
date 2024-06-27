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
package org.apache.catalina.manager;

import java.io.File;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.json.JSONParser;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.xml.sax.InputSource;

public class TestStatusTransformer extends TomcatBaseTest {

    enum Mode {
        HTML, XML, JSON
    }

    @Test
    public void testJSON() throws Exception {
        testStatusServlet(Mode.JSON);
    }

    @Test
    public void testXML() throws Exception {
        testStatusServlet(Mode.XML);
    }

    @Test
    public void testHTML() throws Exception {
        testStatusServlet(Mode.HTML);
    }

    protected void testStatusServlet(Mode mode) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Add default servlet to make some requests
        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());
        ctxt.setPrivileged(true);
        Wrapper defaultServlet = Tomcat.addServlet(ctxt, "default",
                "org.apache.catalina.servlets.DefaultServlet");
        defaultServlet.addInitParameter("fileEncoding", "ISO-8859-1");
        ctxt.addServletMappingDecoded("/", "default");
        Tomcat.addServlet(ctxt, "status", "org.apache.catalina.manager.StatusManagerServlet");
        ctxt.addServletMappingDecoded("/status/*", "status");
        ctxt.addMimeMapping("html", "text/html");
        Context ctxt2 = tomcat.addContext("/test", null);
        Tomcat.addServlet(ctxt2, "status", "org.apache.catalina.manager.StatusManagerServlet");
        ctxt.addServletMappingDecoded("/somepath/*", "status");
        tomcat.start();

        SimpleHttpClient client = new SimpleHttpClient() {
            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(getPort());
        client.setRequest(new String[] {
                "GET /index.html HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);

        String requestline = null;
        switch (mode) {
            case XML -> requestline = "GET /status/all?XML=true HTTP/1.1";
            case JSON -> requestline = "GET /status/all?JSON=true HTTP/1.1";
            default -> requestline = "GET /status/all HTTP/1.1";
        }
        client.setRequest(new String[] {
                requestline + CRLF +
                "Host: localhost" + CRLF +
                "Connection: Close" + CRLF + CRLF });
        client.connect();
        client.processRequest(true);
        String body = client.getResponseBody();
        if (mode.equals(Mode.JSON)) {
            JSONParser parser = new JSONParser(body);
            String result = parser.parse().toString();
            Assert.assertTrue(result.contains("name=localhost/"));
        } else if (mode.equals(Mode.XML)) {
            try (StringReader reader = new StringReader(body)) {
                Document xmlDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(reader));
                String serialized = ((DOMImplementationLS) xmlDocument.getImplementation()).createLSSerializer().writeToString(xmlDocument);
                // Verify that a request is being processed
                Assert.assertTrue(serialized.contains("stage=\"S\""));
            }
        } else {
            // Verify that a request is being processed
            Assert.assertTrue(body.contains("<strong>S</strong>"));
        }
    }

}
