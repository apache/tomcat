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
package org.apache.el.lang;

import java.beans.PropertyEditorManager;
import java.math.BigDecimal;
import java.math.BigInteger;

import javax.el.ELException;
import javax.el.ELManager;

import org.junit.Assert;
import org.junit.Test;

public class TestELSupport {
    @Test
    public void testEquals() {
        Assert.assertTrue(ELSupport.equals(null, "01", Long.valueOf(1)));
    }

    @Test
    public void testBigDecimal() {
        testIsSame(new BigDecimal("0.123456789012345678901234567890123456789012345678901234567890123456789"));
    }

    @Test
    public void testBigInteger() {
        testIsSame(new BigInteger("1234567890123456789012345678901234567890123456789012345678901234567890"));
    }

    @Test
    public void testLong() {
        testIsSame(Long.valueOf(0x0102030405060708L));
    }

    @Test
    public void testInteger() {
        testIsSame(Integer.valueOf(0x01020304));
    }

    @Test
    public void testShort() {
        testIsSame(Short.valueOf((short) 0x0102));
    }

    @Test
    public void testByte() {
        testIsSame(Byte.valueOf((byte) 0xEF));
    }

    @Test
    public void testDouble() {
        testIsSame(Double.valueOf(0.123456789012345678901234));
    }

    @Test
    public void testFloat() {
        testIsSame(Float.valueOf(0.123456F));
    }

    @Test
    public void testCoerceIntegerToNumber() {
        Integer input = Integer.valueOf(4390241);
        Object output = ELSupport.coerceToType(null, input, Number.class);
        Assert.assertEquals(input, output);
    }

    @Test
    public void testCoerceNullToNumber() {
        Object output = ELSupport.coerceToType(null, null, Number.class);
        Assert.assertNull(output);
    }

    @Test
    public void testCoerceEnumAToEnumA() {
        Object output = null;
        try {
            output = ELSupport.coerceToEnum(null, TestEnumA.VALA1, TestEnumA.class);
        } finally {
            Assert.assertEquals(TestEnumA.VALA1, output);
        }
    }

    @Test
    public void testCoerceEnumAToEnumB() {
        Object output = null;
        try {
            output = ELSupport.coerceToEnum(null, TestEnumA.VALA1, TestEnumB.class);
        } catch (ELException ele) {
            // Ignore
        }
        Assert.assertNull(output);
    }

    @Test
    public void testCoerceEnumAToEnumC() {
        Object output = null;
        try {
            output = ELSupport.coerceToEnum(null, TestEnumA.VALA1, TestEnumC.class);
        } catch (ELException ele) {
            // Ignore
        }
        Assert.assertNull(output);
    }

    @Test
    public void testCoerceToType01() {
        Object result = ELManager.getExpressionFactory().coerceToType(null, Integer.class);
        Assert.assertNull("Result: " + result, result);
    }

    @Test
    public void testCoerceToType02() {
        Object result = ELManager.getExpressionFactory().coerceToType(null, int.class);
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testCoerceToType03() {
        Object result = ELManager.getExpressionFactory().coerceToType(null, boolean.class);
        Assert.assertEquals(Boolean.valueOf(null), result);
    }

    @Test
    public void testCoerceToType04() {
        Object result = ELManager.getExpressionFactory().coerceToType(null, String.class);
        Assert.assertEquals("", result);
    }

    @Test
    public void testCoerceToType05() {
        Object result = ELManager.getExpressionFactory().coerceToType(null, Character.class);
        Assert.assertNull("Result: " + result, result);
    }

    @Test
    public void testCoerceToType06() {
        Object result = ELManager.getExpressionFactory().coerceToType("", Character.class);
        Assert.assertEquals(Character.valueOf((char) 0), result);
    }

    @Test
    public void testCoerceToType07() {
        Object result = ELManager.getExpressionFactory().coerceToType(null, char.class);
        Assert.assertEquals(Character.valueOf((char) 0), result);
    }

    @Test
    public void testCoerceToType08() {
        Object result = ELManager.getExpressionFactory().coerceToType("", char.class);
        Assert.assertEquals(Character.valueOf((char) 0), result);
    }

    @Test
    public void testCoerceToType09() {
        Object result = ELManager.getExpressionFactory().coerceToType(null, Boolean.class);
        Assert.assertNull("Result: " + result, result);
    }

    @Test
    public void testCoerceToType10() {
        Object result = ELManager.getExpressionFactory().coerceToType("", Boolean.class);
        Assert.assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void testCoerceToType11() {
        Object result = ELManager.getExpressionFactory().coerceToType(null, boolean.class);
        Assert.assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void testCoerceToType12() {
        Object result = ELManager.getExpressionFactory().coerceToType("", boolean.class);
        Assert.assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void testCoerceToType13() {
        Object result = ELManager.getExpressionFactory().coerceToType("", TesterType.class);
        Assert.assertNull(result);
    }

    @Test
    public void testCoerceToType14() {
        PropertyEditorManager.registerEditor(TesterType.class, TesterTypeEditorNoError.class);
        Object result = ELManager.getExpressionFactory().coerceToType("Foo", TesterType.class);
        Assert.assertEquals("Foo", ((TesterType) result).getValue());
    }

    @Test(expected = ELException.class)
    public void testCoerceToType15() {
        PropertyEditorManager.registerEditor(TesterType.class, TesterTypeEditorError.class);
        Object result = ELManager.getExpressionFactory().coerceToType("Foo", TesterType.class);
        Assert.assertEquals("Foo", ((TesterType) result).getValue());
    }

    @Test
    public void testCoerceToType16() {
        PropertyEditorManager.registerEditor(TesterType.class, TesterTypeEditorError.class);
        Object result = ELManager.getExpressionFactory().coerceToType("", TesterType.class);
        Assert.assertNull(result);
    }

    @Test
    public void testCoerceToNumber01() {
        Object result = ELSupport.coerceToNumber(null, null, Integer.class);
        Assert.assertNull("Result: " + result, result);
    }

    @Test
    public void testCoerceToNumber02() {
        Object result = ELSupport.coerceToNumber(null, null, int.class);
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testCoerceToBoolean01() {
        Object result = ELSupport.coerceToBoolean(null, null, true);
        Assert.assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void testCoerceToBoolean02() {
        Object result = ELSupport.coerceToBoolean(null, null, false);
        Assert.assertNull("Result: " + result, result);
    }

    @Test
    public void testCompareNonMutuallyComparable01() {
        // The ClassCastException raised by obj0's compareTo() must be wrapped
        try {
            ELSupport.compare(null, new TesterComparableA(), new TesterComparableB());
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            Assert.assertTrue("Unexpected cause: " + e.getCause(), e.getCause() instanceof ClassCastException);
        }
    }

    @Test
    public void testCompareNonMutuallyComparable02() {
        // obj0 is not Comparable, so obj1's compareTo() is used. The resulting
        // ClassCastException must be wrapped
        try {
            ELSupport.compare(null, new Object(), new TesterComparableB());
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            Assert.assertTrue("Unexpected cause: " + e.getCause(), e.getCause() instanceof ClassCastException);
        }
    }

    @Test
    public void testCompareBigDecimalWithNonFiniteThrowsELException() {
        // Coercing NaN to BigDecimal uses new BigDecimal(double) which rejects the
        // value. The failure must surface as ELException.
        try {
            ELSupport.compare(null, BigDecimal.ONE, Double.NaN);
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            Assert.assertTrue("Unexpected cause: " + e.getCause(), e.getCause() instanceof NumberFormatException);
        }
    }

    @Test
    public void testEqualsBigDecimalWithNonFiniteThrowsELException() {
        try {
            ELSupport.equals(null, BigDecimal.ONE, Double.NaN);
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            Assert.assertTrue("Unexpected cause: " + e.getCause(), e.getCause() instanceof NumberFormatException);
        }
    }

    @Test
    public void testCoerceToTypeNonFiniteToBigDecimalThrowsELException() {
        try {
            ELSupport.coerceToType(null, Double.POSITIVE_INFINITY, BigDecimal.class);
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            Assert.assertTrue("Unexpected cause: " + e.getCause(), e.getCause() instanceof NumberFormatException);
        }
    }

    @Test
    public void testCoerceToTypeExtremeBigDecimalToBigIntegerThrowsELException() {
        // BigDecimal.toBigInteger() declares ArithmeticException for overflow with
        // extreme exponents. The failure must surface as ELException.
        try {
            ELSupport.coerceToType(null, new BigDecimal("1e999999999"), BigInteger.class);
            Assert.fail("An ELException was expected");
        } catch (ELException e) {
            Assert.assertTrue("Unexpected cause: " + e.getCause(), e.getCause() instanceof ArithmeticException);
        }
    }

    private static class TesterComparableA implements Comparable<TesterComparableA> {
        @Override
        public int compareTo(TesterComparableA o) {
            return 0;
        }
    }

    private static class TesterComparableB implements Comparable<TesterComparableB> {
        @Override
        public int compareTo(TesterComparableB o) {
            return 0;
        }
    }

    private static void testIsSame(Object value) {
        Assert.assertEquals(value, ELSupport.coerceToNumber(null, value, value.getClass()));
    }

    private enum TestEnumA {
        VALA1,
        VALA2
    }

    private enum TestEnumB {
        VALB1,
        VALB2
    }

    private enum TestEnumC {
        VALA1,
        VALA2,
        VALB1,
        VALB2
    }
}
