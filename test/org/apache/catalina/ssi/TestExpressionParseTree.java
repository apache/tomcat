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


    @Test
    public void testSubstituteVariablesPlainVar() throws Exception {
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        mediator.setVariableValue("VAR", "value");
        Assert.assertEquals("value", mediator.substituteVariables("$VAR"));
    }


    @Test
    public void testSubstituteVariablesBracedVar() throws Exception {
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        mediator.setVariableValue("VAR", "value");
        Assert.assertEquals("value", mediator.substituteVariables("${VAR}"));
    }


    @Test
    public void testSubstituteVariablesNoVar() throws Exception {
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        Assert.assertEquals("", mediator.substituteVariables("$UNKNOWN"));
    }


    @Test
    public void testSubstituteVariablesSingleBackslashEscapesDollar() throws Exception {
        // Input: \$VAR (1 backslash) -> $ is escaped, backslash consumed -> literal $VAR
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        mediator.setVariableValue("VAR", "value");
        String input = "\\" + "$VAR";
        Assert.assertEquals("$VAR", mediator.substituteVariables(input));
    }


    @Test
    public void testSubstituteVariablesTwoBackslashesVarSubstituted() throws Exception {
        // Input: \\$VAR (2 backslashes) -> even, $ not escaped, reduce to 1 -> \ + value
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        mediator.setVariableValue("VAR", "value");
        String input = "\\\\" + "$VAR";
        Assert.assertEquals("\\" + "value", mediator.substituteVariables(input));
    }


    @Test
    public void testSubstituteVariablesThreeBackslashesEscapesDollar() throws Exception {
        // Input: 3 backslashes + $VAR -> odd, $ escaped, keep 1 backslash -> \$VAR literal
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        mediator.setVariableValue("VAR", "value");
        String input = "\\\\" + "\\" + "$VAR";
        Assert.assertEquals("\\" + "$VAR", mediator.substituteVariables(input));
    }


    @Test
    public void testSubstituteVariablesFourBackslashesVarSubstituted() throws Exception {
        // Input: 4 backslashes + $VAR -> even, $ not escaped, reduce to 2 -> \\ + value
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        mediator.setVariableValue("VAR", "value");
        String input = "\\\\" + "\\\\" + "$VAR";
        Assert.assertEquals("\\\\" + "value", mediator.substituteVariables(input));
    }


    @Test
    public void testSubstituteVariablesEscapedDollarFollowedByVar() throws Exception {
        // Input: \$Y$Y -> escaped $Y (literal) followed by variable $Y -> $Y + value
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        mediator.setVariableValue("Y", "result");
        String input = "\\" + "$Y$Y";
        Assert.assertEquals("$Yresult", mediator.substituteVariables(input));
    }


    @Test
    public void testSubstituteVariablesNumericEntityBmp() throws Exception {
        // A numeric character reference in the BMP is decoded
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        Assert.assertEquals("A", mediator.substituteVariables("&#65;"));
    }


    @Test
    public void testSubstituteVariablesNumericEntitySupplementary() throws Exception {
        // A numeric character reference outside the BMP (U+1F600 = surrogate pair \uD83D\uDE00) must be decoded
        SSIMediator mediator = new SSIMediator(new TesterSSIExternalResolver(), LAST_MODIFIED);
        Assert.assertEquals("\uD83D\uDE00", mediator.substituteVariables("&#128512;"));
    }

    /**
     * Minimal implementation that provides the bare essentials require for the unit tests.
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
