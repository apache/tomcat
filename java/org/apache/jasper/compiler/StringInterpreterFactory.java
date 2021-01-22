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
package org.apache.jasper.compiler;

import javax.servlet.ServletContext;

/**
 * Provides {@link StringInterpreter} instances for JSP compilation.
 *
 * The search order is as follows:
 * <ol>
 * <li>StringInterpreter instance or implementation class name provided as a
 *     ServletContext attribute</li>
 * <li>Implementation class named in a ServletContext initialisation parameter
 *     </li>
 * <li>Default implementation</li>
 * </ol>
 */
public class StringInterpreterFactory {

    public static final String STRING_INTERPRETER_CLASS_NAME = StringInterpreter.class.getName();

    private static final StringInterpreter DEFAULT_INSTANCE = new DefaultStringInterpreter();


    /**
     * Obtain the correct String Interpreter for the given web application.
     * @param context The Servlet context
     * @return the String interpreter
     * @throws Exception If an error occurs creating the interpreter
     */
    public static StringInterpreter getStringInterpreter(ServletContext context)
            throws Exception {

        StringInterpreter result = null;

        // Search for an implementation
        // 1. ServletContext attribute (set by application or cached by a
        //    previous call to this method).
        Object attribute = context.getAttribute(STRING_INTERPRETER_CLASS_NAME);
        if (attribute instanceof StringInterpreter) {
            return (StringInterpreter) attribute;
        } else if (attribute instanceof String) {
            result = createInstance(context, (String) attribute);
        }

        // 2. ServletContext init parameter
        if (result == null) {
            String className = context.getInitParameter(STRING_INTERPRETER_CLASS_NAME);
            if (className != null) {
                result = createInstance(context, className);
            }
        }

        // 3. Default
        if (result == null) {
            result = DEFAULT_INSTANCE;
        }

        // Cache the result for next time
        context.setAttribute(STRING_INTERPRETER_CLASS_NAME, result);
        return result;
    }


    private static StringInterpreter createInstance(ServletContext context,
            String className) throws Exception {
        return (StringInterpreter) context.getClassLoader().loadClass(
                    className).getConstructor().newInstance();
    }


    private StringInterpreterFactory() {
        // Utility class. Hide default constructor.
    }


    public static class DefaultStringInterpreter implements StringInterpreter {

        @Override
        public String convertString(Class<?> c, String s, String attrName,
                Class<?> propEditorClass, boolean isNamedAttribute) {

            String quoted = s;
            if (!isNamedAttribute) {
                quoted = Generator.quote(s);
            }

            if (propEditorClass != null) {
                String className = c.getCanonicalName();
                return "("
                        + className
                        + ")org.apache.jasper.runtime.JspRuntimeLibrary.getValueFromBeanInfoPropertyEditor("
                        + className + ".class, \"" + attrName + "\", " + quoted
                        + ", " + propEditorClass.getCanonicalName() + ".class)";
            } else if (c == String.class) {
                return quoted;
            } else if (c == boolean.class) {
                return JspUtil.coerceToPrimitiveBoolean(s, isNamedAttribute);
            } else if (c == Boolean.class) {
                return JspUtil.coerceToBoolean(s, isNamedAttribute);
            } else if (c == byte.class) {
                return JspUtil.coerceToPrimitiveByte(s, isNamedAttribute);
            } else if (c == Byte.class) {
                return JspUtil.coerceToByte(s, isNamedAttribute);
            } else if (c == char.class) {
                return JspUtil.coerceToChar(s, isNamedAttribute);
            } else if (c == Character.class) {
                return JspUtil.coerceToCharacter(s, isNamedAttribute);
            } else if (c == double.class) {
                return JspUtil.coerceToPrimitiveDouble(s, isNamedAttribute);
            } else if (c == Double.class) {
                return JspUtil.coerceToDouble(s, isNamedAttribute);
            } else if (c == float.class) {
                return JspUtil.coerceToPrimitiveFloat(s, isNamedAttribute);
            } else if (c == Float.class) {
                return JspUtil.coerceToFloat(s, isNamedAttribute);
            } else if (c == int.class) {
                return JspUtil.coerceToInt(s, isNamedAttribute);
            } else if (c == Integer.class) {
                return JspUtil.coerceToInteger(s, isNamedAttribute);
            } else if (c == short.class) {
                return JspUtil.coerceToPrimitiveShort(s, isNamedAttribute);
            } else if (c == Short.class) {
                return JspUtil.coerceToShort(s, isNamedAttribute);
            } else if (c == long.class) {
                return JspUtil.coerceToPrimitiveLong(s, isNamedAttribute);
            } else if (c == Long.class) {
                return JspUtil.coerceToLong(s, isNamedAttribute);
            } else if (c == Object.class) {
                return quoted;
            }

            String result = coerceToOtherType(c, s, isNamedAttribute);

            if (result != null) {
                return result;
            }

            String className = c.getCanonicalName();
            return "("
                    + className
                    + ")org.apache.jasper.runtime.JspRuntimeLibrary.getValueFromPropertyEditorManager("
                    + className + ".class, \"" + attrName + "\", " + quoted
                    + ")";
        }


        /**
         * Intended to be used by sub-classes that don't need/want to
         * re-implement the logic in
         * {@link #convertString(Class, String, String, Class, boolean)}.
         *
         * @param c                 unused
         * @param s                 unused
         * @param isNamedAttribute  unused
         *
         * @return Always {@code null}
         */
        protected String coerceToOtherType(Class<?> c, String s, boolean isNamedAttribute) {
            return null;
        }
    }
}
