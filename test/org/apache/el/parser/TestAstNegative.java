/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.el.parser;

import jakarta.el.ELException;
import jakarta.el.ELProcessor;

import org.junit.Assert;
import org.junit.Test;

public class TestAstNegative {

    @Test
    public void testNull() {
        ELProcessor processor = new ELProcessor();
        Assert.assertEquals(Long.valueOf(0), processor.eval("-null"));
    }


    @Test
    public void testEmptyString() {
        // An empty string coerces to zero. This is consistent with the
        // coercion used by the binary operators, e.g. 5 - '' is 5.
        ELProcessor processor = new ELProcessor();
        Object result = processor.eval("-''");
        Assert.assertEquals(Long.class, result.getClass());
        Assert.assertEquals(Long.valueOf(0), result);
    }


    @Test
    public void testIntegerString() {
        ELProcessor processor = new ELProcessor();
        Assert.assertEquals(Long.valueOf(-12), processor.eval("-'12'"));
    }


    @Test
    public void testFloatingPointString() {
        ELProcessor processor = new ELProcessor();
        Object result = processor.eval("-'1.5'");
        Assert.assertEquals(Double.class, result.getClass());
        Assert.assertEquals(Double.valueOf(-1.5), result);
    }


    @Test
    public void testNonNumericString() {
        // A string that cannot be parsed as a number must produce an
        // ELException, not the raw NumberFormatException from parsing
        ELProcessor processor = new ELProcessor();
        try {
            processor.eval("-'abc'");
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            // Expected
            Assert.assertTrue("Unexpected cause: " + e.getCause(),
                    e.getCause() instanceof NumberFormatException);
        }
    }


    @Test
    public void testInvalidFloatingPointString() {
        ELProcessor processor = new ELProcessor();
        try {
            processor.eval("-'1.5.5'");
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            // Expected
        }
    }


    @Test
    public void testOverflowString() {
        // A string that is too large for a long cannot be negated as a long.
        // The unary minus of a string is specified in terms of
        // Long.parseLong()/Double.parseDouble(), so this is a conversion
        // failure rather than a change of result type.
        ELProcessor processor = new ELProcessor();
        try {
            processor.eval("-'99999999999999999999999999'");
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            // Expected
        }
    }


    @Test
    public void testNumericLiteral() {
        ELProcessor processor = new ELProcessor();
        Assert.assertEquals(Long.valueOf(-12), processor.eval("-12"));
        Assert.assertEquals(Double.valueOf(-1.5), processor.eval("-1.5"));
    }
}
