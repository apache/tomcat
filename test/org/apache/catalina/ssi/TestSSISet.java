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
        // The attributes may be given in any order, as Apache does
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "value", "var" }, new String[] { "bar", "foo" });
        Assert.assertEquals("", output);
        Assert.assertEquals("bar", mediator.getVariableValue("foo"));
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
    public void testDuplicateAttributes() throws Exception {
        // Later occurrences of an attribute win, regardless of order
        SSIMediator mediator = newMediator();
        String output = process(mediator, new String[] { "value", "value", "var" },
                new String[] { "bar1", "bar2", "foo" });
        Assert.assertEquals("", output);
        Assert.assertEquals("bar2", mediator.getVariableValue("foo"));

        mediator = newMediator();
        output = process(mediator, new String[] { "var", "value", "value" },
                new String[] { "foo", "bar1", "bar2" });
        Assert.assertEquals("", output);
        Assert.assertEquals("bar2", mediator.getVariableValue("foo"));
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
