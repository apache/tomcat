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
package org.apache.jasper.optimizations;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.el.ELResolver;

import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.compiler.ELInterpreter;
import org.apache.jasper.compiler.JspUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * A non-specification compliant {@link ELInterpreter} that optimizes a subset
 * of setters for tag attributes.
 * <p>
 * The cases optimized by this implementation are:
 * <ul>
 * <li>expressions that are solely a literal boolean</li>
 * <li>expressions that are solely a constant string used (with coercion where
 *     necessary) with a setter that accepts:</li>
 * <li><ul>
 *     <li>boolean / Boolean</li>
 *     <li>char / Character</li>
 *     <li>BigDecimal</li>
 *     <li>long / Long</li>
 *     <li>int / Integer</li>
 *     <li>short / Short</li>
 *     <li>byte / Byte</li>
 *     <li>double / Double</li>
 *     <li>float / Float</li>
 *     <li>BigInteger</li>
 *     <li>Enum</li>
 *     <li>String</li>
 *     </ul></li>
 * </ul>
 * The specification compliance issue is that it essentially skips the first
 * three {@link ELResolver}s listed in section JSP.2.9 and effectively hard
 * codes the use of the 4th {@link ELResolver} in that list.
 *
 * @see "https://bz.apache.org/bugzilla/show_bug.cgi?id=64872"
 */
public class ELInterpreterTagSetters implements ELInterpreter {

    // Can't be static
    private final Log log = LogFactory.getLog(ELInterpreterTagSetters.class);

    private final Pattern PATTERN_BOOLEAN = Pattern.compile("[$][{]([\"']?)(true|false)\\1[}]");
    private final Pattern PATTERN_STRING_CONSTANT = Pattern.compile("[$][{]([\"'])(\\w+)\\1[}]");
    private final Pattern PATTERN_NUMERIC = Pattern.compile("[$][{]([\"'])([+-]?\\d+(\\.\\d+)?)\\1[}]");

    @Override
    public String interpreterCall(JspCompilationContext context,
            boolean isTagFile, String expression,
            Class<?> expectedType, String fnmapvar) {

        String result = null;

        // Boolean
        if (Boolean.TYPE == expectedType) {
            Matcher m = PATTERN_BOOLEAN.matcher(expression);
            if (m.matches()) {
                result = m.group(2);
            }
        } else if (Boolean.class == expectedType) {
            Matcher m = PATTERN_BOOLEAN.matcher(expression);
            if (m.matches()) {
                if ("true".equals(m.group(2))) {
                    result = "Boolean.TRUE";
                } else {
                    result = "Boolean.FALSE";
                }
            }
        // Character
        } else if (Character.TYPE == expectedType) {
            Matcher m = PATTERN_STRING_CONSTANT.matcher(expression);
            if (m.matches()) {
                return "\'" + m.group(2).charAt(0) + "\'";
            }
        } else if (Character.class == expectedType) {
            Matcher m = PATTERN_STRING_CONSTANT.matcher(expression);
            if (m.matches()) {
                return "Character.valueOf(\'" + m.group(2).charAt(0) + "\')";
            }
        // Numeric - BigDecimal
        } else if (BigDecimal.class == expectedType) {
            Matcher m = PATTERN_NUMERIC.matcher(expression);
            if (m.matches()) {
                try {
                    @SuppressWarnings("unused")
                    BigDecimal unused = new BigDecimal(m.group(2));
                    result = "new java.math.BigDecimal(\"" + m.group(2) + "\")";
                } catch (NumberFormatException e) {
                    log.debug("Failed to convert [" + m.group(2) + "] to BigDecimal", e);
                    // Continue and resolve the value at runtime
                }
            }
        // Numeric - long/Long
        } else if (Long.TYPE == expectedType || Long.class == expectedType) {
            Matcher m = PATTERN_NUMERIC.matcher(expression);
            if (m.matches()) {
                try {
                    @SuppressWarnings("unused")
                    Long unused = Long.valueOf(m.group(2));
                    if (expectedType.isPrimitive()) {
                        // Long requires explicit declaration as a long literal
                        result = m.group(2) + "L";
                    } else {
                        result = "Long.valueOf(\"" + m.group(2) + "\")";
                    }
                } catch (NumberFormatException e) {
                    log.debug("Failed to convert [" + m.group(2) + "] to Long", e);
                    // Continue and resolve the value at runtime
                }
            }
        // Numeric - int/Integer
        } else if (Integer.TYPE == expectedType || Integer.class == expectedType) {
            Matcher m = PATTERN_NUMERIC.matcher(expression);
            if (m.matches()) {
                try {
                    @SuppressWarnings("unused")
                    Integer unused = Integer.valueOf(m.group(2));
                    if (expectedType.isPrimitive()) {
                        result = m.group(2);
                    } else {
                        result = "Integer.valueOf(\"" + m.group(2) + "\")";
                    }
                } catch (NumberFormatException e) {
                    log.debug("Failed to convert [" + m.group(2) + "] to Integer", e);
                    // Continue and resolve the value at runtime
                }
            }
        // Numeric - short/Short
        } else if (Short.TYPE == expectedType || Short.class == expectedType) {
            Matcher m = PATTERN_NUMERIC.matcher(expression);
            if (m.matches()) {
                try {
                    @SuppressWarnings("unused")
                    Short unused = Short.valueOf(m.group(2));
                    if (expectedType.isPrimitive()) {
                        // short requires a downcast
                        result = "(short) " + m.group(2);
                    } else {
                        result = "Short.valueOf(\"" + m.group(2) + "\")";
                    }
                } catch (NumberFormatException e) {
                    log.debug("Failed to convert [" + m.group(2) + "] to Short", e);
                    // Continue and resolve the value at runtime
                }
            }
        // Numeric - byte/Byte
        } else if (Byte.TYPE == expectedType || Byte.class == expectedType) {
            Matcher m = PATTERN_NUMERIC.matcher(expression);
            if (m.matches()) {
                try {
                    @SuppressWarnings("unused")
                    Byte unused = Byte.valueOf(m.group(2));
                    if (expectedType.isPrimitive()) {
                        // byte requires a downcast
                        result = "(byte) " + m.group(2);
                    } else {
                        result = "Byte.valueOf(\"" + m.group(2) + "\")";
                    }
                } catch (NumberFormatException e) {
                    log.debug("Failed to convert [" + m.group(2) + "] to Byte", e);
                    // Continue and resolve the value at runtime
                }
            }
        // Numeric - double/Double
        } else if (Double.TYPE == expectedType || Double.class == expectedType) {
            Matcher m = PATTERN_NUMERIC.matcher(expression);
            if (m.matches()) {
                try {
                    @SuppressWarnings("unused")
                    Double unused = Double.valueOf(m.group(2));
                    if (expectedType.isPrimitive()) {
                        result = m.group(2);
                    } else {
                        result = "Double.valueOf(\"" + m.group(2) + "\")";
                    }
                } catch (NumberFormatException e) {
                    log.debug("Failed to convert [" + m.group(2) + "] to Double", e);
                    // Continue and resolve the value at runtime
                }
            }
        // Numeric - float/Float
        } else if (Float.TYPE == expectedType || Float.class == expectedType) {
            Matcher m = PATTERN_NUMERIC.matcher(expression);
            if (m.matches()) {
                try {
                    @SuppressWarnings("unused")
                    Float unused = Float.valueOf(m.group(2));
                    if (expectedType.isPrimitive()) {
                        // Float requires explicit declaration as a float literal
                        result = m.group(2) + "f";
                    } else {
                        result = "Float.valueOf(\"" + m.group(2) + "\")";
                    }
                } catch (NumberFormatException e) {
                    log.debug("Failed to convert [" + m.group(2) + "] to Float", e);
                    // Continue and resolve the value at runtime
                }
            }
        // Numeric - BigInteger
        } else if (BigInteger.class == expectedType) {
            Matcher m = PATTERN_NUMERIC.matcher(expression);
            if (m.matches()) {
                try {
                    @SuppressWarnings("unused")
                    BigInteger unused = new BigInteger(m.group(2));
                    result = "new java.math.BigInteger(\"" + m.group(2) + "\")";
                } catch (NumberFormatException e) {
                    log.debug("Failed to convert [" + m.group(2) + "] to BigInteger", e);
                    // Continue and resolve the value at runtime
                }
            }
        // Enum
        } else if (expectedType.isEnum()){
            Matcher m = PATTERN_STRING_CONSTANT.matcher(expression);
            if (m.matches()) {
                try {
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    Enum<?> enumValue = Enum.valueOf((Class<? extends Enum>) expectedType, m.group(2));
                    result = expectedType.getName() + "." + enumValue.name();
                } catch (IllegalArgumentException iae) {
                    log.debug("Failed to convert [" + m.group(2) + "] to Enum type [" + expectedType.getName() + "]", iae);
                    // Continue and resolve the value at runtime
                }
            }
        // String
        } else if (String.class == expectedType) {
            Matcher m = PATTERN_STRING_CONSTANT.matcher(expression);
            if (m.matches()) {
                result = "\"" + m.group(2) + "\"";
            }
        }

        if (result == null) {
            result = JspUtil.interpreterCall(isTagFile, expression, expectedType,
                    fnmapvar);
        }

        if (log.isDebugEnabled()) {
            log.debug("Expression [" + expression + "], type [" + expectedType.getName() + "], returns [" + result + "]");
        }

        return result;
    }
}
