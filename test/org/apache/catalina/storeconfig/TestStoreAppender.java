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
package org.apache.catalina.storeconfig;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link StoreAppender}.
 */
public class TestStoreAppender {

    @Test
    public void testPrintCloseTag() throws Exception {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        StoreDescription desc = new StoreDescription();
        desc.setTag("Server");

        appender.printCloseTag(pw, desc);
        pw.flush();

        Assert.assertEquals("</Server>" + System.lineSeparator(), sw.toString());
    }


    @Test
    public void testPrintOpenTag() throws Exception {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        StoreDescription desc = new StoreDescription();
        desc.setTag("Server");
        desc.setAttributes(false);

        appender.printOpenTag(pw, 0, null, desc);
        pw.flush();

        Assert.assertEquals("<Server>" + System.lineSeparator(), sw.toString());
    }


    @Test
    public void testPrintTag() throws Exception {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        StoreDescription desc = new StoreDescription();
        desc.setTag("Listener");
        desc.setAttributes(false);

        appender.printTag(pw, 0, null, desc);
        pw.flush();

        Assert.assertEquals("<Listener/>" + System.lineSeparator(), sw.toString());
    }


    @Test
    public void testPrintTagContent() throws Exception {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        appender.printTagContent(pw, "WatchedResource", "WEB-INF/web.xml");
        pw.flush();

        Assert.assertEquals("<WatchedResource>WEB-INF/web.xml</WatchedResource>"
                + System.lineSeparator(), sw.toString());
    }


    @Test
    public void testPrintTagContentWithSpecialChars() throws Exception {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        appender.printTagContent(pw, "Value", "a<b&c");
        pw.flush();

        String result = sw.toString();
        Assert.assertTrue(result.contains("a&lt;b&amp;c"));
    }


    @Test
    public void testPrintIndent() {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        appender.printIndent(pw, 4);
        pw.flush();

        Assert.assertEquals("    ", sw.toString());
    }


    @Test
    public void testPrintTagArray() throws Exception {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        String[] elements = {"value1", "value2"};
        appender.printTagArray(pw, "Element", 0, elements);
        pw.flush();

        String result = sw.toString();
        Assert.assertTrue(result.contains("<Element>value1</Element>"));
        Assert.assertTrue(result.contains("<Element>value2</Element>"));
    }


    @Test
    public void testPrintTagArrayNull() throws Exception {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        appender.printTagArray(pw, "Element", 0, null);
        pw.flush();

        Assert.assertEquals("", sw.toString());
    }


    @Test
    public void testPrintTagValueArray() {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        String[] elements = {"TLSv1.2", "TLSv1.3"};
        appender.printTagValueArray(pw, "Protocols", 0, elements);
        pw.flush();

        String result = sw.toString();
        Assert.assertTrue(result.contains("<Protocols>"));
        Assert.assertTrue(result.contains("TLSv1.2"));
        Assert.assertTrue(result.contains("TLSv1.3"));
        Assert.assertTrue(result.contains("</Protocols>"));
    }


    @Test
    public void testPrintTagValueArrayNull() {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        appender.printTagValueArray(pw, "Protocols", 0, null);
        pw.flush();

        Assert.assertEquals("", sw.toString());
    }


    @Test
    public void testPrintTagValueArrayEmpty() {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        appender.printTagValueArray(pw, "Protocols", 0, new String[0]);
        pw.flush();

        Assert.assertEquals("", sw.toString());
    }


    @Test
    public void testPrintValue() {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        appender.printValue(pw, 0, "port", "8080");
        pw.flush();

        String result = sw.toString();
        Assert.assertTrue(result.contains("port=\"8080\""));
    }


    @Test
    public void testIsPrintValue() {
        StoreAppender appender = new StoreAppender();
        StoreDescription desc = new StoreDescription();

        // Default always returns true
        Assert.assertTrue(appender.isPrintValue("bean", "bean2", "attr", desc));
    }


    @Test
    public void testDefaultInstance() throws Exception {
        StoreAppender appender = new StoreAppender();

        StoreDescription original = new StoreDescription();
        original.setTag("Server");

        Object defaultObj = appender.defaultInstance(original);

        Assert.assertNotNull(defaultObj);
        Assert.assertTrue(defaultObj instanceof StoreDescription);
        Assert.assertNotSame(original, defaultObj);
    }


    @Test
    public void testPrintOpenTagWithAttributes() throws Exception {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        StoreDescription desc = new StoreDescription();
        desc.setTag("Connector");
        desc.setAttributes(true);
        desc.setStandard(true);

        // Use a simple bean with non-default values
        SimpleBean bean = new SimpleBean();
        bean.setPort(8443);

        appender.printOpenTag(pw, 0, bean, desc);
        pw.flush();

        String result = sw.toString();
        Assert.assertTrue("Should start with <Connector", result.startsWith("<Connector"));
        Assert.assertTrue("Should contain port attribute", result.contains("port=\"8443\""));
    }


    @Test
    public void testPrintTagWithAttributes() throws Exception {
        StoreAppender appender = new StoreAppender();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        StoreDescription desc = new StoreDescription();
        desc.setTag("Listener");
        desc.setAttributes(true);
        desc.setStandard(false);

        SimpleBean bean = new SimpleBean();
        bean.setPort(9090);

        appender.printTag(pw, 0, bean, desc);
        pw.flush();

        String result = sw.toString();
        Assert.assertTrue("Should contain className for non-standard",
                result.contains("className="));
        Assert.assertTrue("Should end with />", result.trim().endsWith("/>"));
    }


    /**
     * Simple JavaBean for testing attribute printing.
     */
    public static class SimpleBean {
        private int port = 8080;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
