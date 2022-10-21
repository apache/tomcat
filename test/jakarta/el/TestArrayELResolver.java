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
package jakarta.el;

import org.junit.Assert;
import org.junit.Test;

public class TestArrayELResolver {

    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testGetType01() {
        ArrayELResolver resolver = new ArrayELResolver();
        resolver.getType(null, new Object(), new Object());
    }

    /**
     * Tests that a valid property is not resolved if base is not an array.
     */
    @Test
    public void testGetType02() {
        doNegativeTest(new Object(), new Object(), MethodUnderTest.GET_TYPE,
                true);
    }

    /**
     * Tests that a valid property is resolved.
     */
    @Test
    public void testGetType03() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        Class<?> result = resolver.getType(context, base, Integer.valueOf(0));

        Assert.assertEquals(base.getClass().getComponentType(), result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that the key is out of bounds and exception will be thrown.
     */
    @Test(expected = PropertyNotFoundException.class)
    public void testGetType04() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        resolver.getType(context, base, Integer.valueOf(1));
    }

    /**
     * Tests that a result is returned even when a coercion cannot be performed.
     */
    @Test
    public void testGetType05() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        Class<?> result = resolver.getType(context, base, "index");

        Assert.assertEquals(base.getClass().getComponentType(), result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that a null result is returned when the base is null.
     */
    @Test
    public void testGetType06() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        Class<?> result = resolver.getType(context, null, "index");

        Assert.assertNull(result);
        Assert.assertFalse(context.isPropertyResolved());
    }

    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testGetValue01() {
        ArrayELResolver resolver = new ArrayELResolver();
        resolver.getValue(null, new Object(), new Object());
    }

    /**
     * Tests that a valid property is not resolved if base is not an array.
     */
    @Test
    public void testGetValue02() {
        doNegativeTest(new Object(), new Object(), MethodUnderTest.GET_VALUE,
                true);
    }

    /**
     * Tests that a valid property is resolved.
     */
    @Test
    public void testGetValue03() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        Object result = resolver.getValue(context, base, Integer.valueOf(0));

        Assert.assertEquals("element", result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    @Test
    public void testGetValueCoercion01() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        Object result = resolver.getValue(context, base, Character.valueOf((char) 0));

        Assert.assertEquals("element", result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    @Test
    public void testGetValueCoercion02a() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        Object result = resolver.getValue(context, base, Boolean.FALSE);

        Assert.assertEquals("element", result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    @Test
    public void testGetValueCoercion02b() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        Object result = resolver.getValue(context, base, Boolean.TRUE);

        Assert.assertNull(result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    @Test
    public void testGetValueCoercion03() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        Object result = resolver.getValue(context, base, "0");

        Assert.assertEquals("element", result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetValueCoercion04() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        resolver.getValue(context, base, new Object());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetValueCoercion05() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        resolver.getValue(context, base, null);
    }

    /**
     * Tests a coercion cannot be performed as the key is not integer.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetValueCoercion06() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        resolver.getValue(context, base, "key");
    }

    /**
     * Tests that if the key is out of bounds null will be returned.
     */
    @Test
    public void testGetValue05() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        Object result = resolver.getValue(context, base, Integer.valueOf(1));

        Assert.assertNull(result);
        Assert.assertTrue(context.isPropertyResolved());

        result = resolver.getValue(context, base, Integer.valueOf(-1));

        Assert.assertNull(result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testSetValue01() {
        ArrayELResolver resolver = new ArrayELResolver();
        resolver.setValue(null, new Object(), new Object(), new Object());
    }

    /**
     * Tests that a valid property is not set if base is not an array.
     */
    @Test
    public void testSetValue02() {
        doNegativeTest(new Object(), new Object(), MethodUnderTest.SET_VALUE,
                false);
    }

    /**
     * Tests that an exception is thrown when readOnly is true.
     */
    @Test(expected = PropertyNotWritableException.class)
    public void testSetValue03() {
        ArrayELResolver resolver = new ArrayELResolver(true);
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        resolver.setValue(context, new String[] {}, new Object(), new Object());
    }

    /**
     * Tests that a valid property is set.
     */
    @Test
    public void testSetValue04() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        resolver.setValue(context, base, Integer.valueOf(0), "new-element");

        Assert.assertEquals("new-element",
                resolver.getValue(context, base, Integer.valueOf(0)));
        Assert.assertTrue(context.isPropertyResolved());

        resolver.setValue(context, base, Integer.valueOf(0), null);

        Assert.assertEquals(null,
                resolver.getValue(context, base, Integer.valueOf(0)));
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests a coercion cannot be performed as the key is not integer.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testSetValue05() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        resolver.setValue(context, base, "key", "new-element");
    }

    /**
     * Tests that the key is out of bounds and exception will be thrown.
     */
    @Test(expected = PropertyNotFoundException.class)
    public void testSetValue06() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        resolver.setValue(context, base, Integer.valueOf(1), "new-element");
    }

    /**
     * Tests that an exception will be thrown if the value is not from the
     * corresponding type.
     */
    @Test(expected = ClassCastException.class)
    public void testSetValue07() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        resolver.setValue(context, base, Integer.valueOf(0), Integer.valueOf(1));
    }

    /**
     * Tests setting arrays of primitives.
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=55691
     */
    @Test
    public void testSetValue08() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        int[] base = new int[] { 1, 2, 3 };
        resolver.setValue(context, base, Integer.valueOf(1), Integer.valueOf(4));

        Assert.assertEquals(Integer.valueOf(base[1]), Integer.valueOf(4));
    }

    /*
     * Null base should be a NO-OP rather than an exception
     */
    @Test
    public void testSetValue09() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        resolver.setValue(context, null, Integer.valueOf(1), Integer.valueOf(4));
        Assert.assertFalse(context.isPropertyResolved());
    }

    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testIsReadOnly01() {
        ArrayELResolver resolver = new ArrayELResolver();
        resolver.isReadOnly(null, new Object(), new Object());
    }

    /**
     * Tests that the propertyResolved is false if base is not an array.
     */
    @Test
    public void testIsReadOnly02() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        boolean result = resolver.isReadOnly(context, new Object(),
                new Object());

        Assert.assertFalse(result);
        Assert.assertFalse(context.isPropertyResolved());

        resolver = new ArrayELResolver(true);

        result = resolver.isReadOnly(context, new Object(), new Object());

        Assert.assertTrue(result);
        Assert.assertFalse(context.isPropertyResolved());
    }

    /**
     * Tests that if the ArrayELResolver is constructed with readOnly the method
     * will return always true, otherwise false.
     */
    @Test
    public void testIsReadOnly03() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        boolean result = resolver.isReadOnly(context, base, Integer.valueOf(0));

        Assert.assertFalse(result);
        Assert.assertTrue(context.isPropertyResolved());

        resolver = new ArrayELResolver(true);

        result = resolver.isReadOnly(context, base, Integer.valueOf(0));

        Assert.assertTrue(result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that the key is out of bounds and exception will be thrown.
     */
    @Test(expected = PropertyNotFoundException.class)
    public void testIsReadOnly04() {
        doTestIsReadOutOfBounds(1);
    }

    @Test(expected = PropertyNotFoundException.class)
    public void testIsReadOnly05() {
        doTestIsReadOutOfBounds(-1);
    }

    private void doTestIsReadOutOfBounds(int index) {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        resolver.isReadOnly(context, base, Integer.valueOf(index));
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that a result is returned even when a coercion cannot be performed.
     */
    @Test
    public void testIsReadOnly06() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        String[] base = new String[] { "element" };
        boolean result = resolver.isReadOnly(context, base, "key");

        Assert.assertFalse(result);
        Assert.assertTrue(context.isPropertyResolved());

        resolver = new ArrayELResolver(true);

        result = resolver.isReadOnly(context, base, "key");

        Assert.assertTrue(result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    @Test
    public void testIsReadOnly07() {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        boolean result = resolver.isReadOnly(context, null, null);

        Assert.assertFalse(result);
        Assert.assertFalse(context.isPropertyResolved());

        resolver = new ArrayELResolver(true);

        result = resolver.isReadOnly(context, null, null);

        Assert.assertTrue(result);
        Assert.assertFalse(context.isPropertyResolved());
    }

    private void doNegativeTest(Object base, Object trigger,
            MethodUnderTest method, boolean checkResult) {
        ArrayELResolver resolver = new ArrayELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        Object result = null;
        switch (method) {
        case GET_VALUE: {
            result = resolver.getValue(context, base, trigger);
            break;
        }
        case SET_VALUE: {
            resolver.setValue(context, base, trigger, new Object());
            break;
        }
        case GET_TYPE: {
            result = resolver.getType(context, base, trigger);
            break;
        }
        default: {
            // Should never happen
            Assert.fail("Missing case for method");
        }
        }

        if (checkResult) {
            Assert.assertNull(result);
        }
        Assert.assertFalse(context.isPropertyResolved());
    }

    private enum MethodUnderTest {
        GET_VALUE, SET_VALUE, GET_TYPE
    }


    @Test
    public void testGetCommonPropertyType01() {
        // null base, null response
        ArrayELResolver resolver = new ArrayELResolver();
        Assert.assertNull(resolver.getCommonPropertyType(null, null));
    }


    @Test
    public void testGetCommonPropertyType02() {
        // non-array base, null response
        ArrayELResolver resolver = new ArrayELResolver();
        Assert.assertNull(resolver.getCommonPropertyType(null, new Object()));
    }


    @Test
    public void testGetCommonPropertyType03() {
        // array base, Integer response
        ArrayELResolver resolver = new ArrayELResolver();
        Class<?> clazz = resolver.getCommonPropertyType(null, new Object[]{});
        Assert.assertNotNull(clazz);
        Assert.assertEquals(clazz, Integer.class);
    }
}
