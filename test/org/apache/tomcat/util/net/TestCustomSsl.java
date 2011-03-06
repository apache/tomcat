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
package org.apache.tomcat.util.net;

import java.io.File;

import javax.net.ssl.SSLContext;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.jsse.TesterBug50640SslImpl;

/**
 * The keys and certificates used in this file are all available in svn and were
 * generated using a test CA the files for which are in the Tomcat PMC private
 * repository since not all of them are AL2 licensed.
 */
public class TestCustomSsl extends TomcatBaseTest {

    public void testCustomSslImplementation() throws Exception {

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, TesterSupport.getTrustManagers(),
                    new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(
                    sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        } 
        
        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();
        if (connector.getProtocolHandlerClassName().contains("Apr")) {
            // This test is only for JSSE based SSL connectors
            return;
        }

        connector.setProperty("sslImplemenationName", 
                "org.apache.tomcat.util.net.jsse.TesterBug50640SslImpl");
        connector.setProperty(TesterBug50640SslImpl.PROPERTY_NAME,
                TesterBug50640SslImpl.PROPERTY_VALUE);
        
        connector.setProperty("sslProtocol", "tls");
        
        File keystoreFile =
            new File("test/org/apache/tomcat/util/net/localhost.jks");
        connector.setAttribute(
                "keystoreFile", keystoreFile.getAbsolutePath());

        connector.setSecure(true);            
        connector.setProperty("SSLEnabled", "true");

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }

}
