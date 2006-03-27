/*
 * Copyright 2006 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
 * @version $Change: 181177 $$DateTime: 2001/06/26 08:45:09 $$Author: kchung $
 */
public class ELSupport {

    private final static ELSupport REF = new ELSupport();

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
     * @param obj0
     * @param obj1
     * @return
     * @throws EvaluationException
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
        if (obj0 instanceof Comparable) {
            return (obj1 != null) ? ((Comparable) obj0).compareTo(obj1) : 1;
        }
        if (obj1 instanceof Comparable) {
            return (obj0 != null) ? -((Comparable) obj1).compareTo(obj0) : -1;
        }
        throw new ELException(MessageFactory.get("error.compare", obj0, obj1));
    }

    /**
     * @param obj0
     * @param obj1
     * @return
     * @throws EvaluationException
     */
    public final static boolean equals(final Object obj0, final Object obj1)
            throws ELException {
        if (obj0 == obj1) {
            return true;
        } else if (obj0 == null || obj1 == null) {
            return false;
        } else if (obj0 instanceof Boolean || obj1 instanceof Boolean) {
            return coerceToBoolean(obj0).equals(coerceToBoolean(obj1));
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
     * @return
     */
    public final static Boolean coerceToBoolean(final Object obj)
            throws IllegalArgumentException {
        if (obj == null || "".equals(obj)) {
            return Boolean.FALSE;
        }
        if (obj instanceof Boolean || obj.getClass() == Boolean.TYPE) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return Boolean.valueOf((String) obj);
        }

        throw new IllegalArgumentException(MessageFactory.get("error.convert",
                obj, obj.getClass(), Boolean.class));
    }

    public final static Character coerceToCharacter(final Object obj)
            throws IllegalArgumentException {
        if (obj == null || "".equals(obj)) {
            return new Character((char) 0);
        }
        if (obj instanceof String) {
            return new Character(((String) obj).charAt(0));
        }
        if (ELArithmetic.isNumber(obj)) {
            return new Character((char) ((Number) obj).shortValue());
        }
        Class objType = obj.getClass();
        if (obj instanceof Character || objType == Character.TYPE) {
            return (Character) obj;
        }

        throw new IllegalArgumentException(MessageFactory.get("error.convert",
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
            final Class type) throws IllegalArgumentException {
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
            return BigInteger.valueOf(number.longValue());
        }
        if (BigDecimal.class.equals(type)) {
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

        throw new IllegalArgumentException(MessageFactory.get("error.convert",
                number, number.getClass(), type));
    }

    public final static Number coerceToNumber(final Object obj, final Class type)
            throws IllegalArgumentException {
        if (obj == null || "".equals(obj)) {
            return coerceToNumber(ZERO, type);
        }
        if (obj instanceof String) {
            return coerceToNumber((String) obj, type);
        }
        if (ELArithmetic.isNumber(obj)) {
            return coerceToNumber((Number) obj, type);
        }

        Class objType = obj.getClass();
        if (Character.class.equals(objType) || Character.TYPE == objType) {
            return coerceToNumber(new Short((short) ((Character) obj)
                    .charValue()), type);
        }

        throw new IllegalArgumentException(MessageFactory.get("error.convert",
                obj, objType, type));
    }

    protected final static Number coerceToNumber(final String val,
            final Class type) throws IllegalArgumentException {
        if (Long.TYPE == type || Long.class.equals(type)) {
            return Long.valueOf(val);
        }
        if (Integer.TYPE == type || Integer.class.equals(type)) {
            return Integer.valueOf(val);
        }
        if (Double.TYPE == type || Double.class.equals(type)) {
            return Double.valueOf(val);
        }
        if (BigInteger.class.equals(type)) {
            return new BigInteger(val);
        }
        if (BigDecimal.class.equals(type)) {
            return new BigDecimal(val);
        }
        if (Byte.TYPE == type || Byte.class.equals(type)) {
            return Byte.valueOf(val);
        }
        if (Short.TYPE == type || Short.class.equals(type)) {
            return Short.valueOf(val);
        }
        if (Float.TYPE == type || Float.class.equals(type)) {
            return Float.valueOf(val);
        }

        throw new IllegalArgumentException(MessageFactory.get("error.convert",
                val, String.class, type));
    }

    /**
     * @param obj
     * @return
     */
    public final static String coerceToString(final Object obj) {
        if (obj == null) {
            return "";
        } else if (obj instanceof String) {
            return (String) obj;
        } else {
            return obj.toString();
        }
    }

    public final static Object coerceToType(final Object obj, final Class type)
            throws IllegalArgumentException {
        if (type == null || Object.class.equals(type)) {
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
        if (obj != null && type.isAssignableFrom(obj.getClass())) {
            return obj;
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
        throw new IllegalArgumentException(MessageFactory.get("error.convert",
                obj, obj.getClass(), type));
    }

    /**
     * @param obj
     * @return
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
                || obj1 instanceof Float
                || (obj0 != null && (Double.TYPE == obj0.getClass() || Float.TYPE == obj0
                        .getClass())) || (obj1 != null && (Double.TYPE == obj1
                .getClass() || Float.TYPE == obj1.getClass())));
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
                || obj1 instanceof Byte
                || (obj0 != null && (Long.TYPE == obj0.getClass()
                        || Integer.TYPE == obj0.getClass()
                        || Character.TYPE == obj0.getClass()
                        || Short.TYPE == obj0.getClass() || Byte.TYPE == obj0
                        .getClass())) || (obj0 != null && (Long.TYPE == obj0
                .getClass()
                || Integer.TYPE == obj0.getClass()
                || Character.TYPE == obj0.getClass()
                || Short.TYPE == obj0.getClass() || Byte.TYPE == obj0
                .getClass())));
    }

    public final static boolean isStringFloat(final String str) {
        int len = str.length();
        if (len > 1) {
            char c = 0;
            for (int i = 0; i < len; i++) {
                switch (c = str.charAt(i)) {
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
