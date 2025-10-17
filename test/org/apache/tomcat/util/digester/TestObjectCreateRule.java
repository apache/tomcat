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
package org.apache.tomcat.util.digester;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xml.sax.Attributes;

public class TestObjectCreateRule {

    private Digester digester;
    private ObjectCreateRule rule;

    @Before
    public void setUp() {
        digester = new Digester();
        rule = new ObjectCreateRule("java.lang.String");
        rule.setDigester(digester);
    }

    @Test
    public void testBeginObjectIsCreatedAndPushed() throws Exception {
        Attributes attributes = new MockAttributes();

        rule.begin("", "element", attributes);

        // Check if the object was pushed onto the stack
        Object topObject = digester.pop();
        Assert.assertTrue(topObject instanceof String);
    }

    @Test
    public void testBeginWithAttributeNameOverride() throws Exception {
        rule = new ObjectCreateRule("java.lang.Object", "className");
        rule.setDigester(digester);

        MockAttributes attributes = new MockAttributes();
        attributes.setAttribute("className", "java.lang.String");

        rule.begin("", "element", attributes);

        // Check if the overridden class object was pushed onto the stack
        Object topObject = digester.pop();
        Assert.assertTrue(topObject instanceof String);
    }

    @Test(expected = NullPointerException.class)
    public void testBeginNullClassNameThrowsException() throws Exception {
        rule = new ObjectCreateRule(null);
        rule.setDigester(digester);
        rule.begin("", "element", new MockAttributes());
    }

    @Test
    public void testEndObjectIsPopped() throws Exception {
        String instance = "Test Object";
        digester.push(instance);

        rule.end("", "element");

        // Check if the object was popped from the stack
        Assert.assertNull(digester.peek());
    }

    @Test
    public void testToString() {
        rule = new ObjectCreateRule("java.lang.String", "className");
        String result = rule.toString();
        Assert.assertTrue(result.contains("className=java.lang.String"));
        Assert.assertTrue(result.contains("attributeName=className"));
    }

    @Test
    public void testBeginClassNotFoundException() throws Exception {
        rule = new ObjectCreateRule("non.existent.Class", "className");
        rule.setDigester(digester);

        MockAttributes attributes = new MockAttributes();
        attributes.setAttribute("className", "non.existent.Class");

        try {
            rule.begin("", "element", attributes);
            Assert.fail("Expected ClassNotFoundException to be thrown");
        } catch (ClassNotFoundException e) {
            Assert.assertTrue(e.getMessage().contains("non.existent.Class"));
        }

        Assert.assertNull(digester.peek());
    }

    private class MockAttributes implements Attributes {

        private String attributeName;
        private String attributeValue;

        public void setAttribute(String name, String value) {
            this.attributeName = name;
            this.attributeValue = value;
        }

        @Override
        public int getLength() {
            return attributeName != null ? 1 : 0;
        }

        @Override
        public String getURI(int index) {
            return "";
        }

        @Override
        public String getLocalName(int index) {
            return attributeName;
        }

        @Override
        public String getQName(int index) {
            return attributeName;
        }

        @Override
        public String getType(int index) {
            return "CDATA";
        }

        @Override
        public String getValue(int index) {
            return attributeValue;
        }

        @Override
        public int getIndex(String uri, String localName) {
            return attributeName != null && attributeName.equals(localName) ? 0 : -1;
        }

        @Override
        public int getIndex(String qName) {
            return attributeName != null && attributeName.equals(qName) ? 0 : -1;
        }

        @Override
        public String getType(String uri, String localName) {
            return "CDATA";
        }

        @Override
        public String getType(String qName) {
            return "CDATA";
        }

        @Override
        public String getValue(String uri, String localName) {
            return attributeValue;
        }

        @Override
        public String getValue(String qName) {
            return attributeValue;
        }
    }
}