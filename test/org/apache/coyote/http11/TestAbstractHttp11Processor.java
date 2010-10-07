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

package org.apache.coyote.http11;

import java.io.File;

import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestAbstractHttp11Processor extends TomcatBaseTest {

    public void testWithTEVoid() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        
        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: void" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }

    public void testWithTEBuffered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        
        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: buffered" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    public void testWithTEIdentity() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        
        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: identity" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        assertTrue(client.getResponseBody().contains("test - data"));
    }


    public void testWithTESavedRequest() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        
        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: savedrequest" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    public void testWithTEUnsupported() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        
        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: unsupported" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    private static final class Client extends SimpleHttpClient {
        @Override
        public boolean isResponseBodyOK() {
            return getResponseBody().contains("test - data");
        }
    }
}
