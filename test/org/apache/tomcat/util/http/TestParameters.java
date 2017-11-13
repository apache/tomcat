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
package org.apache.tomcat.util.http;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import org.junit.Assert;
import org.junit.Test;

public class TestParameters {

    private static final Parameter SIMPLE =
        new Parameter("foo1", "bar1");
    private static final Parameter SIMPLE_MULTIPLE =
        new Parameter("foo2", "bar1", "bar2", "hello world", "?%@");
    private static final Parameter NO_VALUE =
        new Parameter("foo3");
    private static final Parameter EMPTY_VALUE =
        new Parameter("foo4", "");
    private static final Parameter EMPTY =
        new Parameter("");
    private static final Parameter UTF8 =
            new Parameter("\ufb6b\ufb6a\ufb72", "\uffee\uffeb\uffe2");

    @Test
    public void testProcessParametersByteArrayIntInt() {
        doTestProcessParametersByteArrayIntInt(-1, SIMPLE);
        doTestProcessParametersByteArrayIntInt(-1, SIMPLE_MULTIPLE);
        doTestProcessParametersByteArrayIntInt(-1, NO_VALUE);
        doTestProcessParametersByteArrayIntInt(-1, EMPTY_VALUE);
        doTestProcessParametersByteArrayIntInt(-1, EMPTY);
        doTestProcessParametersByteArrayIntInt(-1, UTF8);
        doTestProcessParametersByteArrayIntInt(-1,
                SIMPLE, SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE, EMPTY, UTF8);
        doTestProcessParametersByteArrayIntInt(-1,
                SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE, EMPTY, UTF8, SIMPLE);
        doTestProcessParametersByteArrayIntInt(-1,
                NO_VALUE, EMPTY_VALUE, EMPTY, UTF8, SIMPLE, SIMPLE_MULTIPLE);
        doTestProcessParametersByteArrayIntInt(-1,
                EMPTY_VALUE, EMPTY, UTF8, SIMPLE, SIMPLE_MULTIPLE, NO_VALUE);
        doTestProcessParametersByteArrayIntInt(-1,
                EMPTY, UTF8, SIMPLE, SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE);
        doTestProcessParametersByteArrayIntInt(-1,
                UTF8, SIMPLE, SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE, EMPTY);

        doTestProcessParametersByteArrayIntInt(1,
                SIMPLE, NO_VALUE, EMPTY_VALUE, UTF8);
        doTestProcessParametersByteArrayIntInt(2,
                SIMPLE, NO_VALUE, EMPTY_VALUE, UTF8);
        doTestProcessParametersByteArrayIntInt(3,
                SIMPLE, NO_VALUE, EMPTY_VALUE, UTF8);
        doTestProcessParametersByteArrayIntInt(4,
                SIMPLE, NO_VALUE, EMPTY_VALUE, UTF8);
    }

    // Make sure the inner Parameter class behaves correctly
    @Test
    public void testInternal() {
        Assert.assertEquals("foo1=bar1", SIMPLE.toString());
        // Note: testing requires that ' ' is encoded as '+',
        // because that is what browsers will send us.
        Assert.assertEquals("foo2=bar1&foo2=bar2&foo2=hello+world&foo2=%3F%25%40",
                SIMPLE_MULTIPLE.toString());
        Assert.assertEquals("foo3", NO_VALUE.toString());
        Assert.assertEquals("foo4=", EMPTY_VALUE.toString());
    }

    private long doTestProcessParametersByteArrayIntInt(int limit,
            Parameter... parameters) {

        // Build the byte array
        StringBuilder input = new StringBuilder();
        boolean first = true;
        for (Parameter parameter : parameters) {
            if (first) {
                first = false;
            } else {
                input.append('&');
            }
            input.append(parameter.toString());
        }

        byte[] data = input.toString().getBytes();

        Parameters p = new Parameters();
        p.setCharset(StandardCharsets.UTF_8);
        p.setLimit(limit);

        long start = System.nanoTime();
        p.processParameters(data, 0, data.length);
        long end = System.nanoTime();

        if (limit == -1) {
            validateParameters(parameters, p);
        } else {
            Parameter[] limitParameters = new Parameter[limit];
            System.arraycopy(parameters, 0, limitParameters, 0, limit);
            validateParameters(limitParameters, p);
        }
        return end - start;
    }

    @Test
    public void testNonExistantParameter() {
        Parameters p = new Parameters();

        String value = p.getParameter("foo");
        Assert.assertNull(value);

        Enumeration<String> names = p.getParameterNames();
        Assert.assertFalse(names.hasMoreElements());

        String[] values = p.getParameterValues("foo");
        Assert.assertNull(values);
    }


    @Test
    public void testAddParameters() {
        Parameters p = new Parameters();

        // Empty at this point
        Enumeration<String> names = p.getParameterNames();
        Assert.assertFalse(names.hasMoreElements());
        String[] values = p.getParameterValues("foo");
        Assert.assertNull(values);

        // Add a parameter with two values
        p.addParameter("foo", "value1");
        p.addParameter("foo", "value2");

        names = p.getParameterNames();
        Assert.assertTrue(names.hasMoreElements());
        Assert.assertEquals("foo", names.nextElement());
        Assert.assertFalse(names.hasMoreElements());

        values = p.getParameterValues("foo");
        Assert.assertEquals(2, values.length);
        Assert.assertEquals("value1", values[0]);
        Assert.assertEquals("value2", values[1]);

        // Add two more values
        p.addParameter("foo", "value3");
        p.addParameter("foo", "value4");

        names = p.getParameterNames();
        Assert.assertTrue(names.hasMoreElements());
        Assert.assertEquals("foo", names.nextElement());
        Assert.assertFalse(names.hasMoreElements());

        values = p.getParameterValues("foo");
        Assert.assertEquals(4, values.length);
        Assert.assertEquals("value1", values[0]);
        Assert.assertEquals("value2", values[1]);
        Assert.assertEquals("value3", values[2]);
        Assert.assertEquals("value4", values[3]);
    }

    @Test
    public void testAddParametersLimit() {
        Parameters p = new Parameters();

        p.setLimit(2);

        // Empty at this point
        Enumeration<String> names = p.getParameterNames();
        Assert.assertFalse(names.hasMoreElements());
        String[] values = p.getParameterValues("foo1");
        Assert.assertNull(values);

        // Add a parameter
        p.addParameter("foo1", "value1");

        names = p.getParameterNames();
        Assert.assertTrue(names.hasMoreElements());
        Assert.assertEquals("foo1", names.nextElement());
        Assert.assertFalse(names.hasMoreElements());

        values = p.getParameterValues("foo1");
        Assert.assertEquals(1, values.length);
        Assert.assertEquals("value1", values[0]);

        // Add another parameter
        p.addParameter("foo2", "value2");

        names = p.getParameterNames();
        Assert.assertTrue(names.hasMoreElements());
        Assert.assertEquals("foo1", names.nextElement());
        Assert.assertEquals("foo2", names.nextElement());
        Assert.assertFalse(names.hasMoreElements());

        values = p.getParameterValues("foo1");
        Assert.assertEquals(1, values.length);
        Assert.assertEquals("value1", values[0]);

        values = p.getParameterValues("foo2");
        Assert.assertEquals(1, values.length);
        Assert.assertEquals("value2", values[0]);

        // Add another parameter
        IllegalStateException e = null;
        try {
            p.addParameter("foo3", "value3");
        } catch (IllegalStateException ise) {
            e = ise;
        }
        Assert.assertNotNull(e);

        // Check current parameters remain unaffected
        names = p.getParameterNames();
        Assert.assertTrue(names.hasMoreElements());
        Assert.assertEquals("foo1", names.nextElement());
        Assert.assertEquals("foo2", names.nextElement());
        Assert.assertFalse(names.hasMoreElements());

        values = p.getParameterValues("foo1");
        Assert.assertEquals(1, values.length);
        Assert.assertEquals("value1", values[0]);

        values = p.getParameterValues("foo2");
        Assert.assertEquals(1, values.length);
        Assert.assertEquals("value2", values[0]);

    }

    private void validateParameters(Parameter[] parameters, Parameters p) {
        Enumeration<String> names = p.getParameterNames();

        int i = 0;
        while (names.hasMoreElements()) {
            while (parameters[i].getName() == null) {
                i++;
            }

            String name = names.nextElement();
            String[] values = p.getParameterValues(name);

            boolean match = false;

            for (Parameter parameter : parameters) {
                if (name.equals(parameter.getName())) {
                    match = true;
                    if (parameter.values.length == 0) {
                        // Special case
                        Assert.assertArrayEquals(new String[] {""}, values);
                    } else {
                        Assert.assertArrayEquals(parameter.getValues(), values);
                    }
                    break;
                }
            }
            Assert.assertTrue(match);
        }
    }

    private static class Parameter {
        private final String name;
        private final String[] values;

        public Parameter(String name, String... values) {
            this.name = name;
            this.values = values;
        }

        public String getName() {
            return name;
        }

        public String[] getValues() {
            return values;
        }

        @Override
        public String toString() {
            try {
                StringBuilder result = new StringBuilder();
                boolean first = true;
                if (values.length == 0) {
                    return URLEncoder.encode(name, "UTF-8");
                }
                for (String value : values) {
                    if (first) {
                        first = false;
                    } else {
                        result.append('&');
                    }
                    if (name != null) {
                        result.append(URLEncoder.encode(name, "UTF-8"));
                    }
                    if (value != null) {
                        result.append('=');
                        result.append(URLEncoder.encode(value, "UTF-8"));
                    }
                }
                return result.toString();
            } catch (UnsupportedEncodingException ex) {
                return ex.toString();
            }
        }
    }
}
