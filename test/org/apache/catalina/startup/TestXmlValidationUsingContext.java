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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;

/**
 * Tests XML validation works on the Context.
 */
public class TestXmlValidationUsingContext extends TomcatBaseTest {
    @Test
    public void contextValidationWithInvalidWebXml() throws Exception {
        File appDir = getTemporaryDirectory();
        File webInf = new File(appDir, "WEB-INF");
        Assert.assertTrue(webInf.isDirectory() || webInf.mkdirs());
        writeInvalidXml(new File(webInf, "web.xml"));
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addWebapp(null, "", appDir.getAbsolutePath());
        ctx.setXmlValidation(true);
        ctx.setXmlNamespaceAware(true);
        tomcat.start();
        Assert.assertFalse("Context should not be available when web.xml is invalid and validation is enabled",
                ctx.getState().isAvailable());
    }

    @Test
    public void contextValidationWithValidWebXml() throws Exception {
        File appDir = getTemporaryDirectory();
        File webInf = new File(appDir, "WEB-INF");
        Assert.assertTrue(webInf.isDirectory() || webInf.mkdirs());
        writeValidXml(new File(webInf, "web.xml"));
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addWebapp(null, "", appDir.getAbsolutePath());
        ctx.setXmlValidation(true);
        ctx.setXmlNamespaceAware(true);
        tomcat.start();
        Assert.assertTrue("Context should be available when web.xml is valid and validation is enabled",
                ctx.getState().isAvailable());
    }

    private void writeValidXml(File webXml) throws IOException {
        try (FileWriter fw = new FileWriter(webXml)) {
            fw.write(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<web-app xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\"\n" +
                        "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "         xsi:schemaLocation=\"http://xmlns.jcp.org/xml/ns/javaee " +
                        "http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd\"\n" +
                        "         version=\"4.0\">\n" +
                        "</web-app>");
        }
    }
    private void writeInvalidXml(File webXml) throws IOException {
        try (FileWriter fw = new FileWriter(webXml)) {
            fw.write(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<web-app>\n" +
                    "</web-app>");
        }
    }
}
