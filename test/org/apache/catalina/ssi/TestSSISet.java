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
package org.apache.catalina.ssi;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TestSSISet {

    @Test
    public void testValueBeforeVar() throws Exception {
        // As for Apache, the var attribute must precede the value attribute
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "value", "var" }, new String[] { "bar", "foo" });
        Assert.assertEquals(mediator.getConfigErrMsg(), output);
        Assert.assertNull(mediator.getVariableValue("foo"));
    }


    @Test
    public void testVarBeforeValue() throws Exception {
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "var", "value" }, new String[] { "foo", "bar" });
        Assert.assertEquals("", output);
        Assert.assertEquals("bar", mediator.getVariableValue("foo"));
    }


    @Test
    public void testVarOnly() throws Exception {
        // The value attribute is required, as it is for Apache
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "var" }, new String[] { "foo" });
        Assert.assertEquals(mediator.getConfigErrMsg(), output);
        Assert.assertNull(mediator.getVariableValue("foo"));
    }


    @Test
    public void testValueWithoutVar() throws Exception {
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "value" }, new String[] { "bar" });
        Assert.assertEquals(mediator.getConfigErrMsg(), output);
        Assert.assertNull(mediator.getVariableValue("foo"));
    }


    @Test
    public void testMultiplePairs() throws Exception {
        // Each value applies to the preceding var, as for Apache
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "var", "value", "var", "value" },
                new String[] { "a", "x", "b", "y" });
        Assert.assertEquals("", output);
        Assert.assertEquals("x", mediator.getVariableValue("a"));
        Assert.assertEquals("y", mediator.getVariableValue("b"));
    }


    @Test
    public void testRepeatedValue() throws Exception {
        // Each value is assigned to the current var so the last one wins
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "var", "value", "value", "var", "value" },
                new String[] { "a", "1", "2", "b", "3" });
        Assert.assertEquals("", output);
        Assert.assertEquals("2", mediator.getVariableValue("a"));
        Assert.assertEquals("3", mediator.getVariableValue("b"));
    }


    @Test
    public void testRepeatedVar() throws Exception {
        // Only the last var before a value is set
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "var", "var", "value" },
                new String[] { "a", "b", "y" });
        Assert.assertEquals("", output);
        Assert.assertNull(mediator.getVariableValue("a"));
        Assert.assertEquals("y", mediator.getVariableValue("b"));
    }


    @Test
    public void testTrailingVar() throws Exception {
        // A trailing var without a value is ignored once something has been set, as for Apache
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "var", "value", "var" }, new String[] { "a", "x", "b" });
        Assert.assertEquals("", output);
        Assert.assertEquals("x", mediator.getVariableValue("a"));
        Assert.assertNull(mediator.getVariableValue("b"));
    }


    @Test
    public void testLeadingValueStopsProcessing() throws Exception {
        // The leading value is an error so the later var/value pair is never applied
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "value", "var", "value" }, new String[] { "x", "a", "y" });
        Assert.assertEquals(mediator.getConfigErrMsg(), output);
        Assert.assertNull(mediator.getVariableValue("a"));
    }


    @Test
    public void testNoAttributes() throws Exception {
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[0], new String[0]);
        Assert.assertEquals(mediator.getConfigErrMsg(), output);
    }


    @Test
    public void testEmptyValue() throws Exception {
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "var", "value" }, new String[] { "foo", "" });
        Assert.assertEquals("", output);
        Assert.assertEquals("", mediator.getVariableValue("foo"));
    }


    @Test
    public void testInvalidAttribute() throws Exception {
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "name" }, new String[] { "foo" });
        Assert.assertEquals(mediator.getConfigErrMsg(), output);
    }


    private SSIMediator newMediator() {
        return new SSIMediator(new TesterSSIExternalResolver(), 0);
    }


    private String process(SSIMediator mediator, String[] paramNames, String[] paramValues) throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        try {
            new SSISet().process(mediator, "set", paramNames, paramValues, writer);
        } catch (SSIStopProcessingException e) {
            // Expected for the error cases
        }
        writer.flush();
        return stringWriter.toString();
    }

    /**
     * Minimal implementation that provides the bare essentials required for the unit tests.
     */
    private static class TesterSSIExternalResolver implements SSIExternalResolver {

        private final Map<String,String> variables = new HashMap<>();

        @Override
        public void addVariableNames(Collection<String> variableNames) {
            // NO-OP
        }

        @Override
        public String getVariableValue(String name) {
            return variables.get(name);
        }

        @Override
        public void setVariableValue(String name, String value) {
            variables.put(name, value);
        }

        @Override
        public Date getCurrentDate() {
            return null;
        }

        @Override
        public long getFileSize(String path, boolean virtual) {
            return 0;
        }

        @Override
        public long getFileLastModified(String path, boolean virtual) {
            return 0;
        }

        @Override
        public String getFileText(String path, boolean virtual) {
            return null;
        }

        @Override
        public void log(String message, Throwable throwable) {
            // NO-OP
        }
    }
}
