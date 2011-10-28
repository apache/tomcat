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

import java.util.Enumeration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import org.apache.tomcat.util.buf.UEncoder;

public class TestParameters {

    private static final Parameter SIMPLE =
        new Parameter("foo1", "bar1");
    private static final Parameter SIMPLE_MULTIPLE =
        new Parameter("foo2", "bar1", "bar2");
    private static final Parameter NO_VALUE =
        new Parameter("foo3");
    private static final Parameter EMPTY_VALUE =
        new Parameter("foo4", "");
    private static final Parameter EMPTY =
        new Parameter("");

    @Test
    public void testProcessParametersByteArrayIntInt() {
        doTestProcessParametersByteArrayIntInt(SIMPLE);
        doTestProcessParametersByteArrayIntInt(SIMPLE_MULTIPLE);
        doTestProcessParametersByteArrayIntInt(NO_VALUE);
        doTestProcessParametersByteArrayIntInt(EMPTY_VALUE);
        doTestProcessParametersByteArrayIntInt(EMPTY);
        doTestProcessParametersByteArrayIntInt(
                SIMPLE, SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE, EMPTY);
        doTestProcessParametersByteArrayIntInt(
                SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE, EMPTY, SIMPLE);
        doTestProcessParametersByteArrayIntInt(
                NO_VALUE, EMPTY_VALUE, EMPTY, SIMPLE, SIMPLE_MULTIPLE);
        doTestProcessParametersByteArrayIntInt(
                EMPTY_VALUE, EMPTY, SIMPLE, SIMPLE_MULTIPLE, NO_VALUE);
        doTestProcessParametersByteArrayIntInt(
                EMPTY, SIMPLE, SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE);
    }

    // Make sure the inner Parameter class behaves correctly
    @Test
    public void testInternal() {
        assertEquals("foo1=bar1", SIMPLE.toString());
        assertEquals("foo2=bar1&foo2=bar2", SIMPLE_MULTIPLE.toString());
        assertEquals("foo3", NO_VALUE.toString());
        assertEquals("foo4=", EMPTY_VALUE.toString());
    }

    private long doTestProcessParametersByteArrayIntInt(
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
        p.setEncoding("UTF-8");

        long start = System.nanoTime();
        p.processParameters(data, 0, data.length);
        long end = System.nanoTime();

        validateParameters(parameters, p);
        return end - start;
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
                        assertArrayEquals(new String[] {""}, values);
                    } else {
                        assertArrayEquals(parameter.getValues(), values);
                    }
                    break;
                }
            }
            assertTrue(match);
        }
    }

    private static class Parameter {
        private final String name;
        private final String[] values;
        private final UEncoder uencoder = new UEncoder();

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
            StringBuilder result = new StringBuilder();
            boolean first = true;
            if (values.length == 0) {
                return name;
            }
            for (String value : values) {
                if (first) {
                    first = false;
                } else {
                    result.append('&');
                }
                if (name != null) {
                    result.append(uencoder.encodeURL(name));
                }
                if (value != null) {
                    result.append('=');
                    result.append(value);
                }
            }

            return result.toString();
        }
    }
}
