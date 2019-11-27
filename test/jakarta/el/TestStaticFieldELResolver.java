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

public class TestStaticFieldELResolver {

    private static final String PROPERTY01_NAME = "publicStaticString";
    private static final String PROPERTY01_VALUE = "publicStaticStringNewValue";
    private static final String PROPERTY02_NAME = "nonExistingString";
    private static final String PROPERTY03_NAME = "publicString";
    private static final String PROPERTY04_NAME = "privateStaticString";
    private static final String PROPERTY05_NAME = "privateString";
    private static final String METHOD01_NAME = "<init>";
    private static final String METHOD02_NAME = "getPublicStaticString";
    private static final String METHOD03_NAME = "setPrivateString";
    private static final String METHOD04_NAME = "printPublicStaticString";

    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testGetValue01() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        resolver.getValue(null, new Object(), new Object());
    }

    /**
     * Tests that a valid property is resolved.
     */
    @Test
    public void testGetValue02() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        Object result = resolver.getValue(context, new ELClass(
                TesterClass.class), PROPERTY01_NAME);

        Assert.assertEquals(PROPERTY01_NAME, result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that a valid property is not resolved if base is not ELCLass.
     */
    @Test
    public void testGetValue03() {
        doNegativeTest(new Object(), PROPERTY01_NAME, MethodUnderTest.GET_VALUE);
    }

    /**
     * Tests that non String property is not resolved.
     */
    @Test
    public void testGetValue04() {
        doNegativeTest(new ELClass(TesterClass.class), new Object(),
                MethodUnderTest.GET_VALUE);
    }

    /**
     * Property doesn't exist
     */
    @Test
    public void testGetValue05() {
        doThrowableTest(PROPERTY02_NAME, MethodUnderTest.GET_VALUE, true);
    }

    /**
     * Property is not static
     */
    @Test
    public void testGetValue06() {
        doThrowableTest(PROPERTY03_NAME, MethodUnderTest.GET_VALUE, false);
    }

    /**
     * Property is not public
     */
    @Test
    public void testGetValue07() {
        doThrowableTest(PROPERTY04_NAME, MethodUnderTest.GET_VALUE, true);
    }

    /**
     * Property is neither public nor static
     */
    @Test
    public void testGetValue08() {
        doThrowableTest(PROPERTY05_NAME, MethodUnderTest.GET_VALUE, true);
    }

    /**
     * Tests that a valid property of Enum is resolved.
     */
    @Test
    public void testGetValue09() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        Object result = resolver.getValue(context, new ELClass(
                MethodUnderTest.class), MethodUnderTest.GET_TYPE.toString());

        Assert.assertEquals(MethodUnderTest.GET_TYPE, result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testSetValue01() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        resolver.setValue(null, new Object(), new Object(), new Object());
    }

    /**
     * Tests that cannot write to a static field.
     */
    @Test(expected = PropertyNotWritableException.class)
    public void testSetValue02() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        resolver.setValue(context, new ELClass(TesterClass.class),
                PROPERTY01_NAME, PROPERTY01_VALUE);
    }

    /**
     * Tests that the operation is not invoked if base is not ELCLass.
     */
    @Test
    public void testSetValue03() {
        doNegativeTest(new Object(), PROPERTY01_NAME, MethodUnderTest.SET_VALUE);
    }

    /**
     * Tests that the operation is no invoked when the property is not String.
     */
    @Test
    public void testSetValue04() {
        doNegativeTest(new ELClass(TesterClass.class), new Object(),
                MethodUnderTest.SET_VALUE);
    }

    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testIsReadOnly01() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        resolver.isReadOnly(null, new Object(), new Object());
    }

    /**
     * Tests that the propertyResolved is true when base is ELCLass and the
     * property is String.
     */
    @Test
    public void testIsReadOnly02() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        boolean result = resolver.isReadOnly(context, new ELClass(
                TesterClass.class), PROPERTY01_NAME);

        Assert.assertTrue(result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that the propertyResolved is false if base is not ELCLass.
     */
    @Test
    public void testIsReadOnly03() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        boolean result = resolver.isReadOnly(context, new Object(),
                PROPERTY01_NAME);

        Assert.assertTrue(result);
        Assert.assertFalse(context.isPropertyResolved());
    }

    /**
     * Tests that the propertyResolved is false when the property is not String.
     */
    @Test
    public void testIsReadOnly04() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        boolean result = resolver.isReadOnly(context, new ELClass(
                TesterClass.class), new Object());

        Assert.assertTrue(result);
        Assert.assertFalse(context.isPropertyResolved());
    }

    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testGetType01() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        resolver.getType(null, new Object(), new Object());
    }

    /**
     * Tests that a valid property is resolved.
     */
    @Test
    public void testGetType02() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        Class<?> result = resolver.getType(context, new ELClass(
                TesterClass.class), PROPERTY01_NAME);

        Assert.assertEquals(PROPERTY01_NAME.getClass(), result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that a valid property is not resolved if base is not ELCLass.
     */
    @Test
    public void testGetType03() {
        doNegativeTest(new Object(), PROPERTY01_NAME, MethodUnderTest.GET_TYPE);
    }

    /**
     * Tests that non String property is not resolved.
     */
    @Test
    public void testGetType04() {
        doNegativeTest(new ELClass(TesterClass.class), new Object(),
                MethodUnderTest.GET_TYPE);
    }

    /**
     * Property doesn't exist
     */
    @Test
    public void testGetType05() {
        doThrowableTest(PROPERTY02_NAME, MethodUnderTest.GET_TYPE, true);
    }

    /**
     * Property is not static
     */
    @Test
    public void testGetType06() {
        doThrowableTest(PROPERTY03_NAME, MethodUnderTest.GET_TYPE, false);
    }

    /**
     * Property is not public
     */
    @Test
    public void testGetType07() {
        doThrowableTest(PROPERTY04_NAME, MethodUnderTest.GET_TYPE, true);
    }

    /**
     * Property is neither public nor static
     */
    @Test
    public void testGetType08() {
        doThrowableTest(PROPERTY05_NAME, MethodUnderTest.GET_TYPE, true);
    }

    /**
     * Tests that a valid property of Enum is resolved.
     */
    @Test
    public void testGetType09() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        Class<?> result = resolver.getType(context, new ELClass(
                MethodUnderTest.class), MethodUnderTest.GET_TYPE.toString());

        Assert.assertEquals(MethodUnderTest.GET_TYPE.getClass(), result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that a null context results in an NPE as per EL Javadoc.
     */
    @Test(expected = NullPointerException.class)
    public void testInvoke01() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        resolver.invoke(null, new Object(), new Object(), new Class<?>[] {},
                new Object[] {});
    }

    /**
     * Tests a constructor invocation.
     */
    @Test
    public void testInvoke02() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        Object result = resolver.invoke(context,
                new ELClass(TesterClass.class), METHOD01_NAME, null, null);

        Assert.assertEquals(TesterClass.class, result.getClass());
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests a method invocation.
     */
    @Test
    public void testInvoke03() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        Object result = resolver.invoke(context,
                new ELClass(TesterClass.class), METHOD02_NAME,
                new Class<?>[] {}, new Object[] {});

        Assert.assertEquals(PROPERTY01_NAME, result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    /**
     * Tests that a valid method is not resolved if base is not ELCLass.
     */
    @Test
    public void testInvoke04() {
        doNegativeTest(new Object(), METHOD02_NAME, MethodUnderTest.INVOKE);
    }

    /**
     * Tests that non String method name is not resolved.
     */
    @Test
    public void testInvoke05() {
        doNegativeTest(new ELClass(TesterClass.class), new Object(),
                MethodUnderTest.INVOKE);
    }

    /**
     * Tests that a private constructor invocation will fail.
     */
    @Test
    public void testInvoke06() {
        doThrowableTest(METHOD01_NAME, MethodUnderTest.INVOKE, false);
    }

    /**
     * Tests that a non static method invocation will fail.
     */
    @Test
    public void testInvoke07() {
        doThrowableTest(METHOD03_NAME, MethodUnderTest.INVOKE, false);
    }

    /**
     * Tests a void method invocation.
     */
    @Test
    public void testInvoke08() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        Object result = resolver.invoke(context,
                new ELClass(TesterClass.class), METHOD04_NAME,
                new Class<?>[] {}, new Object[] {});

        Assert.assertNull(result);
        Assert.assertTrue(context.isPropertyResolved());
    }

    private void doNegativeTest(Object elClass, Object trigger,
            MethodUnderTest method) {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        Object result = null;
        switch (method) {
        case GET_VALUE: {
            result = resolver.getValue(context, elClass, trigger);
            break;
        }
        case SET_VALUE: {
            resolver.setValue(context, elClass, trigger, PROPERTY01_VALUE);
            result = resolver.getValue(context, elClass, trigger);
            break;
        }
        case GET_TYPE: {
            result = resolver.getType(context, elClass, trigger);
            break;
        }
        case INVOKE: {
            result = resolver.invoke(context, elClass, trigger,
                    new Class<?>[] { String.class }, new Object[] { "test" });
            break;
        }
        default: {
            // Should never happen
            Assert.fail("Missing case for method");
        }
        }

        Assert.assertNull(result);
        Assert.assertFalse(context.isPropertyResolved());
    }

    private void doThrowableTest(String trigger, MethodUnderTest method,
            boolean checkCause) {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        ELContext context = new StandardELContext(
                ELManager.getExpressionFactory());

        ELException exception = null;
        try {
            switch (method) {
            case GET_VALUE: {
                resolver.getValue(context, new ELClass(TesterClass.class),
                        trigger);
                break;
            }
            case GET_TYPE: {
                resolver.getType(context, new ELClass(TesterClass.class),
                        trigger);
                break;
            }
            case INVOKE: {
                resolver.invoke(context, new ELClass(TesterClass.class),
                        trigger, new Class<?>[] { String.class },
                        new Object[] { "test" });
                break;
            }
            default: {
                // Should never happen
                Assert.fail("Missing case for method");
            }
            }

        } catch (PropertyNotFoundException | MethodNotFoundException e) {
            exception = e;
        }

        Assert.assertTrue(context.isPropertyResolved());
        Assert.assertNotNull(exception);

        if (checkCause) {
            // Can't be null due to assertion above
            Throwable cause = exception.getCause();
            Assert.assertNotNull(cause);
        }
    }

    private enum MethodUnderTest {
        GET_VALUE, SET_VALUE, GET_TYPE, INVOKE
    }
}
