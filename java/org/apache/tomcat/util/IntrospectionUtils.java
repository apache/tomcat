/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.security.PermissionCheck;

/**
 * Utils for introspection and reflection
 */
public final class IntrospectionUtils {

    private static final Log log = LogFactory.getLog(IntrospectionUtils.class);

    /**
     * Find a method with the right name If found, call the method ( if param is
     * int or boolean we'll convert value to the right type before) - that means
     * you can have setDebug(1).
     * @param o The object to set a property on
     * @param name The property name
     * @param value The property value
     * @return <code>true</code> if operation was successful
     */
    public static boolean setProperty(Object o, String name, String value) {
        return setProperty(o,name,value,true);
    }

    @SuppressWarnings("null") // setPropertyMethodVoid is not null when used
    public static boolean setProperty(Object o, String name, String value,
            boolean invokeSetProperty) {
        if (log.isDebugEnabled())
            log.debug("IntrospectionUtils: setProperty(" +
                    o.getClass() + " " + name + "=" + value + ")");

        String setter = "set" + capitalize(name);

        try {
            Method methods[] = findMethods(o.getClass());
            Method setPropertyMethodVoid = null;
            Method setPropertyMethodBool = null;

            // First, the ideal case - a setFoo( String ) method
            for (Method item : methods) {
                Class<?> paramT[] = item.getParameterTypes();
                if (setter.equals(item.getName()) && paramT.length == 1
                        && "java.lang.String".equals(paramT[0].getName())) {

                    item.invoke(o, new Object[]{value});
                    return true;
                }
            }

            // Try a setFoo ( int ) or ( boolean )
            for (Method method : methods) {
                boolean ok = true;
                if (setter.equals(method.getName())
                        && method.getParameterTypes().length == 1) {

                    // match - find the type and invoke it
                    Class<?> paramType = method.getParameterTypes()[0];
                    Object params[] = new Object[1];

                    // Try a setFoo ( int )
                    if ("java.lang.Integer".equals(paramType.getName())
                            || "int".equals(paramType.getName())) {
                        try {
                            params[0] = Integer.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                        // Try a setFoo ( long )
                    } else if ("java.lang.Long".equals(paramType.getName())
                            || "long".equals(paramType.getName())) {
                        try {
                            params[0] = Long.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }

                        // Try a setFoo ( boolean )
                    } else if ("java.lang.Boolean".equals(paramType.getName())
                            || "boolean".equals(paramType.getName())) {
                        params[0] = Boolean.valueOf(value);

                        // Try a setFoo ( InetAddress )
                    } else if ("java.net.InetAddress".equals(paramType
                            .getName())) {
                        try {
                            params[0] = InetAddress.getByName(value);
                        } catch (UnknownHostException exc) {
                            if (log.isDebugEnabled())
                                log.debug("IntrospectionUtils: Unable to resolve host name:" + value);
                            ok = false;
                        }

                        // Unknown type
                    } else {
                        if (log.isDebugEnabled())
                            log.debug("IntrospectionUtils: Unknown type " +
                                    paramType.getName());
                    }

                    if (ok) {
                        method.invoke(o, params);
                        return true;
                    }
                }

                // save "setProperty" for later
                if ("setProperty".equals(method.getName())) {
                    if (method.getReturnType() == Boolean.TYPE) {
                        setPropertyMethodBool = method;
                    } else {
                        setPropertyMethodVoid = method;
                    }

                }
            }

            // Ok, no setXXX found, try a setProperty("name", "value")
            if (invokeSetProperty && (setPropertyMethodBool != null ||
                    setPropertyMethodVoid != null)) {
                Object params[] = new Object[2];
                params[0] = name;
                params[1] = value;
                if (setPropertyMethodBool != null) {
                    try {
                        return ((Boolean) setPropertyMethodBool.invoke(o,
                                params)).booleanValue();
                    }catch (IllegalArgumentException biae) {
                        //the boolean method had the wrong
                        //parameter types. lets try the other
                        if (setPropertyMethodVoid!=null) {
                            setPropertyMethodVoid.invoke(o, params);
                            return true;
                        }else {
                            throw biae;
                        }
                    }
                } else {
                    setPropertyMethodVoid.invoke(o, params);
                    return true;
                }
            }

        } catch (IllegalArgumentException ex2) {
            log.warn("IAE " + o + " " + name + " " + value, ex2);
        } catch (SecurityException ex1) {
            log.warn("IntrospectionUtils: SecurityException for " +
                    o.getClass() + " " + name + "=" + value + ")", ex1);
        } catch (IllegalAccessException iae) {
            log.warn("IntrospectionUtils: IllegalAccessException for " +
                    o.getClass() + " " + name + "=" + value + ")", iae);
        } catch (InvocationTargetException ie) {
            ExceptionUtils.handleThrowable(ie.getCause());
            log.warn("IntrospectionUtils: InvocationTargetException for " +
                    o.getClass() + " " + name + "=" + value + ")", ie);
        }
        return false;
    }

    public static Object getProperty(Object o, String name) {
        String getter = "get" + capitalize(name);
        String isGetter = "is" + capitalize(name);

        try {
            Method methods[] = findMethods(o.getClass());
            Method getPropertyMethod = null;

            // First, the ideal case - a getFoo() method
            for (Method method : methods) {
                Class<?> paramT[] = method.getParameterTypes();
                if (getter.equals(method.getName()) && paramT.length == 0) {
                    return method.invoke(o, (Object[]) null);
                }
                if (isGetter.equals(method.getName()) && paramT.length == 0) {
                    return method.invoke(o, (Object[]) null);
                }

                if ("getProperty".equals(method.getName())) {
                    getPropertyMethod = method;
                }
            }

            // Ok, no setXXX found, try a getProperty("name")
            if (getPropertyMethod != null) {
                Object params[] = new Object[1];
                params[0] = name;
                return getPropertyMethod.invoke(o, params);
            }

        } catch (IllegalArgumentException ex2) {
            log.warn("IAE " + o + " " + name, ex2);
        } catch (SecurityException ex1) {
            log.warn("IntrospectionUtils: SecurityException for " +
                    o.getClass() + " " + name + ")", ex1);
        } catch (IllegalAccessException iae) {
            log.warn("IntrospectionUtils: IllegalAccessException for " +
                    o.getClass() + " " + name + ")", iae);
        } catch (InvocationTargetException ie) {
            if (ie.getCause() instanceof NullPointerException) {
                // Assume the underlying object uses a storage to represent an unset property
                return null;
            }
            ExceptionUtils.handleThrowable(ie.getCause());
            log.warn("IntrospectionUtils: InvocationTargetException for " +
                    o.getClass() + " " + name + ")", ie);
        }
        return null;
    }

    /**
     * Replaces ${NAME} in the value with the value of the property 'NAME'.
     * Replaces ${NAME:DEFAULT} with the value of the property 'NAME:DEFAULT',
     * if the property 'NAME:DEFAULT' is not set,
     * the expression is replaced with the value of the property 'NAME',
     * if the property 'NAME' is not set,
     * the expression is replaced with 'DEFAULT'.
     * If the property is not set and there is no default the value will be
     * returned unmodified.
     *
     * @param value The value
     * @param staticProp Replacement properties
     * @param dynamicProp Replacement properties
     * @return the replacement value
     * @deprecated Use {@link #replaceProperties(String, Hashtable, PropertySource[], ClassLoader)}
     */
    @Deprecated
    public static String replaceProperties(String value,
            Hashtable<Object,Object> staticProp, PropertySource dynamicProp[]) {
        return replaceProperties(value, staticProp, dynamicProp, null);
    }

    /**
     * Replace ${NAME} with the property value.
     * @param value The value
     * @param staticProp Replacement properties
     * @param dynamicProp Replacement properties
     * @param classLoader Class loader associated with the code requesting the
     *                    property
     *
     * @return the replacement value
     */
    public static String replaceProperties(String value,
            Hashtable<Object,Object> staticProp, PropertySource dynamicProp[],
            ClassLoader classLoader) {

        if (value.indexOf('$') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        int prev = 0;
        // assert value!=nil
        int pos;
        while ((pos = value.indexOf('$', prev)) >= 0) {
            if (pos > 0) {
                sb.append(value.substring(prev, pos));
            }
            if (pos == (value.length() - 1)) {
                sb.append('$');
                prev = pos + 1;
            } else if (value.charAt(pos + 1) != '{') {
                sb.append('$');
                prev = pos + 1; // XXX
            } else {
                int endName = value.indexOf('}', pos);
                if (endName < 0) {
                    sb.append(value.substring(pos));
                    prev = value.length();
                    continue;
                }
                String n = value.substring(pos + 2, endName);
                String v = getProperty(n, staticProp, dynamicProp, classLoader);
                if (v == null) {
                    // {name:default}
                    int col = n.indexOf(":-");
                    if (col != -1) {
                        String dV = n.substring(col + 2);
                        n = n.substring(0, col);
                        v = getProperty(n, staticProp, dynamicProp, classLoader);
                        if (v == null) {
                            v = dV;
                        }
                    } else {
                        v = "${" + n + "}";
                    }
                }
                sb.append(v);
                prev = endName + 1;
            }
        }
        if (prev < value.length())
            sb.append(value.substring(prev));
        return sb.toString();
    }

    private static String getProperty(String name, Hashtable<Object, Object> staticProp,
            PropertySource[] dynamicProp, ClassLoader classLoader) {
        String v = null;
        if (staticProp != null) {
            v = (String) staticProp.get(name);
        }
        if (v == null && dynamicProp != null) {
            for (PropertySource propertySource : dynamicProp) {
                if (propertySource instanceof SecurePropertySource) {
                    v = ((SecurePropertySource) propertySource).getProperty(name, classLoader);
                } else {
                    v = propertySource.getProperty(name);
                }
                if (v != null) {
                    break;
                }
            }
        }
        return v;
    }

    /**
     * Reverse of Introspector.decapitalize.
     * @param name The name
     * @return the capitalized string
     */
    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    // -------------------- other utils --------------------
    public static void clear() {
        objectMethods.clear();
    }

    private static final Hashtable<Class<?>,Method[]> objectMethods = new Hashtable<>();

    public static Method[] findMethods(Class<?> c) {
        Method methods[] = objectMethods.get(c);
        if (methods != null)
            return methods;

        methods = c.getMethods();
        objectMethods.put(c, methods);
        return methods;
    }

    @SuppressWarnings("null") // params cannot be null when comparing lengths
    public static Method findMethod(Class<?> c, String name,
            Class<?> params[]) {
        Method methods[] = findMethods(c);
        for (Method method : methods) {
            if (method.getName().equals(name)) {
                Class<?> methodParams[] = method.getParameterTypes();
                if (params == null && methodParams.length == 0) {
                    return method;
                }
                if (params.length != methodParams.length) {
                    continue;
                }
                boolean found = true;
                for (int j = 0; j < params.length; j++) {
                    if (params[j] != methodParams[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    return method;
                }
            }
        }
        return null;
    }

    public static Object callMethod1(Object target, String methodN,
            Object param1, String typeParam1, ClassLoader cl) throws Exception {
        if (target == null || param1 == null) {
            throw new IllegalArgumentException(
                    "IntrospectionUtils: Assert: Illegal params " +
                    target + " " + param1);
        }
        if (log.isDebugEnabled())
            log.debug("IntrospectionUtils: callMethod1 " +
                    target.getClass().getName() + " " +
                    param1.getClass().getName() + " " + typeParam1);

        Class<?> params[] = new Class[1];
        if (typeParam1 == null)
            params[0] = param1.getClass();
        else
            params[0] = cl.loadClass(typeParam1);
        Method m = findMethod(target.getClass(), methodN, params);
        if (m == null)
            throw new NoSuchMethodException(target.getClass().getName() + " "
                    + methodN);
        try {
            return m.invoke(target, new Object[] { param1 });
        } catch (InvocationTargetException ie) {
            ExceptionUtils.handleThrowable(ie.getCause());
            throw ie;
        }
    }

    public static Object callMethodN(Object target, String methodN,
            Object params[], Class<?> typeParams[]) throws Exception {
        Method m = null;
        m = findMethod(target.getClass(), methodN, typeParams);
        if (m == null) {
            if (log.isDebugEnabled())
                log.debug("IntrospectionUtils: Can't find method " + methodN +
                        " in " + target + " CLASS " + target.getClass());
            return null;
        }
        try {
            Object o = m.invoke(target, params);

            if (log.isDebugEnabled()) {
                // debug
                StringBuilder sb = new StringBuilder();
                sb.append(target.getClass().getName()).append('.')
                        .append(methodN).append("( ");
                for (int i = 0; i < params.length; i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(params[i]);
                }
                sb.append(")");
                log.debug("IntrospectionUtils:" + sb.toString());
            }
            return o;
        } catch (InvocationTargetException ie) {
            ExceptionUtils.handleThrowable(ie.getCause());
            throw ie;
        }
    }

    public static Object convert(String object, Class<?> paramType) {
        Object result = null;
        if ("java.lang.String".equals(paramType.getName())) {
            result = object;
        } else if ("java.lang.Integer".equals(paramType.getName())
                || "int".equals(paramType.getName())) {
            try {
                result = Integer.valueOf(object);
            } catch (NumberFormatException ex) {
            }
            // Try a setFoo ( boolean )
        } else if ("java.lang.Boolean".equals(paramType.getName())
                || "boolean".equals(paramType.getName())) {
            result = Boolean.valueOf(object);

            // Try a setFoo ( InetAddress )
        } else if ("java.net.InetAddress".equals(paramType
                .getName())) {
            try {
                result = InetAddress.getByName(object);
            } catch (UnknownHostException exc) {
                if (log.isDebugEnabled())
                    log.debug("IntrospectionUtils: Unable to resolve host name:" +
                            object);
            }

            // Unknown type
        } else {
            if (log.isDebugEnabled())
                log.debug("IntrospectionUtils: Unknown type " +
                        paramType.getName());
        }
        if (result == null) {
            throw new IllegalArgumentException("Can't convert argument: " + object);
        }
        return result;
    }


    /**
     * Checks to see if the specified class is an instance of or assignable from
     * the specified type. The class <code>clazz</code>, all its superclasses,
     * interfaces and those superinterfaces are tested for a match against
     * the type name <code>type</code>.
     *
     * This is similar to <code>instanceof</code> or {@link Class#isAssignableFrom}
     * except that the target type will not be resolved into a Class
     * object, which provides some security and memory benefits.
     *
     * @param clazz The class to test for a match.
     * @param type The name of the type that <code>clazz</code> must be.
     *
     * @return <code>true</code> if the <code>clazz</code> tested is an
     *         instance of the specified <code>type</code>,
     *         <code>false</code> otherwise.
     */
    public static boolean isInstance(Class<?> clazz, String type) {
        if (type.equals(clazz.getName())) {
            return true;
        }

        Class<?>[] ifaces = clazz.getInterfaces();
        for (Class<?> iface : ifaces) {
            if (isInstance(iface, type)) {
                return true;
            }
        }

        Class<?> superClazz = clazz.getSuperclass();
        if (superClazz == null) {
            return false;
        } else {
            return isInstance(superClazz, type);
        }
    }


    // -------------------- Get property --------------------
    // This provides a layer of abstraction

    public static interface PropertySource {
        public String getProperty(String key);
    }


    public static interface SecurePropertySource extends PropertySource {

        /**
         * Obtain a property value, checking that code associated with the
         * provided class loader has permission to access the property. If the
         * {@code classLoader} is {@code null} or if {@code classLoader} does
         * not implement {@link PermissionCheck} then the property value will be
         * looked up <b>without</b> a call to
         * {@link PermissionCheck#check(java.security.Permission)}
         *
         * @param key           The key of the requested property
         * @param classLoader   The class loader associated with the code that
         *                      trigger the property lookup
         * @return The property value or {@code null} if it could not be found
         *         or if {@link PermissionCheck#check(java.security.Permission)}
         *         fails
         */
        public String getProperty(String key, ClassLoader classLoader);
    }
}
