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
package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.http.parser.Forwarded.Element;
import org.apache.tomcat.util.http.parser.Forwarded.Node;

public class TestForwarded {

    private static List<Element> parseLine(String line) throws IOException {
        List<Element> result = new ArrayList<>();
        if (Forwarded.parse(new StringReader(line), result)) {
            return result;
        }
        return null;
    }


    @Test
    public void testSingleElement() throws Exception {
        List<Element> elements = parseLine("for=192.0.2.60;proto=http;by=203.0.113.43");
        Assert.assertEquals(1, elements.size());
        Element e = elements.get(0);
        Assert.assertEquals("192.0.2.60", e.getFor());
        Assert.assertEquals("http", e.getProto());
        Assert.assertEquals("203.0.113.43", e.getBy());
        Assert.assertNull(e.getHost());
        Assert.assertTrue(e.isValid());
    }


    @Test
    public void testTwoElements() throws Exception {
        List<Element> elements = parseLine("for=192.0.2.43, for=198.51.100.17");
        Assert.assertEquals(2, elements.size());
        Assert.assertEquals("192.0.2.43", elements.get(0).getFor());
        Assert.assertEquals("198.51.100.17", elements.get(1).getFor());
    }


    @Test
    public void testIPv6WithPort() throws Exception {
        List<Element> elements = parseLine("For=\"[2001:db8:cafe::17]:4711\"");
        Node node = elements.get(0).getForNode();
        Assert.assertEquals(Node.Type.IPV6, node.getType());
        Assert.assertEquals("2001:db8:cafe::17", node.getName());
        Assert.assertEquals("4711", node.getPort());
        Assert.assertTrue(node.isAddress());
    }


    @Test
    public void testIPv4WithPort() throws Exception {
        List<Element> elements = parseLine("for=\"192.0.2.43:47011\"");
        Node node = elements.get(0).getForNode();
        Assert.assertEquals(Node.Type.IPV4, node.getType());
        Assert.assertEquals("192.0.2.43", node.getName());
        Assert.assertEquals("47011", node.getPort());
    }


    @Test
    public void testUnquotedNodeWithPortRejected() throws Exception {
        Assert.assertNull(parseLine("for=192.0.2.43:47011"));
    }


    @Test
    public void testUnknown() throws Exception {
        for (String s : new String[] { "unknown", "UNKNOWN" }) {
            Node node = parseLine("for=" + s).get(0).getForNode();
            Assert.assertEquals(Node.Type.UNKNOWN, node.getType());
            Assert.assertFalse(node.isAddress());
        }
    }


    @Test
    public void testObfuscated() throws Exception {
        for (String s : new String[] { "_hidden", "_SEVKISEK" }) {
            Node node = parseLine("for=" + s).get(0).getForNode();
            Assert.assertEquals(Node.Type.OBFUSCATED, node.getType());
            Assert.assertEquals(s, node.getName());
            Assert.assertFalse(node.isAddress());
        }
    }


    @Test
    public void testObfuscatedPort() throws Exception {
        Node node = parseLine("for=\"_x:_p\"").get(0).getForNode();
        Assert.assertEquals(Node.Type.OBFUSCATED, node.getType());
        Assert.assertEquals("_p", node.getPort());
    }


    @Test
    public void testInvalidNodes() {
        String[] invalid = new String[] { "example.com", "1.2.3", "01.2.3.4", "256.1.1.1", "1.2.3.4.5",
                "[2001:db8::1%eth0]", "[::1]:100000", "[::1", "2001:db8::1", "1.2.3.4:", "1.2.3.4:abc", "1.2.3.4:123456",
                "_", "[::1]x", "" };
        for (String s : invalid) {
            Assert.assertNull(s, Node.parse(s));
        }
        Assert.assertNull(Node.parse(null));
    }


    @Test
    public void testCaseInsensitiveNames() throws Exception {
        Element e = parseLine("FOR=1.2.3.4;PROTO=https").get(0);
        Assert.assertEquals("1.2.3.4", e.getFor());
        Assert.assertEquals("https", e.getProto());
    }


    @Test
    public void testDuplicateParameter() throws Exception {
        List<Element> elements = parseLine("for=1.2.3.4;for=5.6.7.8");
        Assert.assertEquals(1, elements.size());
        Assert.assertFalse(elements.get(0).isValid());
    }


    @Test
    public void testEmptyListElementsAndPairs() throws Exception {
        for (String s : new String[] { "for=1.2.3.4,", ", ,for=1.2.3.4", "for=1.2.3.4;;proto=http;" }) {
            Assert.assertEquals(s, 1, parseLine(s).size());
        }
    }


    @Test
    public void testMultipleFields() throws Exception {
        List<Element> elements = Forwarded.parse(
                Collections.enumeration(Arrays.asList("for=192.0.2.43", "for=\"[2001:db8:cafe::17]\", for=unknown")));
        Assert.assertEquals(3, elements.size());
        Assert.assertEquals(Node.Type.IPV4, elements.get(0).getForNode().getType());
        Assert.assertEquals(Node.Type.IPV6, elements.get(1).getForNode().getType());
        Assert.assertEquals(Node.Type.UNKNOWN, elements.get(2).getForNode().getType());
    }


    @Test
    public void testMalformedFieldRejectsAll() throws Exception {
        Assert.assertNull(Forwarded.parse(Collections.enumeration(Arrays.asList("for=192.0.2.43", "for=\"x"))));
    }


    @Test
    public void testNoFields() throws Exception {
        Assert.assertTrue(Forwarded.parse(Collections.<String>emptyEnumeration()).isEmpty());
    }


    @Test
    public void testRejected() throws Exception {
        for (String s : new String[] { "for=\"unterminated, for=1.2.3.4", "for=", "=x", "for 1.2.3.4", "for=a b",
                "for=1.2.3.4;ext=", "for=1.2.3.4;ext=;proto=http" }) {
            Assert.assertNull(s, parseLine(s));
        }
    }


    @Test
    public void testEmptyQuotedString() throws Exception {
        List<Element> elements = parseLine("for=192.0.2.1;ext=\"\"");
        Assert.assertEquals(1, elements.size());
        Assert.assertEquals("192.0.2.1", elements.get(0).getFor());
        Assert.assertEquals("for=192.0.2.1;ext=\"\"", elements.get(0).toHeaderValue());

        // An empty value is not a node so the element does not identify a client
        elements = parseLine("for=\"\";proto=https");
        Assert.assertEquals(1, elements.size());
        Assert.assertNull(elements.get(0).getForNode());
        Assert.assertEquals("https", elements.get(0).getProto());
    }


    /*
     * RFC 7239: port = 1*5DIGIT. The value is not checked against the range of a TCP port.
     */
    @Test
    public void testNodePortSyntax() {
        Assert.assertEquals("99999", Node.parse("[::1]:99999").getPort());
        Assert.assertEquals("00080", Node.parse("192.0.2.1:00080").getPort());
        Assert.assertEquals("1", Node.parse("192.0.2.1:1").getPort());
        Assert.assertEquals("_p-1.x", Node.parse("192.0.2.1:_p-1.x").getPort());
        Assert.assertNull(Node.parse("192.0.2.1:100000"));
        Assert.assertNull(Node.parse("192.0.2.1:_"));
    }


    @Test
    public void testIPv6Nodes() {
        Assert.assertEquals(Node.Type.IPV6, Node.parse("[::1]").getType());
        Assert.assertEquals(Node.Type.IPV6, Node.parse("[2001:db8:cafe::17]:4711").getType());
        Assert.assertEquals("::ffff:192.0.2.1", Node.parse("[::ffff:192.0.2.1]").getName());
        Assert.assertEquals(Node.Type.IPV6, Node.parse("[64:ff9b::192.0.2.33]").getType());
        for (String s : new String[] { "[1.2.3.4]", "[::1%25eth0]", "[::1%eth0]", "[]", "[:::1]", "[1::2::3]",
                "[::ffff:192.0.2.256]", "[12345::1]", "[::g]" }) {
            Assert.assertNull(s, Node.parse(s));
        }
    }


    @Test
    public void testQuotedStringUnescaped() throws Exception {
        Assert.assertEquals("ex\"a", parseLine("host=\"ex\\\"a\"").get(0).getHost());
    }


    @Test
    public void testToHeaderValue() throws Exception {
        String[] values = new String[] { "for=1.2.3.4;proto=https", "for=\"[2001:db8::1]:4711\";host=\"ex\\\"a\"",
                "for=_hidden;x-ext=abc" };
        for (String s : values) {
            Assert.assertEquals(s, parseLine(s).get(0).toHeaderValue());
        }
        Assert.assertEquals("for=\"1.2.3.4:80\";proto=http", parseLine("FOR=\"1.2.3.4:80\";Proto=http").get(0).toHeaderValue());
    }
}
