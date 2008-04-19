package org.apache.el.lang;

import java.math.BigDecimal;
import java.math.BigInteger;

import junit.framework.TestCase;

public class TestELSupport extends TestCase {
    public void testBigDecimal() {
        testIsSame(new BigDecimal(
                "0.123456789012345678901234567890123456789012345678901234567890123456789"));
    }

    public void testBigInteger() {
        testIsSame(new BigInteger(
                "1234567890123456789012345678901234567890123456789012345678901234567890"));
    }

    public void testLong() {
        testIsSame(Long.valueOf(0x0102030405060708L));
    }

    public void testInteger() {
        testIsSame(Integer.valueOf(0x01020304));
    }

    public void testShort() {
        testIsSame(Short.valueOf((short) 0x0102));
    }

    public void testByte() {
        testIsSame(Byte.valueOf((byte) 0xEF));
    }

    public void testDouble() {
        testIsSame(Double.valueOf(0.123456789012345678901234));
    }

    public void testFloat() {
        testIsSame(Float.valueOf(0.123456F));
    }

    private static void testIsSame(Object value) {
        assertEquals(value, ELSupport.coerceToNumber(value, value.getClass()));
    }
}
