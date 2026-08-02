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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Unit tests for {@link ExpressionTokenizer}. Tests all token types and
 * boundary conditions of the tokenizer that are not exercised by the
 * end-to-end tests in {@link TestExpressionParseTree}.
 */
@RunWith(Parameterized.class)
public class TestExpressionTokenizer {

    private final String expression;
    private final int[] expectedTokens;
    private final String[] expectedValues;


    @Parameters(name = "{index}: [{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        // Single operators. Note that TOKEN_END is not part of the expected
        // sequence: hasMoreTokens() returns false once the last real token
        // has been consumed and TOKEN_END is only returned by nextToken()
        // once hasMoreTokens() is false (matching the usage in
        // ExpressionParseTree).
        parameterSets.add(new Object[] { "(",
                new int[] { ExpressionTokenizer.TOKEN_LBRACE }, new String[] { null } });
        parameterSets.add(new Object[] { ")",
                new int[] { ExpressionTokenizer.TOKEN_RBRACE }, new String[] { null } });
        parameterSets.add(new Object[] { "=",
                new int[] { ExpressionTokenizer.TOKEN_EQ }, new String[] { null } });
        parameterSets.add(new Object[] { "!=",
                new int[] { ExpressionTokenizer.TOKEN_NOT_EQ }, new String[] { null } });
        parameterSets.add(new Object[] { "!",
                new int[] { ExpressionTokenizer.TOKEN_NOT }, new String[] { null } });
        parameterSets.add(new Object[] { "||",
                new int[] { ExpressionTokenizer.TOKEN_OR }, new String[] { null } });
        parameterSets.add(new Object[] { "&&",
                new int[] { ExpressionTokenizer.TOKEN_AND }, new String[] { null } });
        parameterSets.add(new Object[] { ">",
                new int[] { ExpressionTokenizer.TOKEN_GT }, new String[] { null } });
        parameterSets.add(new Object[] { ">=",
                new int[] { ExpressionTokenizer.TOKEN_GE }, new String[] { null } });
        parameterSets.add(new Object[] { "<",
                new int[] { ExpressionTokenizer.TOKEN_LT }, new String[] { null } });
        parameterSets.add(new Object[] { "<=",
                new int[] { ExpressionTokenizer.TOKEN_LE }, new String[] { null } });

        // String values
        parameterSets.add(new Object[] { "foo",
                new int[] { ExpressionTokenizer.TOKEN_STRING }, new String[] { "foo" } });
        parameterSets.add(new Object[] { "foo bar",
                new int[] { ExpressionTokenizer.TOKEN_STRING, ExpressionTokenizer.TOKEN_STRING },
                new String[] { "foo", "bar" } });
        parameterSets.add(new Object[] { "a = a",
                new int[] { ExpressionTokenizer.TOKEN_STRING, ExpressionTokenizer.TOKEN_EQ,
                        ExpressionTokenizer.TOKEN_STRING },
                new String[] { "a", null, "a" } });

        // Quoted strings (single and double quotes)
        parameterSets.add(new Object[] { "\"foo\"",
                new int[] { ExpressionTokenizer.TOKEN_STRING }, new String[] { "foo" } });
        parameterSets.add(new Object[] { "'foo'",
                new int[] { ExpressionTokenizer.TOKEN_STRING }, new String[] { "foo" } });
        parameterSets.add(new Object[] { "\"foo bar\"",
                new int[] { ExpressionTokenizer.TOKEN_STRING }, new String[] { "foo bar" } });
        // Escaped quote inside a quoted string
        parameterSets.add(new Object[] { "\"foo\\\"bar\"",
                new int[] { ExpressionTokenizer.TOKEN_STRING }, new String[] { "foo\\\"bar" } });

        // Regular expression strings
        parameterSets.add(new Object[] { "/a/",
                new int[] { ExpressionTokenizer.TOKEN_STRING }, new String[] { "/a/" } });
        parameterSets.add(new Object[] { "/a=b/",
                new int[] { ExpressionTokenizer.TOKEN_STRING }, new String[] { "/a=b/" } });
        // Escaped slash inside a regular expression
        parameterSets.add(new Object[] { "/a\\/b/",
                new int[] { ExpressionTokenizer.TOKEN_STRING }, new String[] { "/a\\/b/" } });

        // Operators mixed with strings
        parameterSets.add(new Object[] { "a && b",
                new int[] { ExpressionTokenizer.TOKEN_STRING, ExpressionTokenizer.TOKEN_AND,
                        ExpressionTokenizer.TOKEN_STRING },
                new String[] { "a", null, "b" } });
        parameterSets.add(new Object[] { "a || b",
                new int[] { ExpressionTokenizer.TOKEN_STRING, ExpressionTokenizer.TOKEN_OR,
                        ExpressionTokenizer.TOKEN_STRING },
                new String[] { "a", null, "b" } });
        parameterSets.add(new Object[] { "!a",
                new int[] { ExpressionTokenizer.TOKEN_NOT, ExpressionTokenizer.TOKEN_STRING },
                new String[] { null, "a" } });
        parameterSets.add(new Object[] { "a >= b",
                new int[] { ExpressionTokenizer.TOKEN_STRING, ExpressionTokenizer.TOKEN_GE,
                        ExpressionTokenizer.TOKEN_STRING },
                new String[] { "a", null, "b" } });
        parameterSets.add(new Object[] { "a <= b",
                new int[] { ExpressionTokenizer.TOKEN_STRING, ExpressionTokenizer.TOKEN_LE,
                        ExpressionTokenizer.TOKEN_STRING },
                new String[] { "a", null, "b" } });
        parameterSets.add(new Object[] { "(a)",
                new int[] { ExpressionTokenizer.TOKEN_LBRACE, ExpressionTokenizer.TOKEN_STRING,
                        ExpressionTokenizer.TOKEN_RBRACE },
                new String[] { null, "a", null } });

        // Edge cases: empty and whitespace-only expressions yield no tokens
        parameterSets.add(new Object[] { "", new int[] {}, new String[] {} });
        parameterSets.add(new Object[] { "   ", new int[] {}, new String[] {} });
        parameterSets.add(new Object[] { "\t\n", new int[] {}, new String[] {} });

        return parameterSets;
    }


    public TestExpressionTokenizer(String expression, int[] expectedTokens, String[] expectedValues) {
        this.expression = expression;
        this.expectedTokens = expectedTokens;
        this.expectedValues = expectedValues;
    }


    @Test
    public void testTokens() {
        ExpressionTokenizer tokenizer = new ExpressionTokenizer(expression);
        for (int i = 0; i < expectedTokens.length; i++) {
            Assert.assertTrue("Expected more tokens at position " + i + " for expression '" + expression + "'",
                    tokenizer.hasMoreTokens());
            Assert.assertEquals("Unexpected token type at position " + i + " for expression '" + expression + "'",
                    expectedTokens[i], tokenizer.nextToken());
            Assert.assertEquals("Unexpected token value at position " + i + " for expression '" + expression + "'",
                    expectedValues[i], tokenizer.getTokenValue());
        }
        Assert.assertFalse("Expected no more tokens for expression '" + expression + "'",
                tokenizer.hasMoreTokens());
    }
}
