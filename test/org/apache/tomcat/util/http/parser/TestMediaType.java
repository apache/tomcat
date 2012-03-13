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
package org.apache.tomcat.util.http.parser;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link HttpParser} focusing on media-type as defined in
 * section 3.7 of RFC 2616.
 */
public class TestMediaType {

    // Include whitespace to ensure Parser handles it correctly (it should be
    // skipped).
    private static final String TYPE = " foo ";
    private static final String SUBTYPE = " bar ";
    private static final String TYPES = TYPE + "/" + SUBTYPE;

    private static final Parameter PARAM_TOKEN =
            new Parameter("a", "b");
    private static final Parameter PARAM_QUOTED =
            new Parameter("x", "y");
    private static final Parameter PARAM_EMPTY_QUOTED =
            new Parameter("z", "\"\"");
    private static final Parameter PARAM_COMPLEX_QUOTED =
            new Parameter("w", "\"foo'bar,a=b;x=y\"");
    private static final String CHARSET = "UTF-8";
    private static final String WS_CHARSET = " \tUTF-8";
    private static final String CHARSET_WS = "UTF-8 \t";
    // Since this is quoted, it should retain the space at the end
    private static final String CHARSET_QUOTED = "\"" + CHARSET_WS + "\"";
    private static final Parameter PARAM_CHARSET =
            new Parameter("charset", CHARSET);
    private static final Parameter PARAM_WS_CHARSET =
            new Parameter("charset", WS_CHARSET);
    private static final Parameter PARAM_CHARSET_WS =
            new Parameter("charset", CHARSET_WS);
    private static final Parameter PARAM_CHARSET_QUOTED =
            new Parameter("charset", CHARSET_QUOTED);


    @Test
    public void testSimple() throws ParseException {
        doTest();
    }


    @Test
    public void testSimpleWithToken() throws ParseException {
        doTest(PARAM_TOKEN);
    }


    @Test
    public void testSimpleWithQuotedString() throws ParseException {
        doTest(PARAM_QUOTED);
    }


    @Test
    public void testSimpleWithEmptyQuotedString() throws ParseException {
        doTest(PARAM_EMPTY_QUOTED);
    }


    @Test
    public void testSimpleWithComplesQuotedString() throws ParseException {
        doTest(PARAM_COMPLEX_QUOTED);
    }


    @Test
    public void testSimpleWithCharset() throws ParseException {
        doTest(PARAM_CHARSET);
    }


    @Test
    public void testSimpleWithCharsetWhitespaceBefore() throws ParseException {
        doTest(PARAM_WS_CHARSET);
    }


    @Test
    public void testSimpleWithCharsetWhitespaceAfter() throws ParseException {
        doTest(PARAM_CHARSET_WS);
    }


    @Test
    public void testSimpleWithCharsetQuoted() throws ParseException {
        doTest(PARAM_CHARSET_QUOTED);
    }


    @Test
    public void testSimpleWithAll() throws ParseException {
        doTest(PARAM_COMPLEX_QUOTED, PARAM_EMPTY_QUOTED, PARAM_QUOTED,
                PARAM_TOKEN, PARAM_CHARSET);
    }


    @Test
    public void testCharset() throws ParseException {
        StringBuilder sb = new StringBuilder();
        sb.append(TYPES);
        sb.append(PARAM_CHARSET);
        sb.append(PARAM_TOKEN);

        StringReader sr = new StringReader(sb.toString());
        HttpParser hp = new HttpParser(sr);
        AstMediaType m = hp.MediaType();

        assertEquals(sb.toString().replaceAll(" ", ""), m.toString());
        assertEquals(CHARSET, m.getCharset());
        assertEquals(TYPES.replaceAll(" ", "") + PARAM_TOKEN,
                m.toStringNoCharset());
    }


    @Test
    public void testCharsetQuoted() throws ParseException {
        StringBuilder sb = new StringBuilder();
        sb.append(TYPES);
        sb.append(PARAM_CHARSET_QUOTED);

        StringReader sr = new StringReader(sb.toString());
        HttpParser hp = new HttpParser(sr);
        AstMediaType m = hp.MediaType();

        assertEquals(CHARSET_WS, m.getCharset());
        assertEquals(TYPES.replaceAll(" ", ""),
                m.toStringNoCharset());
    }


    @Test
    public void testBug52811() throws ParseException {
        String input = "multipart/related;boundary=1_4F50BD36_CDF8C28;" +
                "Start=\"<31671603.smil>\";" +
                "Type=\"application/smil;charset=UTF-8\"";

        StringReader sr = new StringReader(input);
        HttpParser hp = new HttpParser(sr);
        AstMediaType m = hp.MediaType();

        assertTrue(m.children.length == 5);

        // Check the types
        assertTrue(m.children[0] instanceof AstType);
        assertTrue(m.children[1] instanceof AstSubType);
        assertEquals("multipart", m.children[0].toString());
        assertEquals("related", m.children[1].toString());

        // Check the parameters
        AstParameter p = (AstParameter) m.children[2];
        assertTrue(p.children.length == 2);
        assertTrue(p.children[0] instanceof AstAttribute);
        assertTrue(p.children[1] instanceof AstValue);
        assertEquals("boundary", p.children[0].toString());
        assertEquals("1_4F50BD36_CDF8C28", p.children[1].toString());

        p = (AstParameter) m.children[3];
        assertTrue(p.children.length == 2);
        assertTrue(p.children[0] instanceof AstAttribute);
        assertTrue(p.children[1] instanceof AstValue);
        assertEquals("Start", p.children[0].toString());
        assertEquals("\"<31671603.smil>\"", p.children[1].toString());

        p = (AstParameter) m.children[4];
        assertTrue(p.children.length == 2);
        assertTrue(p.children[0] instanceof AstAttribute);
        assertTrue(p.children[1] instanceof AstValue);
        assertEquals("Type", p.children[0].toString());
        assertEquals("\"application/smil;charset=UTF-8\"",
                p.children[1].toString());

        assertEquals(input, m.toString());
        assertEquals(input, m.toStringNoCharset());
        assertNull(m.getCharset());
    }


    private void doTest(Parameter... parameters) throws ParseException {
        StringBuilder sb = new StringBuilder();
        sb.append(TYPES);
        for (Parameter p : parameters) {
            sb.append(p.toString());
        }

        StringReader sr = new StringReader(sb.toString());
        HttpParser hp = new HttpParser(sr);
        AstMediaType m = hp.MediaType();

        // Check all expected children are present
        assertTrue(m.children.length == 2 + parameters.length);

        // Check the types
        assertTrue(m.children[0] instanceof AstType);
        assertTrue(m.children[1] instanceof AstSubType);
        assertEquals(TYPE.trim(), m.children[0].toString());
        assertEquals(SUBTYPE.trim(), m.children[1].toString());

        // Check the parameters
        for (int i = 0; i <  parameters.length; i++) {
            assertTrue(m.children[i + 2] instanceof AstParameter);
            AstParameter p = (AstParameter) m.children[i + 2];
            assertTrue(p.children.length == 2);
            assertTrue(p.children[0] instanceof AstAttribute);
            assertTrue(p.children[1] instanceof AstValue);
            assertEquals(parameters[i].getName().trim(), p.children[0].toString());
            assertEquals(parameters[i].getValue().trim(), p.children[1].toString());
        }
    }


    private static class Parameter {
        private final String name;
        private final String value;

        public Parameter(String name,String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(";");
            sb.append(name);
            sb.append("=");
            sb.append(value);
            return sb.toString();
        }
    }
}
