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
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import jakarta.el.ELException;
import jakarta.el.ELManager;
import jakarta.el.ELProcessor;

import org.junit.Assert;
import org.junit.Test;

public class TestELSupport {
    @Test
    public void testEquals() {
        Assert.assertTrue(ELSupport.equals(null, "01", Long.valueOf(1)));
    }

    @Test
    public void testBigDecimal() {
        testIsSame(new BigDecimal(
                "0.123456789012345678901234567890123456789012345678901234567890123456789"));
    }

    @Test
    public void testBigInteger() {
        testIsSame(new BigInteger(
                "1234567890123456789012345678901234567890123456789012345678901234567890"));
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
        Object result = ELManager.getExpressionFactory().coerceToType(
                null, Integer.class);
        Assert.assertNull("Result: " + result, result);
    }

    @Test
    public void testCoerceToType02() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                null, int.class);
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testCoerceToType03() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                null, boolean.class);
        Assert.assertEquals(Boolean.valueOf(null), result);
    }

    @Test
    public void testCoerceToType04() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                null, String.class);
        Assert.assertEquals("", result);
    }

    @Test
    public void testCoerceToType05() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                null, Character.class);
        Assert.assertNull("Result: " + result, result);
    }

    @Test
    public void testCoerceToType06() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                "", Character.class);
        Assert.assertEquals(Character.valueOf((char) 0), result);
    }

    @Test
    public void testCoerceToType07() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                null, char.class);
        Assert.assertEquals(Character.valueOf((char) 0), result);
    }

    @Test
    public void testCoerceToType08() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                "", char.class);
        Assert.assertEquals(Character.valueOf((char) 0), result);
    }

    @Test
    public void testCoerceToType09() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                null, Boolean.class);
        Assert.assertNull("Result: " + result, result);
    }

    @Test
    public void testCoerceToType10() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                "", Boolean.class);
        Assert.assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void testCoerceToType11() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                null, boolean.class);
        Assert.assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void testCoerceToType12() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                "", boolean.class);
        Assert.assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void testCoerceToType13() {
        Object result = ELManager.getExpressionFactory().coerceToType(
                "", TesterType.class);
        Assert.assertNull(result);
    }

    @Test
    public void testCoerceToType14() {
        PropertyEditorManager.registerEditor(TesterType.class, TesterTypeEditorNoError.class);
        Object result = ELManager.getExpressionFactory().coerceToType(
                "Foo", TesterType.class);
        Assert.assertEquals("Foo", ((TesterType) result).getValue());
    }

    @Test(expected=ELException.class)
    public void testCoerceToType15() {
        PropertyEditorManager.registerEditor(TesterType.class, TesterTypeEditorError.class);
        Object result = ELManager.getExpressionFactory().coerceToType(
                "Foo", TesterType.class);
        Assert.assertEquals("Foo", ((TesterType) result).getValue());
    }

    @Test
    public void testCoerceToType16() {
        PropertyEditorManager.registerEditor(TesterType.class, TesterTypeEditorError.class);
        Object result = ELManager.getExpressionFactory().coerceToType(
                "", TesterType.class);
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


    @Test
    public void testCoercetoFunctionalInterface01() throws Exception {
        final ELProcessor elp = new ELProcessor();
        elp.defineFunction("", "", "org.apache.el.lang.TestELSupport", "testPredicateA");
        Object result = elp.eval("testPredicateA(x -> x.equals('data'))");
        Assert.assertEquals("PASS", result);
    }


    @Test
    public void testCoercetoFunctionalInterface02() throws Exception {
        final ELProcessor elp = new ELProcessor();
        elp.defineFunction("", "", "org.apache.el.lang.TestELSupport", "testPredicateA");
        Object result = elp.eval("testPredicateA(x -> !x.equals('data'))");
        Assert.assertEquals("BLOCK", result);
    }


    @Test
    public void testCoercetoFunctionalInterface03() throws Exception {
        final ELProcessor elp = new ELProcessor();
        elp.defineFunction("", "", "org.apache.el.lang.TestELSupport", "testPredicateB");
        Object result = elp.eval("testPredicateB(x -> x > 50)");
        Assert.assertEquals("PASS", result);
    }


    @Test
    public void testCoercetoFunctionalInterface04() throws Exception {
        final ELProcessor elp = new ELProcessor();
        elp.defineFunction("", "", "org.apache.el.lang.TestELSupport", "testPredicateB");
        Object result = elp.eval("testPredicateB(x -> x < 50)");
        Assert.assertEquals("BLOCK", result);
    }


    @Test(expected = ELException.class)
    public void testCoercetoFunctionalInterface05() throws Exception {
        final ELProcessor elp = new ELProcessor();
        elp.defineFunction("", "", "org.apache.el.lang.TestELSupport", "testPredicateC");
        elp.eval("testPredicateC(x -> x > 50)");
    }


    @Test
    public void testCoercetoFunctionalInterface06() throws Exception {
        final ELProcessor elp = new ELProcessor();
        elp.defineFunction("", "", "org.apache.el.lang.TestELSupport", "testBiPredicateA");
        Object result = elp.eval("testBiPredicateA((x,y) -> x.name().equals('VALA1') && y)");
        Assert.assertEquals("PASS", result);
    }


    /*
     * Note: The following tests use compareTo(). When the target object (Long or String) is examined by reflection
     * both compareTo(Object) and compareTo(Long)/compareTo(String) methods will be found as potential matches. The
     * method matching rules (see section 1.2.1.2 of the specification) require that overload resolution has a higher
     * precedence than coercion resolution so it is always the compareTo(Object) method that will be used for the
     * following tests resulting in a ClassCastException (which is wrapped in an ELException).
     */

    @Test(expected = ELException.class)
    public void testCoercetoFunctionalInterface07() throws Exception {
        final ELProcessor elp = new ELProcessor();
        elp.defineFunction("", "", "org.apache.el.lang.TestELSupport", "testPredicateB");
        // This should trigger an exception as String isn't assignable to Long
        Object result = elp.eval("testPredicateB(x -> x.compareTo('data') == 0)");
        Assert.assertEquals("BLOCK", result);
    }


    @Test(expected = ELException.class)
    public void testCoercetoFunctionalInterface08() throws Exception {
        final ELProcessor elp = new ELProcessor();
        elp.defineFunction("", "", "org.apache.el.lang.TestELSupport", "testPredicateA");
        // This should trigger an exception as Long isn't assignable to String
        Object result = elp.eval("testPredicateA(x -> x.compareTo(1234) == 0)");
        Assert.assertEquals("BLOCK", result);
    }


    @Test(expected = ELException.class)
    public void testCoercetoFunctionalInterface09() throws Exception {
        final ELProcessor elp = new ELProcessor();
        elp.defineFunction("", "", "org.apache.el.lang.TestELSupport", "testPredicateB");
        // This should trigger an exception as String isn't assignable to Long despite this String being coercible
        Object result = elp.eval("testPredicateB(x -> x.compareTo('1234') == 0)");
        Assert.assertEquals("BLOCK", result);
    }


    public static String testPredicateA(Predicate<String> filter) {
        String s = "data";
        if (filter.test(s)) {
            return "PASS";
        } else {
            return "BLOCK";
        }
    }


    public static String testPredicateB(Predicate<Long> filter) {
        Long l = Long.valueOf(100);
        if (filter.test(l)) {
            return "PASS";
        } else {
            return "BLOCK";
        }
    }


    public static String testPredicateC(Predicate<String> filter) {
        String s = "text";
        if (filter.test(s)) {
            return "PASS";
        } else {
            return "BLOCK";
        }
    }


    public static String testBiPredicateA(BiPredicate<TestEnumC,Boolean> filter) {
        // Mainly interested in if these coerce correctly
        if (filter.test(TestEnumC.VALA1, Boolean.TRUE)) {
            return "PASS";
        } else {
            return "BLOCK";
        }
    }


    @Test
    public void testIsFunctionalInterface01() {
        Assert.assertTrue(ELSupport.isFunctionalInterface(Predicate.class));
    }


    @Test
    public void testIsFunctionalInterface02() {
        // Interface but more than one abstract method
        Assert.assertFalse(ELSupport.isFunctionalInterface(Map.class));
    }


    @Test
    public void testIsFunctionalInterface03() {
        // Not an interface
        Assert.assertFalse(ELSupport.isFunctionalInterface(String.class));
    }


    @Test
    public void testIsFunctionalInterface04() {
        // Extends a functional interface with no changes
        Assert.assertTrue(ELSupport.isFunctionalInterface(FunctionalA.class));
    }


    @Test
    public void testIsFunctionalInterface05() {
        // Extends a functional interface with additional abstract method
        Assert.assertFalse(ELSupport.isFunctionalInterface(FunctionalB.class));
    }


    @Test
    public void testIsFunctionalInterface06() {
        // Extends a functional interface with additional default method
        Assert.assertTrue(ELSupport.isFunctionalInterface(FunctionalC.class));
    }


    @Test
    public void testIsFunctionalInterface07() {
        // Extends a functional interface and overrides method in Object
        Assert.assertTrue(ELSupport.isFunctionalInterface(FunctionalD.class));
    }


    @Test
    public void testIsFunctionalInterface08() {
        // Extends a functional interface adds a method that looks like a
        // method from Object
        Assert.assertFalse(ELSupport.isFunctionalInterface(FunctionalE.class));
    }


    private interface FunctionalA<T> extends Predicate<T> {
    }


    private interface FunctionalB<T> extends Predicate<T> {
        void extra();
    }


    private interface FunctionalC<T> extends Predicate<T> {
        @SuppressWarnings("unused")
        default void extra() {
        }
    }


    private interface FunctionalD<T> extends Predicate<T> {
        @Override
        String toString();
        @Override
        int hashCode();
        @Override
        boolean equals(Object o);
    }


    private interface FunctionalE<T> extends Predicate<T> {
        boolean equals(String s);
    }
}
