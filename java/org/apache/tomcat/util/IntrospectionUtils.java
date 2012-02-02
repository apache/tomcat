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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.StringTokenizer;

/**
 * Utils for introspection and reflection
 */
public final class IntrospectionUtils {


    private static final org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( IntrospectionUtils.class );

    /**
     * @deprecated  No longer required. Will be removed in Tomcat 8.0.x.
     */
    @Deprecated
    public static String guessInstall(String installSysProp,
            String homeSysProp, String jarName) {
        return guessInstall(installSysProp, homeSysProp, jarName, null);
    }

    /**
     * Guess a product install/home by analyzing the class path. It works for
     * product using the pattern: lib/executable.jar or if executable.jar is
     * included in classpath by a shell script. ( java -jar also works )
     *
     * Insures both "install" and "home" System properties are set. If either or
     * both System properties are unset, "install" and "home" will be set to the
     * same value. This value will be the other System property that is set, or
     * the guessed value if neither is set.
     *
     * @deprecated  No longer required. Will be removed in Tomcat 8.0.x.
     */
    @Deprecated
    public static String guessInstall(String installSysProp,
            String homeSysProp, String jarName, String classFile) {
        String install = null;
        String home = null;

        if (installSysProp != null)
            install = System.getProperty(installSysProp);

        if (homeSysProp != null)
            home = System.getProperty(homeSysProp);

        if (install != null) {
            if (home == null)
                System.getProperties().put(homeSysProp, install);
            return install;
        }

        // Find the directory where jarName.jar is located

        String cpath = System.getProperty("java.class.path");
        String pathSep = System.getProperty("path.separator");
        StringTokenizer st = new StringTokenizer(cpath, pathSep);
        while (st.hasMoreTokens()) {
            String path = st.nextToken();
            // log( "path " + path );
            if (path.endsWith(jarName)) {
                home = path.substring(0, path.length() - jarName.length());
                try {
                    if ("".equals(home)) {
                        home = new File("./").getCanonicalPath();
                    } else if (home.endsWith(File.separator)) {
                        home = home.substring(0, home.length() - 1);
                    }
                    File f = new File(home);
                    String parentDir = f.getParent();
                    if (parentDir == null)
                        parentDir = home; // unix style
                    File f1 = new File(parentDir);
                    install = f1.getCanonicalPath();
                    if (installSysProp != null)
                        System.getProperties().put(installSysProp, install);
                    if (home == null && homeSysProp != null)
                        System.getProperties().put(homeSysProp, install);
                    return install;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                String fname = path + (path.endsWith("/") ? "" : "/")
                        + classFile;
                if (new File(fname).exists()) {
                    try {
                        File f = new File(path);
                        String parentDir = f.getParent();
                        if (parentDir == null)
                            parentDir = path; // unix style
                        File f1 = new File(parentDir);
                        install = f1.getCanonicalPath();
                        if (installSysProp != null)
                            System.getProperties().put(installSysProp, install);
                        if (home == null && homeSysProp != null)
                            System.getProperties().put(homeSysProp, install);
                        return install;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        // if install directory can't be found, use home as the default
        if (home != null) {
            System.getProperties().put(installSysProp, home);
            return home;
        }

        return null;
    }

    /**
     * Find a method with the right name If found, call the method ( if param is
     * int or boolean we'll convert value to the right type before) - that means
     * you can have setDebug(1).
     */
    public static boolean setProperty(Object o, String name, String value) {
        return setProperty(o,name,value,true);
    }
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
            for (int i = 0; i < methods.length; i++) {
                Class<?> paramT[] = methods[i].getParameterTypes();
                if (setter.equals(methods[i].getName()) && paramT.length == 1
                        && "java.lang.String".equals(paramT[0].getName())) {

                    methods[i].invoke(o, new Object[] { value });
                    return true;
                }
            }

            // Try a setFoo ( int ) or ( boolean )
            for (int i = 0; i < methods.length; i++) {
                boolean ok = true;
                if (setter.equals(methods[i].getName())
                        && methods[i].getParameterTypes().length == 1) {

                    // match - find the type and invoke it
                    Class<?> paramType = methods[i].getParameterTypes()[0];
                    Object params[] = new Object[1];

                    // Try a setFoo ( int )
                    if ("java.lang.Integer".equals(paramType.getName())
                            || "int".equals(paramType.getName())) {
                        try {
                            params[0] = new Integer(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                    // Try a setFoo ( long )
                    }else if ("java.lang.Long".equals(paramType.getName())
                                || "long".equals(paramType.getName())) {
                            try {
                                params[0] = new Long(value);
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
                        methods[i].invoke(o, params);
                        return true;
                    }
                }

                // save "setProperty" for later
                if ("setProperty".equals(methods[i].getName())) {
                    if (methods[i].getReturnType()==Boolean.TYPE){
                        setPropertyMethodBool = methods[i];
                    }else {
                        setPropertyMethodVoid = methods[i];
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
            if (log.isDebugEnabled())
                log.debug("IntrospectionUtils: SecurityException for " +
                        o.getClass() + " " + name + "=" + value + ")", ex1);
        } catch (IllegalAccessException iae) {
            if (log.isDebugEnabled())
                log.debug("IntrospectionUtils: IllegalAccessException for " +
                        o.getClass() + " " + name + "=" + value + ")", iae);
        } catch (InvocationTargetException ie) {
            ExceptionUtils.handleThrowable(ie.getCause());
            if (log.isDebugEnabled())
                log.debug("IntrospectionUtils: InvocationTargetException for " +
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
            for (int i = 0; i < methods.length; i++) {
                Class<?> paramT[] = methods[i].getParameterTypes();
                if (getter.equals(methods[i].getName()) && paramT.length == 0) {
                    return methods[i].invoke(o, (Object[]) null);
                }
                if (isGetter.equals(methods[i].getName()) && paramT.length == 0) {
                    return methods[i].invoke(o, (Object[]) null);
                }

                if ("getProperty".equals(methods[i].getName())) {
                    getPropertyMethod = methods[i];
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
            if (log.isDebugEnabled())
                log.debug("IntrospectionUtils: SecurityException for " +
                        o.getClass() + " " + name + ")", ex1);
        } catch (IllegalAccessException iae) {
            if (log.isDebugEnabled())
                log.debug("IntrospectionUtils: IllegalAccessException for " +
                        o.getClass() + " " + name + ")", iae);
        } catch (InvocationTargetException ie) {
            ExceptionUtils.handleThrowable(ie.getCause());
            if (log.isDebugEnabled())
                log.debug("IntrospectionUtils: InvocationTargetException for " +
                        o.getClass() + " " + name + ")");
        }
        return null;
    }

    /**
     * Replace ${NAME} with the property value
     */
    public static String replaceProperties(String value,
            Hashtable<Object,Object> staticProp, PropertySource dynamicProp[]) {
        if (value.indexOf("$") < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        int prev = 0;
        // assert value!=nil
        int pos;
        while ((pos = value.indexOf("$", prev)) >= 0) {
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
                String v = null;
                if (staticProp != null) {
                    v = (String) staticProp.get(n);
                }
                if (v == null && dynamicProp != null) {
                    for (int i = 0; i < dynamicProp.length; i++) {
                        v = dynamicProp[i].getProperty(n);
                        if (v != null) {
                            break;
                        }
                    }
                }
                if (v == null)
                    v = "${" + n + "}";

                sb.append(v);
                prev = endName + 1;
            }
        }
        if (prev < value.length())
            sb.append(value.substring(prev));
        return sb.toString();
    }

    /**
     * Reverse of Introspector.decapitalize
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

    static Hashtable<Class<?>,Method[]> objectMethods =
        new Hashtable<Class<?>,Method[]>();

    public static Method[] findMethods(Class<?> c) {
        Method methods[] = objectMethods.get(c);
        if (methods != null)
            return methods;

        methods = c.getMethods();
        objectMethods.put(c, methods);
        return methods;
    }

    public static Method findMethod(Class<?> c, String name,
            Class<?> params[]) {
        Method methods[] = findMethods(c);
        if (methods == null)
            return null;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals(name)) {
                Class<?> methodParams[] = methods[i].getParameterTypes();
                if (methodParams == null)
                    if (params == null || params.length == 0)
                        return methods[i];
                if (params == null)
                    if (methodParams == null || methodParams.length == 0)
                        return methods[i];
                if (params.length != methodParams.length)
                    continue;
                boolean found = true;
                for (int j = 0; j < params.length; j++) {
                    if (params[j] != methodParams[j]) {
                        found = false;
                        break;
                    }
                }
                if (found)
                    return methods[i];
            }
        }
        return null;
    }

    public static Object callMethod1(Object target, String methodN,
            Object param1, String typeParam1, ClassLoader cl) throws Exception {
        if (target == null || param1 == null) {
            if (log.isDebugEnabled())
                log.debug("IntrospectionUtils: Assert: Illegal params " +
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

    /**
     * @deprecated Not used, though compliments callMethod1 and callMethodN here
     */
    @Deprecated
    public static Object callMethod0(Object target, String methodN)
            throws Exception {
        if (target == null) {
            if (log.isDebugEnabled())
                log.debug("IntrospectionUtils: Assert: Illegal params " +
                        target);
            return null;
        }
        if (log.isDebugEnabled())
            log.debug("IntrospectionUtils: callMethod0 " +
                    target.getClass().getName() + "." + methodN);

        Class<?> params[] = new Class[0];
        Method m = findMethod(target.getClass(), methodN, params);
        if (m == null)
            throw new NoSuchMethodException(target.getClass().getName() + " "
                    + methodN);
        try {
            return m.invoke(target, emptyArray);
        } catch (InvocationTargetException ie) {
            ExceptionUtils.handleThrowable(ie.getCause());
            throw ie;
        }
    }

    /**
     * @deprecated Used only by deprecated method
     */
    @Deprecated
    private static final Object[] emptyArray = new Object[] {};

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
                result = new Integer(object);
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

    // -------------------- Get property --------------------
    // This provides a layer of abstraction

    public static interface PropertySource {

        public String getProperty(String key);

    }

}
