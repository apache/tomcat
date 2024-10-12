package org.apache.tomcat.util.digester;

import org.xml.sax.Attributes;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


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
    public void testBegin_ObjectIsCreatedAndPushed() throws Exception {
        Attributes attributes = new MockAttributes();

        rule.begin("", "element", attributes);

        // Check if the object was pushed onto the stack
        Object topObject = digester.pop();
        assertTrue(topObject instanceof String);
    }

    @Test
    public void testBegin_WithAttributeNameOverride() throws Exception {
        rule = new ObjectCreateRule("java.lang.Object", "className");
        rule.setDigester(digester);

        MockAttributes attributes = new MockAttributes();
        attributes.setAttribute("className", "java.lang.String");

        rule.begin("", "element", attributes);

        // Check if the overridden class object was pushed onto the stack
        Object topObject = digester.pop();
        assertTrue(topObject instanceof String);
    }

    @Test(expected = NullPointerException.class)
    public void testBegin_NullClassNameThrowsException() throws Exception {
        rule = new ObjectCreateRule(null);
        rule.setDigester(digester);
        rule.begin("", "element", new MockAttributes());
    }

    @Test
    public void testEnd_ObjectIsPopped() throws Exception {
        String instance = "Test Object";
        digester.push(instance);

        rule.end("", "element");

        // Check if the object was popped from the stack
        assertNull(digester.peek());
    }

    @Test
    public void testToString() {
        rule = new ObjectCreateRule("java.lang.String", "className");
        String result = rule.toString();
        assertTrue(result.contains("className=java.lang.String"));
        assertTrue(result.contains("attributeName=className"));
    }

    // MockAttributes class to simulate the SAX Attributes without a mocking library
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
