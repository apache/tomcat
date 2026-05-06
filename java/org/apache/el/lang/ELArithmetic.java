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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import javax.el.ELException;

import org.apache.el.util.MessageFactory;


/**
 * A helper class of Arithmetic defined by the EL Specification.
 */
public abstract class ELArithmetic {

    /**
     * Arithmetic delegate for BigDecimal operations.
     */
    public static final class BigDecimalDelegate extends ELArithmetic {
        /**
         * Construct a new BigDecimalDelegate.
         */
        public BigDecimalDelegate() {
            super();
        }

        @Override
        protected Number add(Number num0, Number num1) {
            return ((BigDecimal) num0).add((BigDecimal) num1);
        }

        @Override
        protected Number coerce(Number num) {
            if (num instanceof BigDecimal) {
                return num;
            }
            if (num instanceof BigInteger) {
                return new BigDecimal((BigInteger) num);
            }
            return new BigDecimal(num.doubleValue());
        }

        @Override
        protected Number coerce(String str) {
            return new BigDecimal(str);
        }

        @Override
        protected Number divide(Number num0, Number num1) {
            return ((BigDecimal) num0).divide((BigDecimal) num1, RoundingMode.HALF_UP);
        }

        @Override
        protected Number subtract(Number num0, Number num1) {
            return ((BigDecimal) num0).subtract((BigDecimal) num1);
        }

        @Override
        protected Number mod(Number num0, Number num1) {
            return Double.valueOf(num0.doubleValue() % num1.doubleValue());
        }

        @Override
        protected Number multiply(Number num0, Number num1) {
            return ((BigDecimal) num0).multiply((BigDecimal) num1);
        }

        @Override
        public boolean matches(Object obj0, Object obj1) {
            return (obj0 instanceof BigDecimal || obj1 instanceof BigDecimal);
        }
    }

    /**
     * Arithmetic delegate for BigInteger operations.
     */
    public static final class BigIntegerDelegate extends ELArithmetic {
        /**
         * Construct a new BigIntegerDelegate.
         */
        public BigIntegerDelegate() {
            super();
        }

        @Override
        protected Number add(Number num0, Number num1) {
            return ((BigInteger) num0).add((BigInteger) num1);
        }

        @Override
        protected Number coerce(Number num) {
            if (num instanceof BigInteger) {
                return num;
            }
            return new BigInteger(num.toString());
        }

        @Override
        protected Number coerce(String str) {
            return new BigInteger(str);
        }

        @Override
        protected Number divide(Number num0, Number num1) {
            return (new BigDecimal((BigInteger) num0)).divide(new BigDecimal((BigInteger) num1), RoundingMode.HALF_UP);
        }

        @Override
        protected Number multiply(Number num0, Number num1) {
            return ((BigInteger) num0).multiply((BigInteger) num1);
        }

        @Override
        protected Number mod(Number num0, Number num1) {
            return ((BigInteger) num0).remainder((BigInteger) num1);
        }

        @Override
        protected Number subtract(Number num0, Number num1) {
            return ((BigInteger) num0).subtract((BigInteger) num1);
        }

        @Override
        public boolean matches(Object obj0, Object obj1) {
            return (obj0 instanceof BigInteger || obj1 instanceof BigInteger);
        }
    }

    /**
     * Arithmetic delegate for double/float operations.
     */
    public static final class DoubleDelegate extends ELArithmetic {
        /**
         * Construct a new DoubleDelegate.
         */
        public DoubleDelegate() {
            super();
        }

        @Override
        protected Number add(Number num0, Number num1) {
            // could only be one of these
            if (num0 instanceof BigDecimal) {
                return ((BigDecimal) num0).add(new BigDecimal(num1.doubleValue()));
            } else if (num1 instanceof BigDecimal) {
                return ((new BigDecimal(num0.doubleValue()).add((BigDecimal) num1)));
            }
            return Double.valueOf(num0.doubleValue() + num1.doubleValue());
        }

        @Override
        protected Number coerce(Number num) {
            if (num instanceof Double) {
                return num;
            }
            if (num instanceof BigInteger) {
                return new BigDecimal((BigInteger) num);
            }
            return Double.valueOf(num.doubleValue());
        }

        @Override
        protected Number coerce(String str) {
            return Double.valueOf(str);
        }

        @Override
        protected Number divide(Number num0, Number num1) {
            return Double.valueOf(num0.doubleValue() / num1.doubleValue());
        }

        @Override
        protected Number mod(Number num0, Number num1) {
            return Double.valueOf(num0.doubleValue() % num1.doubleValue());
        }

        @Override
        protected Number subtract(Number num0, Number num1) {
            // could only be one of these
            if (num0 instanceof BigDecimal) {
                return ((BigDecimal) num0).subtract(new BigDecimal(num1.doubleValue()));
            } else if (num1 instanceof BigDecimal) {
                return ((new BigDecimal(num0.doubleValue()).subtract((BigDecimal) num1)));
            }
            return Double.valueOf(num0.doubleValue() - num1.doubleValue());
        }

        @Override
        protected Number multiply(Number num0, Number num1) {
            // could only be one of these
            if (num0 instanceof BigDecimal) {
                return ((BigDecimal) num0).multiply(new BigDecimal(num1.doubleValue()));
            } else if (num1 instanceof BigDecimal) {
                return ((new BigDecimal(num0.doubleValue()).multiply((BigDecimal) num1)));
            }
            return Double.valueOf(num0.doubleValue() * num1.doubleValue());
        }

        @Override
        public boolean matches(Object obj0, Object obj1) {
            return (obj0 instanceof Double || obj1 instanceof Double || obj0 instanceof Float ||
                    obj1 instanceof Float || (obj0 instanceof String && ELSupport.isStringFloat((String) obj0)) ||
                    (obj1 instanceof String && ELSupport.isStringFloat((String) obj1)));
        }
    }

    /**
     * Arithmetic delegate for long/integer operations.
     */
    public static final class LongDelegate extends ELArithmetic {
        /**
         * Construct a new LongDelegate.
         */
        public LongDelegate() {
            super();
        }

        @Override
        protected Number add(Number num0, Number num1) {
            return Long.valueOf(num0.longValue() + num1.longValue());
        }

        @Override
        protected Number coerce(Number num) {
            if (num instanceof Long) {
                return num;
            }
            return Long.valueOf(num.longValue());
        }

        @Override
        protected Number coerce(String str) {
            return Long.valueOf(str);
        }

        @Override
        protected Number divide(Number num0, Number num1) {
            return Long.valueOf(num0.longValue() / num1.longValue());
        }

        @Override
        protected Number mod(Number num0, Number num1) {
            return Long.valueOf(num0.longValue() % num1.longValue());
        }

        @Override
        protected Number subtract(Number num0, Number num1) {
            return Long.valueOf(num0.longValue() - num1.longValue());
        }

        @Override
        protected Number multiply(Number num0, Number num1) {
            return Long.valueOf(num0.longValue() * num1.longValue());
        }

        @Override
        public boolean matches(Object obj0, Object obj1) {
            return (obj0 instanceof Long || obj1 instanceof Long);
        }
    }

    /**
     * BigDecimal arithmetic delegate instance.
     */
    public static final BigDecimalDelegate BIGDECIMAL = new BigDecimalDelegate();

    /**
     * BigInteger arithmetic delegate instance.
     */
    public static final BigIntegerDelegate BIGINTEGER = new BigIntegerDelegate();

    /**
     * Double arithmetic delegate instance.
     */
    public static final DoubleDelegate DOUBLE = new DoubleDelegate();

    /**
     * Long arithmetic delegate instance.
     */
    public static final LongDelegate LONG = new LongDelegate();

    private static final Long ZERO = Long.valueOf(0);

    /**
     * Add two objects, coercing them to the appropriate numeric type.
     *
     * @param obj0 The first operand
     * @param obj1 The second operand
     * @return The result of the addition
     */
    public static Number add(final Object obj0, final Object obj1) {
        final ELArithmetic delegate = findDelegate(obj0, obj1);
        if (delegate == null) {
            return Long.valueOf(0);
        }

        Number num0 = delegate.coerce(obj0);
        Number num1 = delegate.coerce(obj1);

        return delegate.add(num0, num1);
    }

    /**
     * Compute the modulo of two objects, coercing them to the appropriate numeric type.
     *
     * @param obj0 The dividend
     * @param obj1 The divisor
     * @return The result of the modulo operation
     */
    public static Number mod(final Object obj0, final Object obj1) {
        if (obj0 == null && obj1 == null) {
            return Long.valueOf(0);
        }

        final ELArithmetic delegate;
        if (BIGDECIMAL.matches(obj0, obj1)) {
            delegate = DOUBLE;
        } else if (DOUBLE.matches(obj0, obj1)) {
            delegate = DOUBLE;
        } else if (BIGINTEGER.matches(obj0, obj1)) {
            delegate = BIGINTEGER;
        } else {
            delegate = LONG;
        }

        Number num0 = delegate.coerce(obj0);
        Number num1 = delegate.coerce(obj1);

        return delegate.mod(num0, num1);
    }

    /**
     * Subtract two objects, coercing them to the appropriate numeric type.
     *
     * @param obj0 The minuend
     * @param obj1 The subtrahend
     * @return The result of the subtraction
     */
    public static Number subtract(final Object obj0, final Object obj1) {
        final ELArithmetic delegate = findDelegate(obj0, obj1);
        if (delegate == null) {
            return Long.valueOf(0);
        }

        Number num0 = delegate.coerce(obj0);
        Number num1 = delegate.coerce(obj1);

        return delegate.subtract(num0, num1);
    }

    /**
     * Divide two objects, coercing them to the appropriate numeric type.
     *
     * @param obj0 The dividend
     * @param obj1 The divisor
     * @return The result of the division
     */
    public static Number divide(final Object obj0, final Object obj1) {
        if (obj0 == null && obj1 == null) {
            return ZERO;
        }

        final ELArithmetic delegate;
        if (BIGDECIMAL.matches(obj0, obj1)) {
            delegate = BIGDECIMAL;
        } else if (BIGINTEGER.matches(obj0, obj1)) {
            delegate = BIGDECIMAL;
        } else {
            delegate = DOUBLE;
        }

        Number num0 = delegate.coerce(obj0);
        Number num1 = delegate.coerce(obj1);

        return delegate.divide(num0, num1);
    }

    /**
     * Multiply two objects, coercing them to the appropriate numeric type.
     *
     * @param obj0 The first factor
     * @param obj1 The second factor
     * @return The result of the multiplication
     */
    public static Number multiply(final Object obj0, final Object obj1) {
        final ELArithmetic delegate = findDelegate(obj0, obj1);
        if (delegate == null) {
            return Long.valueOf(0);
        }

        Number num0 = delegate.coerce(obj0);
        Number num1 = delegate.coerce(obj1);

        return delegate.multiply(num0, num1);
    }

    private static ELArithmetic findDelegate(final Object obj0, final Object obj1) {
        if (obj0 == null && obj1 == null) {
            return null;
        }

        if (BIGDECIMAL.matches(obj0, obj1)) {
            return BIGDECIMAL;
        } else if (DOUBLE.matches(obj0, obj1)) {
            if (BIGINTEGER.matches(obj0, obj1)) {
                return BIGDECIMAL;
            } else {
                return DOUBLE;
            }
        } else if (BIGINTEGER.matches(obj0, obj1)) {
            return BIGINTEGER;
        } else {
            return LONG;
        }
    }

    /**
     * Check if the given object is a number.
     *
     * @param obj The object to check
     * @return true if the object is a number
     */
    public static boolean isNumber(final Object obj) {
        return (obj != null && isNumberType(obj.getClass()));
    }

    /**
     * Check if the given class is a number type.
     *
     * @param type The class to check
     * @return true if the class is a number type
     */
    public static boolean isNumberType(final Class<?> type) {
        return type == Long.TYPE || type == Double.TYPE || type == Byte.TYPE || type == Short.TYPE ||
                type == Integer.TYPE || type == Float.TYPE || Number.class.isAssignableFrom(type);
    }

    /**
     * Protected constructor for subclasses.
     */
    protected ELArithmetic() {
        super();
    }

    /**
     * Add two numbers.
     *
     * @param num0 The first number
     * @param num1 The second number
     * @return The sum
     */
    protected abstract Number add(Number num0, Number num1);

    /**
     * Multiply two numbers.
     *
     * @param num0 The first number
     * @param num1 The second number
     * @return The product
     */
    protected abstract Number multiply(Number num0, Number num1);

    /**
     * Subtract two numbers.
     *
     * @param num0 The minuend
     * @param num1 The subtrahend
     * @return The difference
     */
    protected abstract Number subtract(Number num0, Number num1);

    /**
     * Compute the modulo of two numbers.
     *
     * @param num0 The dividend
     * @param num1 The divisor
     * @return The remainder
     */
    protected abstract Number mod(Number num0, Number num1);

    /**
     * Coerce a number to the delegate's preferred type.
     *
     * @param num The number to coerce
     * @return The coerced number
     */
    protected abstract Number coerce(Number num);

    /**
     * Coerce an object to a number.
     *
     * @param obj The object to coerce
     * @return The coerced number
     */
    protected final Number coerce(final Object obj) {

        if (isNumber(obj)) {
            return coerce((Number) obj);
        }
        if (obj == null || "".equals(obj)) {
            return coerce(ZERO);
        }
        if (obj instanceof String) {
            return coerce((String) obj);
        }
        if (obj instanceof Character) {
            return coerce(Short.valueOf((short) ((Character) obj).charValue()));
        }

        throw new ELException(MessageFactory.get("error.convert", obj, obj.getClass(), "Number"));
    }

    /**
     * Coerce a string to a number.
     *
     * @param str The string to coerce
     * @return The coerced number
     */
    protected abstract Number coerce(String str);

    /**
     * Divide two numbers.
     *
     * @param num0 The dividend
     * @param num1 The divisor
     * @return The quotient
     */
    protected abstract Number divide(Number num0, Number num1);

    /**
     * Check if this delegate matches the given operand types.
     *
     * @param obj0 The first operand
     * @param obj1 The second operand
     * @return true if this delegate should handle these types
     */
    protected abstract boolean matches(Object obj0, Object obj1);
}
