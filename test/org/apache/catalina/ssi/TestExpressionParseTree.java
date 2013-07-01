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

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TestExpressionParseTree {

    private static final long LAST_MODIFIED = 60 * 60 * 24 * 1000;


    @Test
    public void testSimple1() throws Exception {
        SSIMediator mediator =
                new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        ExpressionParseTree ept =
                new ExpressionParseTree("a = a", mediator);
        Assert.assertTrue(ept.evaluateTree());
    }


    @Test
    public void testSimple2() throws Exception {
        SSIMediator mediator =
                new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        ExpressionParseTree ept =
                new ExpressionParseTree("a = b", mediator);
        Assert.assertFalse(ept.evaluateTree());
    }


    @Test
    public void testSimple3() throws Exception {
        SSIMediator mediator =
                new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        ExpressionParseTree ept =
                new ExpressionParseTree("a = /a/", mediator);
        Assert.assertTrue(ept.evaluateTree());
    }


    @Test
    public void testSimple4() throws Exception {
        SSIMediator mediator =
                new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        ExpressionParseTree ept =
                new ExpressionParseTree("a = /b/", mediator);
        Assert.assertFalse(ept.evaluateTree());
    }


    @Test
    public void testSimple5() throws Exception {
        SSIExternalResolver r = new TesterSSIExternalResolver();
        r.setVariableValue("QUERY_STRING", "a");
        SSIMediator mediator = new SSIMediator(r, LAST_MODIFIED);
        ExpressionParseTree ept =
                new ExpressionParseTree("$QUERY_STRING = a", mediator);
        Assert.assertTrue(ept.evaluateTree());
    }


    @Test
    public void testSimple6() throws Exception {
        SSIExternalResolver r = new TesterSSIExternalResolver();
        r.setVariableValue("QUERY_STRING", "a");
        SSIMediator mediator = new SSIMediator(r, LAST_MODIFIED);
        ExpressionParseTree ept =
                new ExpressionParseTree("$QUERY_STRING = b", mediator);
        Assert.assertFalse(ept.evaluateTree());
    }


    @Test
    public void testSimple7() throws Exception {
        SSIExternalResolver r = new TesterSSIExternalResolver();
        r.setVariableValue("QUERY_STRING", "a");
        SSIMediator mediator = new SSIMediator(r, LAST_MODIFIED);
        ExpressionParseTree ept =
                new ExpressionParseTree("$QUERY_STRING = /a/", mediator);
        Assert.assertTrue(ept.evaluateTree());
    }


    @Test
    public void testSimple8() throws Exception {
        SSIExternalResolver r = new TesterSSIExternalResolver();
        r.setVariableValue("QUERY_STRING", "a");
        SSIMediator mediator = new SSIMediator(r, LAST_MODIFIED);
        ExpressionParseTree ept =
                new ExpressionParseTree("$QUERY_STRING = /b/", mediator);
        Assert.assertFalse(ept.evaluateTree());
    }


    @Test
    public void testBug55176a() throws Exception {
        SSIExternalResolver r = new TesterSSIExternalResolver();
        r.setVariableValue("QUERY_STRING", "a=");
        SSIMediator mediator = new SSIMediator(r, LAST_MODIFIED);
        ExpressionParseTree ept =
                new ExpressionParseTree("$QUERY_STRING = /a=/", mediator);
        Assert.assertTrue(ept.evaluateTree());
    }


    @Test
    public void testBug55176b() throws Exception {
        SSIExternalResolver r = new TesterSSIExternalResolver();
        r.setVariableValue("QUERY_STRING", "a");
        SSIMediator mediator = new SSIMediator(r, LAST_MODIFIED);
        ExpressionParseTree ept =
                new ExpressionParseTree("$QUERY_STRING = /a=/", mediator);
        Assert.assertFalse(ept.evaluateTree());
    }


    /**
     * Minimal implementation that provides the bare essentials require for the
     * unit tests.
     */
    private static class TesterSSIExternalResolver
            implements SSIExternalResolver {

        private Map<String,String> variables = new HashMap<>();

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
        public long getFileSize(String path, boolean virtual)
                throws IOException {
            return 0;
        }

        @Override
        public long getFileLastModified(String path, boolean virtual)
                throws IOException {
            return 0;
        }

        @Override
        public String getFileText(String path, boolean virtual)
                throws IOException {
            return null;
        }

        @Override
        public void log(String message, Throwable throwable) {
            // NO-OP
        }
    }
}
