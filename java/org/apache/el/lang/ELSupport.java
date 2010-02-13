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

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.math.BigDecimal;
import java.math.BigInteger;

import javax.el.ELException;
import javax.el.PropertyNotFoundException;

import org.apache.el.util.MessageFactory;


/**
 * A helper class that implements the EL Specification
 * 
 * @author Jacob Hookom [jacob@hookom.net]
 * @version $Change: 181177 $$Date$$Author$
 */
public class ELSupport {

    private final static Long ZERO = new Long(0L);

    public final static void throwUnhandled(Object base, Object property)
            throws ELException {
        if (base == null) {
            throw new PropertyNotFoundException(MessageFactory.get(
                    "error.resolver.unhandled.null", property));
        } else {
            throw new PropertyNotFoundException(MessageFactory.get(
                    "error.resolver.unhandled", base.getClass(), property));
        }
    }

    /**
     * Compare two objects, after coercing to the same type if appropriate.
     * 
     * If the objects are identical, or they are equal according to 
     * {@link #equals(Object, Object)} then return 0.
     * 
     * If either object is a BigDecimal, then coerce both to BigDecimal first.
     * Similarly for Double(Float), BigInteger, and Long(Integer, Char, Short, Byte).
     *  
     * Otherwise, check that the first object is an instance of Comparable, and compare
     * against the second object. If that is null, return 1, otherwise
     * return the result of comparing against the second object.
     * 
     * Similarly, if the second object is Comparable, if the first is null, return -1,
     * else return the result of comparing against the first object.
     * 
     * A null object is considered as:
     * <ul>
     * <li>ZERO when compared with Numbers</li>
     * <li>the empty string for String compares</li>
     * <li>Otherwise null is considered to be lower than anything else.</li>
     * </ul>
     * 
     * @param obj0 first object
     * @param obj1 second object
     * @return -1, 0, or 1 if this object is less than, equal to, or greater than val.
     * @throws ELException if neither object is Comparable
     * @throws ClassCastException if the objects are not mutually comparable
     */
    public final static int compare(final Object obj0, final Object obj1)
            throws ELException {
        if (obj0 == obj1 || equals(obj0, obj1)) {
            return 0;
        }
        if (isBigDecimalOp(obj0, obj1)) {
            BigDecimal bd0 = (BigDecimal) coerceToNumber(obj0, BigDecimal.class);
            BigDecimal bd1 = (BigDecimal) coerceToNumber(obj1, BigDecimal.class);
            return bd0.compareTo(bd1);
        }
        if (isDoubleOp(obj0, obj1)) {
            Double d0 = (Double) coerceToNumber(obj0, Double.class);
            Double d1 = (Double) coerceToNumber(obj1, Double.class);
            return d0.compareTo(d1);
        }
        if (isBigIntegerOp(obj0, obj1)) {
            BigInteger bi0 = (BigInteger) coerceToNumber(obj0, BigInteger.class);
            BigInteger bi1 = (BigInteger) coerceToNumber(obj1, BigInteger.class);
            return bi0.compareTo(bi1);
        }
        if (isLongOp(obj0, obj1)) {
            Long l0 = (Long) coerceToNumber(obj0, Long.class);
            Long l1 = (Long) coerceToNumber(obj1, Long.class);
            return l0.compareTo(l1);
        }
        if (obj0 instanceof String || obj1 instanceof String) {
            return coerceToString(obj0).compareTo(coerceToString(obj1));
        }
        if (obj0 instanceof Comparable<?>) {
            @SuppressWarnings("unchecked") // checked above
            final Comparable<Object> comparable = (Comparable<Object>) obj0;
            return (obj1 != null) ? comparable.compareTo(obj1) : 1;
        }
        if (obj1 instanceof Comparable<?>) {
            @SuppressWarnings("unchecked") // checked above
            final Comparable<Object> comparable = (Comparable<Object>) obj1;
            return (obj0 != null) ? -comparable.compareTo(obj0) : -1;
        }
        throw new ELException(MessageFactory.get("error.compare", obj0, obj1));
    }

    /**
     * Compare two objects for equality, after coercing to the same type if appropriate.
     * 
     * If the objects are identical (including both null) return true.
     * If either object is null, return false.
     * If either object is Boolean, coerce both to Boolean and check equality.
     * Similarly for Enum, String, BigDecimal, Double(Float), Long(Integer, Short, Byte, Character)
     * Otherwise default to using Object.equals().
     * 
     * @param obj0 the first object
     * @param obj1 the second object
     * @return true if the objects are equal
     * @throws ELException
     */
    public final static boolean equals(final Object obj0, final Object obj1)
            throws ELException {
        if (obj0 == obj1) {
            return true;
        } else if (obj0 == null || obj1 == null) {
            return false;
        } else if (obj0 instanceof Boolean || obj1 instanceof Boolean) {
            return coerceToBoolean(obj0).equals(coerceToBoolean(obj1));
        } else if (obj0.getClass().isEnum()) {
            return obj0.equals(coerceToEnum(obj1, obj0.getClass()));
        } else if (obj1.getClass().isEnum()) {
            return obj1.equals(coerceToEnum(obj0, obj1.getClass()));
        } else if (obj0 instanceof String || obj1 instanceof String) {
            int lexCompare = coerceToString(obj0).compareTo(coerceToString(obj1));
            return (lexCompare == 0) ? true : false;
        }
        if (isBigDecimalOp(obj0, obj1)) {
            BigDecimal bd0 = (BigDecimal) coerceToNumber(obj0, BigDecimal.class);
            BigDecimal bd1 = (BigDecimal) coerceToNumber(obj1, BigDecimal.class);
            return bd0.equals(bd1);
        }
        if (isDoubleOp(obj0, obj1)) {
            Double d0 = (Double) coerceToNumber(obj0, Double.class);
            Double d1 = (Double) coerceToNumber(obj1, Double.class);
            return d0.equals(d1);
        }
        if (isBigIntegerOp(obj0, obj1)) {
            BigInteger bi0 = (BigInteger) coerceToNumber(obj0, BigInteger.class);
            BigInteger bi1 = (BigInteger) coerceToNumber(obj1, BigInteger.class);
            return bi0.equals(bi1);
        }
        if (isLongOp(obj0, obj1)) {
            Long l0 = (Long) coerceToNumber(obj0, Long.class);
            Long l1 = (Long) coerceToNumber(obj1, Long.class);
            return l0.equals(l1);
        } else {
            return obj0.equals(obj1);
        }
    }

    /**
     * @param obj
     * @param type
     * @return
     */
    public final static Enum<?> coerceToEnum(final Object obj, Class type) {
        if (obj == null || "".equals(obj)) {
            return null;
        }
        if (type.isAssignableFrom(obj.getClass())) {
            return (Enum<?>) obj;
        }
        
        if (!(obj instanceof String)) {
            throw new ELException(MessageFactory.get("error.convert",
                    obj, obj.getClass(), type));
        }

        Enum<?> result;
        try {
             result = Enum.valueOf(type, (String) obj);
        } catch (IllegalArgumentException iae) {
            throw new ELException(MessageFactory.get("error.convert",
                    obj, obj.getClass(), type));
        }
        return result;
    }

    /**
     * Convert an object to Boolean.
     * Null and empty string are false.
     * @param obj the object to convert
     * @return the Boolean value of the object
     * @throws ELException if object is not Boolean or String
     */
    public final static Boolean coerceToBoolean(final Object obj)
            throws ELException {
        if (obj == null || "".equals(obj)) {
            return Boolean.FALSE;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return Boolean.valueOf((String) obj);
        }

        throw new ELException(MessageFactory.get("error.convert",
                obj, obj.getClass(), Boolean.class));
    }

    public final static Character coerceToCharacter(final Object obj)
            throws ELException {
        if (obj == null || "".equals(obj)) {
            return new Character((char) 0);
        }
        if (obj instanceof String) {
            return new Character(((String) obj).charAt(0));
        }
        if (ELArithmetic.isNumber(obj)) {
            return new Character((char) ((Number) obj).shortValue());
        }
        Class<?> objType = obj.getClass();
        if (obj instanceof Character) {
            return (Character) obj;
        }

        throw new ELException(MessageFactory.get("error.convert",
                obj, objType, Character.class));
    }

    public final static Number coerceToNumber(final Object obj) {
        if (obj == null) {
            return ZERO;
        } else if (obj instanceof Number) {
            return (Number) obj;
        } else {
            String str = coerceToString(obj);
            if (isStringFloat(str)) {
                return toFloat(str);
            } else {
                return toNumber(str);
            }
        }
    }

    protected final static Number coerceToNumber(final Number number,
            final Class<?> type) throws ELException {
        if (Long.TYPE == type || Long.class.equals(type)) {
            return new Long(number.longValue());
        }
        if (Double.TYPE == type || Double.class.equals(type)) {
            return new Double(number.doubleValue());
        }
        if (Integer.TYPE == type || Integer.class.equals(type)) {
            return new Integer(number.intValue());
        }
        if (BigInteger.class.equals(type)) {
            if (number instanceof BigDecimal) {
                return ((BigDecimal) number).toBigInteger();
            }
            if (number instanceof BigInteger) {
                return number;
            }
            return BigInteger.valueOf(number.longValue());
        }
        if (BigDecimal.class.equals(type)) {
            if (number instanceof BigDecimal) {
                return number;
            }
            if (number instanceof BigInteger) {
                return new BigDecimal((BigInteger) number);
            }
            return new BigDecimal(number.doubleValue());
        }
        if (Byte.TYPE == type || Byte.class.equals(type)) {
            return new Byte(number.byteValue());
        }
        if (Short.TYPE == type || Short.class.equals(type)) {
            return new Short(number.shortValue());
        }
        if (Float.TYPE == type || Float.class.equals(type)) {
            return new Float(number.floatValue());
        }
        if (Number.class.equals(type)) {
            return number;
        }

        throw new ELException(MessageFactory.get("error.convert",
                number, number.getClass(), type));
    }

    public final static Number coerceToNumber(final Object obj,
            final Class<?> type) throws ELException {
        if (obj == null || "".equals(obj)) {
            return coerceToNumber(ZERO, type);
        }
        if (obj instanceof String) {
            return coerceToNumber((String) obj, type);
        }
        if (ELArithmetic.isNumber(obj)) {
            return coerceToNumber((Number) obj, type);
        }

        if (obj instanceof Character) {
            return coerceToNumber(new Short((short) ((Character) obj)
                    .charValue()), type);
        }

        throw new ELException(MessageFactory.get("error.convert",
                obj, obj.getClass(), type));
    }

    protected final static Number coerceToNumber(final String val,
            final Class<?> type) throws ELException {
        if (Long.TYPE == type || Long.class.equals(type)) {
            try {
                return Long.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (Integer.TYPE == type || Integer.class.equals(type)) {
            try {
                return Integer.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (Double.TYPE == type || Double.class.equals(type)) {
            try {
                return Double.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (BigInteger.class.equals(type)) {
            try {
                return new BigInteger(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (BigDecimal.class.equals(type)) {
            try {
                return new BigDecimal(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (Byte.TYPE == type || Byte.class.equals(type)) {
            try {
                return Byte.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (Short.TYPE == type || Short.class.equals(type)) {
            try {
                return Short.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }
        if (Float.TYPE == type || Float.class.equals(type)) {
            try {
                return Float.valueOf(val);
            } catch (NumberFormatException nfe) {
                throw new ELException(MessageFactory.get("error.convert",
                        val, String.class, type));
            }
        }

        throw new ELException(MessageFactory.get("error.convert",
                val, String.class, type));
    }

    /**
     * Coerce an object to a string
     * @param obj
     * @return the String value of the object
     */
    public final static String coerceToString(final Object obj) {
        if (obj == null) {
            return "";
        } else if (obj instanceof String) {
            return (String) obj;
        } else if (obj instanceof Enum<?>) {
            return ((Enum<?>) obj).name();
        } else {
            return obj.toString();
        }
    }

    public final static void checkType(final Object obj, final Class<?> type)
        throws ELException {
        if (String.class.equals(type)) {
            coerceToString(obj);
        }
        if (ELArithmetic.isNumberType(type)) {
            coerceToNumber(obj, type);
        }
        if (Character.class.equals(type) || Character.TYPE == type) {
            coerceToCharacter(obj);
        }
        if (Boolean.class.equals(type) || Boolean.TYPE == type) {
            coerceToBoolean(obj);
        }
        if (type.isEnum()) {
            coerceToEnum(obj, type);
        }
    }

    public final static Object coerceToType(final Object obj,
            final Class<?> type) throws ELException {
        if (type == null || Object.class.equals(type) ||
                (obj != null && type.isAssignableFrom(obj.getClass()))) {
            return obj;
        }
        if (String.class.equals(type)) {
            return coerceToString(obj);
        }
        if (ELArithmetic.isNumberType(type)) {
            return coerceToNumber(obj, type);
        }
        if (Character.class.equals(type) || Character.TYPE == type) {
            return coerceToCharacter(obj);
        }
        if (Boolean.class.equals(type) || Boolean.TYPE == type) {
            return coerceToBoolean(obj);
        }
        if (type.isEnum()) {
            return coerceToEnum(obj, type);
        }

        // new to spec
        if (obj == null)
            return null;
        if (obj instanceof String) {
            if ("".equals(obj))
                return null;
            PropertyEditor editor = PropertyEditorManager.findEditor(type);
            if (editor != null) {
                editor.setAsText((String) obj);
                return editor.getValue();
            }
        }
        throw new ELException(MessageFactory.get("error.convert",
                obj, obj.getClass(), type));
    }

    /**
     * Check if an array contains any {@code null} entries.
     * @param obj array to be checked
     * @return true if the array contains a {@code null}
     */
    public final static boolean containsNulls(final Object[] obj) {
        for (int i = 0; i < obj.length; i++) {
            if (obj[0] == null) {
                return true;
            }
        }
        return false;
    }

    public final static boolean isBigDecimalOp(final Object obj0,
            final Object obj1) {
        return (obj0 instanceof BigDecimal || obj1 instanceof BigDecimal);
    }

    public final static boolean isBigIntegerOp(final Object obj0,
            final Object obj1) {
        return (obj0 instanceof BigInteger || obj1 instanceof BigInteger);
    }

    public final static boolean isDoubleOp(final Object obj0, final Object obj1) {
        return (obj0 instanceof Double
                || obj1 instanceof Double
                || obj0 instanceof Float
                || obj1 instanceof Float);
    }

    public final static boolean isDoubleStringOp(final Object obj0,
            final Object obj1) {
        return (isDoubleOp(obj0, obj1)
                || (obj0 instanceof String && isStringFloat((String) obj0)) || (obj1 instanceof String && isStringFloat((String) obj1)));
    }

    public final static boolean isLongOp(final Object obj0, final Object obj1) {
        return (obj0 instanceof Long
                || obj1 instanceof Long
                || obj0 instanceof Integer
                || obj1 instanceof Integer
                || obj0 instanceof Character
                || obj1 instanceof Character
                || obj0 instanceof Short
                || obj1 instanceof Short
                || obj0 instanceof Byte
                || obj1 instanceof Byte);
    }

    public final static boolean isStringFloat(final String str) {
        int len = str.length();
        if (len > 1) {
            for (int i = 0; i < len; i++) {
                switch (str.charAt(i)) {
                case 'E':
                    return true;
                case 'e':
                    return true;
                case '.':
                    return true;
                }
            }
        }
        return false;
    }

    public final static Number toFloat(final String value) {
        try {
            if (Double.parseDouble(value) > Double.MAX_VALUE) {
                return new BigDecimal(value);
            } else {
                return new Double(value);
            }
        } catch (NumberFormatException e0) {
            return new BigDecimal(value);
        }
    }

    public final static Number toNumber(final String value) {
        try {
            return new Integer(Integer.parseInt(value));
        } catch (NumberFormatException e0) {
            try {
                return new Long(Long.parseLong(value));
            } catch (NumberFormatException e1) {
                return new BigInteger(value);
            }
        }
    }

    /**
     * 
     */
    public ELSupport() {
        super();
    }

}
