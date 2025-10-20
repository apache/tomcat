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
package org.apache.catalina.core;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.connector.Request;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.RequestUtil;

@RunWith(value = Parameterized.class)
public class TestApplicationContextStripPathParams extends TomcatBaseTest {

    private final String input;
    private final String expectedOutput;
    private final Boolean hasParameter;

    public TestApplicationContextStripPathParams(String input, String expectedOutput, Boolean hasParameter) {
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.hasParameter = hasParameter;
    }

    @Parameters(name = "{index}: input[{0}]")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] { { "/foo", "/foo", Boolean.FALSE }, { "/foo/", "/foo/", Boolean.FALSE },
                { "/foo/bar", "/foo/bar", Boolean.FALSE }, { "/foo;", "/foo", Boolean.FALSE },
                { "/foo;/", "/foo/", Boolean.FALSE }, { "/foo;/bar", "/foo/bar", Boolean.FALSE },
                { "/foo;a=1", "/foo", Boolean.TRUE }, { "/foo;a=1/", "/foo/", Boolean.TRUE },
                { "/foo;a=1/bar", "/foo/bar", Boolean.TRUE },
                // Arguably not valid but does the right thing anyway
                { ";/foo", "/foo", Boolean.FALSE }, { ";a=1/foo", "/foo", Boolean.TRUE },
                { ";/foo/bar", "/foo/bar", Boolean.FALSE }, { ";/foo;a=1/bar", "/foo/bar", Boolean.TRUE },
                { ";/foo;=/bar", "/foo/bar", Boolean.FALSE }, { ";/foo;a=/bar", "/foo/bar", Boolean.FALSE },
                { ";/foo;=1/bar", "/foo/bar", Boolean.FALSE }, { "/foo;a=1;b=1/bar", "/foo/bar", Boolean.TRUE },
                { ";/foo;a=1;b=1/bar", "/foo/bar", Boolean.TRUE }, { "/foo;b=1;a=1/bar", "/foo/bar", Boolean.TRUE },
                { ";/foo;b=1;a=1/bar", "/foo/bar", Boolean.TRUE }, });
    }

    @Test
    public void testStringPathParams() {
        Request request = new Request(null, new org.apache.coyote.Request());
        String output = RequestUtil.stripPathParams(input, request);
        Assert.assertEquals(expectedOutput, output);
        String parameter = request.getPathParameter("a");
        if (hasParameter.booleanValue()) {
            Assert.assertEquals("1", parameter);
        } else {
            Assert.assertNull(parameter);
        }
    }
}