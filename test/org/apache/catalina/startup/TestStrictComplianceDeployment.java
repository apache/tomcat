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
package org.apache.catalina.startup;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.catalina.Context;

/**
 * Verification for STRICT_SERVLET_COMPLIANCE and web.xml parsing.
 */
public class TestStrictComplianceDeployment extends TomcatBaseTest {
    private static final String STRICT_SERVLET_COMPLIANCE = "org.apache.catalina.STRICT_SERVLET_COMPLIANCE";
    private static String originalPropertyValue;
    @BeforeClass
    public static void enableStrictServletCompliance() {
        originalPropertyValue = System.getProperty(STRICT_SERVLET_COMPLIANCE);
        System.setProperty(STRICT_SERVLET_COMPLIANCE, "true");
    }

    @AfterClass
    public static void restoreStrictServletCompliance() {
        if (originalPropertyValue == null) {
            System.clearProperty(STRICT_SERVLET_COMPLIANCE);
        } else {
            System.setProperty(STRICT_SERVLET_COMPLIANCE, originalPropertyValue);
        }
    }

    @Test
    public void testWebAppDeployWithStrictComplianceWithSaxParseException() throws Exception {
        File appDir = getTemporaryDirectory();
        File webInf = new File(appDir, "WEB-INF");
        Assert.assertTrue(webInf.isDirectory() || webInf.mkdirs());
        writeInvalidXml(new File(webInf, "web.xml"));
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addWebapp(null,"", appDir.getAbsolutePath());

        try(WebappLogCapture capture = attachWebappLogCapture(
            ctx, Level.SEVERE,"org.apache.tomcat.util.digester.Digester")) {
            tomcat.start();
            Assert.assertTrue("A 'Parse error' was found in the logs.", capture.containsText("Parse error at line"));
            Assert.assertTrue("A SAXParseException was found in the logs.", capture.hasException(org.xml.sax.SAXParseException.class));

        }
    }

    @Test
    public void testWebAppDeployWithStrictComplianceNoSaxParseException() throws Exception {
        File appDir = getTemporaryDirectory();
        File webInf = new File(appDir, "WEB-INF");
        Assert.assertTrue(webInf.isDirectory() || webInf.mkdirs());
        writeValidXml(new File(webInf, "web.xml"));
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addWebapp(null,"", appDir.getAbsolutePath());

        try(WebappLogCapture capture = attachWebappLogCapture(
            ctx, Level.SEVERE,"org.apache.tomcat.util.digester.Digester")) {
            tomcat.start();
            Assert.assertFalse("A 'Parse error' was found in the logs.", capture.containsText("Parse error at line"));
            Assert.assertFalse("A SAXParseException was found in the logs.", capture.hasException(org.xml.sax.SAXParseException.class));

        }
    }
    private void writeValidXml(File webXml) throws IOException {
        try (FileWriter fw = new FileWriter(webXml)) {
            fw.write(
                """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                                                 https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
                             version="6.0">
                    </web-app>
                    """);
        }
    }
    private void writeInvalidXml(File webXml) throws IOException {
        try (FileWriter fw = new FileWriter(webXml)) {
            fw.write(
                """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <web-app>
                    </web-app>
                    """);
        }
    }

}
