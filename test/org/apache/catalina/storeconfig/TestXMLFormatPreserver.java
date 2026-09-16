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

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.parsers.SAXParserFactory;

import org.junit.Assert;
import org.junit.Test;

import org.xml.sax.InputSource;

/**
 * Unit tests for {@link XMLFormatPreserver}.
 */
public class TestXMLFormatPreserver {

    @Test
    public void testNullOriginal() {
        String newXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Server port=\"8005\"/>\n";
        Assert.assertEquals(newXml, XMLFormatPreserver.preserve((File) null, newXml, "UTF-8"));
        Assert.assertEquals(newXml, XMLFormatPreserver.preserve("", newXml, "UTF-8"));
        Assert.assertNull(XMLFormatPreserver.preserve("<Server/>", null, "UTF-8"));
    }

    @Test
    public void testMalformedDocuments() {
        String newXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Server port=\"8005\"/>\n";
        Assert.assertEquals(newXml, XMLFormatPreserver.preserve("<broken", newXml, "UTF-8"));
        Assert.assertEquals("<broken", XMLFormatPreserver.preserve("<Server/>", "<broken", "UTF-8"));
    }

    @Test
    public void testDifferentRootElements() {
        Assert.assertEquals("<Server/>", XMLFormatPreserver.preserve("<Host/>", "<Server/>", "UTF-8"));
    }

    @Test
    public void testCommentsAndBlankLinesPreserved() {
        String originalXml = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!--",
                "  License header line 1",
                "  License header line 2",
                "-->",
                "<Server port=\"8005\">",
                "    <!-- Section comment -->",
                "",
                "    <Service name=\"Catalina\">",
                "        <Connector port=\"8080\"/>",
                "        <!-- Commented out element",
                "        <Connector port=\"8009\"/>",
                "        -->",
                "    </Service>",
                "</Server>",
                "");
        String newXml = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Server port=\"8006\">",
                "  <Service name=\"Catalina\">",
                "    <Connector port=\"8080\"/>",
                "  </Service>",
                "</Server>",
                "");
        String expected = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!--",
                "  License header line 1",
                "  License header line 2",
                "-->",
                "<Server port=\"8006\">",
                "    <!-- Section comment -->",
                "",
                "  <Service name=\"Catalina\">",
                "    <Connector port=\"8080\"/>",
                "        <!-- Commented out element",
                "        <Connector port=\"8009\"/>",
                "        -->",
                "  </Service>",
                "</Server>",
                "");
        Assert.assertEquals(expected, XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8"));
    }

    @Test
    public void testAttributeOrderPreserved() {
        String originalXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Server>\n"
                + "  <Connector protocol=\"HTTP/1.1\" port=\"8080\"/>\n</Server>\n";
        String newXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Server>\n"
                + "  <Connector port=\"8080\" protocol=\"HTTP/1.1\" maxThreads=\"200\"/>\n</Server>\n";
        String result = XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8");
        Assert.assertTrue(result, result.contains("<Connector protocol=\"HTTP/1.1\" port=\"8080\" maxThreads=\"200\"/>"));
    }

    @Test
    public void testAttributeWrapping() {
        String originalXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Server>\n"
                + "  <Connector longAttributeOne=\"123456789012345678901234567890\"\n"
                + "    longAttributeTwo=\"ABCDEFGHIJ\"/>\n</Server>\n";
        String newXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Server>\n"
                + "  <Connector longAttributeOne=\"123456789012345678901234567890\"\n"
                + "    longAttributeTwo=\"K\"/>\n</Server>\n";
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Server>\n"
                + "  <Connector longAttributeOne=\"123456789012345678901234567890\"\n"
                + "    longAttributeTwo=\"K\"/>\n</Server>\n";
        Assert.assertEquals(expected, XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8"));
    }

    @Test
    public void testSiblingMatchingByKeyAttributes() {
        String originalXml = String.join("\n",
                "<Server>",
                "    <!-- SSL connector -->",
                "    <Connector port=\"8443\"/>",
                "    <!-- HTTP connector -->",
                "    <Connector port=\"8080\"/>",
                "</Server>",
                "");
        String newXml = String.join("\n",
                "<Server>",
                "  <Connector port=\"8081\"/>",
                "  <Connector port=\"8443\"/>",
                "</Server>",
                "");
        String expected = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Server>",
                "  <Connector port=\"8081\"/>",
                "    <!-- SSL connector -->",
                "  <Connector port=\"8443\"/>",
                "</Server>",
                "");
        Assert.assertEquals(expected, XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8"));
    }

    @Test
    public void testNewElementIsNotDecorated() {
        String originalXml = String.join("\n",
                "<Server>",
                "    <!-- HTTP -->",
                "    <Connector port=\"8080\"/>",
                "</Server>",
                "");
        String newXml = String.join("\n",
                "<Server>",
                "  <Connector port=\"8080\"/>",
                "  <Connector port=\"8443\"/>",
                "</Server>",
                "");
        String expected = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Server>",
                "    <!-- HTTP -->",
                "  <Connector port=\"8080\"/>",
                "  <Connector port=\"8443\"/>",
                "</Server>",
                "");
        Assert.assertEquals(expected, XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8"));
    }

    @Test
    public void testRemovedElementDropsItsComments() {
        String originalXml = String.join("\n",
                "<Server>",
                "    <!-- Listener A -->",
                "    <Listener className=\"org.a.A\"/>",
                "    <Listener className=\"org.b.B\"/>",
                "</Server>",
                "");
        String newXml = String.join("\n",
                "<Server>",
                "  <Listener className=\"org.b.B\"/>",
                "</Server>",
                "");
        String expected = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Server>",
                "  <Listener className=\"org.b.B\"/>",
                "</Server>",
                "");
        Assert.assertEquals(expected, XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8"));
    }

    @Test
    public void testTextElementsMatchedByText() {
        String originalXml = String.join("\n",
                "<Host name=\"localhost\">",
                "    <!-- watched conf -->",
                "    <WatchedResource>conf/context.xml</WatchedResource>",
                "    <WatchedResource>WEB-INF/web.xml</WatchedResource>",
                "</Host>",
                "");
        String newXml = String.join("\n",
                "<Host name=\"localhost\">",
                "  <WatchedResource>conf/context.xml</WatchedResource>",
                "</Host>",
                "");
        String expected = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Host name=\"localhost\">",
                "    <!-- watched conf -->",
                "  <WatchedResource>conf/context.xml</WatchedResource>",
                "</Host>",
                "");
        Assert.assertEquals(expected, XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8"));
    }

    @Test
    public void testEscaping() {
        String originalXml = "<Server><Connector port=\"8080\" note=\"a &amp; b &lt; c\"/></Server>";
        String newXml = "<Server><Connector port=\"8080\" note=\"a &amp; b &lt; c\"/></Server>";
        String result = XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8");
        Assert.assertTrue(result, result.contains("note=\"a &amp; b &lt; c\""));
    }

    @Test
    public void testCrlfLineEndingsPreserved() {
        String originalXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<!-- comment -->\r\n"
                + "<Server port=\"8005\"/>\r\n";
        String newXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Server port=\"8006\"/>\n";
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<!-- comment -->\r\n"
                + "<Server port=\"8006\"/>\r\n";
        Assert.assertEquals(expected, XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8"));
    }

    @Test
    public void testDoctypePreserved() {
        String originalXml = "<?xml version=\"1.0\"?>\n<!DOCTYPE Server SYSTEM \"server.dtd\">\n"
                + "<Server port=\"8005\"/>\n";
        String newXml = "<Server port=\"8006\"/>\n";
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE Server SYSTEM \"server.dtd\">\n" + "<Server port=\"8006\"/>\n";
        Assert.assertEquals(expected, XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8"));
    }

    @Test
    public void testResultIsWellFormed() throws Exception {
        String originalXml = String.join("\n",
                "<Server>",
                "    <!-- comment -->",
                "    <Service name=\"Catalina\"/>",
                "</Server>",
                "");
        String newXml = String.join("\n",
                "<Server>",
                "  <Service name=\"Catalina\"/>",
                "  <Listener className=\"org.x.Y\"/>",
                "</Server>",
                "");
        String result = XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8");
        SAXParserFactory.newInstance().newSAXParser().getXMLReader().parse(new InputSource(new StringReader(result)));
    }

    @Test
    public void testIdempotent() {
        String originalXml = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!--",
                "  License header line 1",
                "-->",
                "<Server port=\"8005\">",
                "    <!-- Section comment -->",
                "",
                "    <Service name=\"Catalina\">",
                "        <Connector port=\"8080\"/>",
                "    </Service>",
                "</Server>",
                "");
        String newXml = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Server port=\"8006\">",
                "  <Service name=\"Catalina\">",
                "    <Connector port=\"8080\"/>",
                "  </Service>",
                "</Server>",
                "");
        String formatted = XMLFormatPreserver.preserve(originalXml, newXml, "UTF-8");
        String formattedAgain = XMLFormatPreserver.preserve(formatted, newXml, "UTF-8");
        Assert.assertEquals(formatted, formattedAgain);
    }

    @Test
    public void testFileOverload() throws Exception {
        String originalXml = String.join("\n",
                "<Server>",
                "    <!-- comment -->",
                "    <Service name=\"Catalina\"/>",
                "</Server>",
                "");
        String newXml = String.join("\n",
                "<Server>",
                "  <Service name=\"Catalina\"/>",
                "</Server>",
                "");
        String expected = String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Server>",
                "    <!-- comment -->",
                "  <Service name=\"Catalina\"/>",
                "</Server>",
                "");
        File file = File.createTempFile("xml-format-preserver", ".xml");
        file.deleteOnExit();
        Files.write(file.toPath(), originalXml.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(expected, XMLFormatPreserver.preserve(file, newXml, "UTF-8"));
        Assert.assertEquals(newXml, XMLFormatPreserver.preserve(new File(file, "missing.xml"), newXml, "UTF-8"));
        Assert.assertEquals(newXml, XMLFormatPreserver.preserve(file.getParentFile(), newXml, "UTF-8"));
    }

    @Test
    public void testFileOverloadWithBom() throws Exception {
        String originalXml = "\uFEFF" + "<Server>\n    <!-- comment -->\n    <Service name=\"Catalina\"/>\n</Server>\n";
        String newXml = "<Server>\n  <Service name=\"Catalina\"/>\n</Server>\n";
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Server>\n    <!-- comment -->\n  <Service name=\"Catalina\"/>\n</Server>\n";
        File file = File.createTempFile("xml-format-preserver", ".xml");
        file.deleteOnExit();
        Files.write(file.toPath(), originalXml.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(expected, XMLFormatPreserver.preserve(file, newXml, "UTF-8"));
    }

}
