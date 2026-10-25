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
package org.apache.tomcat.jdbc.naming;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ClassLoaderUtil;

/**
 * Simple way of configuring generic resources by using reflection.
 * Example usage:
 * <pre><code>
 * &lt;Resource factory=&quot;org.apache.tomcat.jdbc.naming.GenericNamingResourcesFactory&quot;
 *              name=&quot;jdbc/test&quot;
 *              type=&quot;org.apache.derby.jdbc.ClientXADataSource&quot;
 *              databaseName=&quot;sample&quot;
 *              createDatabase=&quot;create&quot;
 *              serverName=&quot;localhost&quot;
 *              port=&quot;1527&quot;/&gt;
 * </code></pre>
 *
 */
public class GenericNamingResourcesFactory implements ObjectFactory {
    /**
     * Creates a new GenericNamingResourcesFactory instance.
     */
    public GenericNamingResourcesFactory() {
        // Default constructor
    }

    private static final Log log = LogFactory.getLog(GenericNamingResourcesFactory.class);

    /**
     * Reference metadata that Tomcat attaches to a {@code ResourceRef}; these
     * are not bean properties of the target object. Mirrors the list excluded
     * by {@code org.apache.naming.factory.BeanFactory}. Inlined because
     * tomcat-jdbc.jar does not depend on catalina.jar.
     */
    private static final Set<String> RESERVED_ADDR_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "factory", "singleton", "scope", "auth", "description",
                    "forceString")));

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        if ((obj == null) || !(obj instanceof Reference)) {
            return null;
        }
        Reference ref = (Reference) obj;
        Enumeration<RefAddr> refs = ref.getAll();

        String type = ref.getClassName();
        if (type == null || type.isEmpty()) {
            throw new NamingException(
                    "No resource type (the \"type\" attribute) is defined for reference ["
                            + name + "]; GenericNamingResourcesFactory needs the class of the "
                            + "object to create.");
        }
        Object o;
        try {
            Class<?> clazz = ClassLoaderUtil.loadClass(
                    type,
                    GenericNamingResourcesFactory.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader());
            o = clazz.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            NamingException ne = new NamingException(
                    "Class [" + type + "] of reference [" + name + "] was not found");
            ne.setRootCause(e);
            throw ne;
        } catch (InvocationTargetException e) {
            // The class has a no-argument constructor, but it threw. Propagate
            // only a fatal VirtualMachineError, in the spirit of
            // ExceptionUtils.handleThrowable(), which tomcat-jdbc.jar cannot
            // use directly.
            Throwable cause = e.getCause();
            if (cause instanceof VirtualMachineError) {
                throw (VirtualMachineError) cause;
            }
            NamingException ne = new NamingException("The constructor of class ["
                    + type + "] of reference [" + name
                    + "] threw an exception and the object cannot be created");
            ne.setRootCause(e);
            throw ne;
        } catch (ReflectiveOperationException e) {
            // No public no-arg constructor (NoSuchMethodException), or the
            // class cannot be instantiated (abstract, interface, illegal
            // access).
            NamingException ne = new NamingException("Class [" + type
                    + "] of reference [" + name
                    + "] has no usable public no-argument constructor and "
                    + "cannot be created by this factory");
            ne.setRootCause(e);
            throw ne;
        }

        while (refs.hasMoreElements()) {
            RefAddr addr = refs.nextElement();
            String param = addr.getType();
            if (param == null || RESERVED_ADDR_TYPES.contains(param)) {
                // reference metadata, not a configuration property
                continue;
            }
            String value = null;
            if (addr.getContent()!=null) {
                value = addr.getContent().toString();
            }
            if (!setProperty(o, param, value)) {
                log.warn("Property [" + param + "] was not applied to ["
                        + type + "]: no usable setter found, or the "
                        + "configured value could not be applied.");
            }
        }
        return o;
    }

    @SuppressWarnings("null") // setPropertyMethodVoid can't be null when used
    private static boolean setProperty(Object o, String name, String value) {
        if (log.isTraceEnabled()) {
            log.trace("IntrospectionUtils: setProperty(" +
                    o.getClass() + " " + name + "=" + value + ")");
        }

        String setter = "set" + capitalize(name);

        try {
            Method[] methods = o.getClass().getMethods();
            Method setPropertyMethodVoid = null;
            Method setPropertyMethodBool = null;

            // First, the ideal case - a setFoo( String ) method
            for (int i = 0; i < methods.length; i++) {
                Class<?>[] paramT = methods[i].getParameterTypes();
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
                    Object[] params = new Object[1];

                    // Try a setFoo ( int )
                    if ("java.lang.Integer".equals(paramType.getName())
                            || "int".equals(paramType.getName())) {
                        try {
                            params[0] = Integer.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                    // Try a setFoo ( long )
                    }else if ("java.lang.Long".equals(paramType.getName())
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
                            if (log.isDebugEnabled()) {
                                log.debug("IntrospectionUtils: Unable to resolve host name:" + value);
                            }
                            ok = false;
                        }

                        // Unknown type
                    } else {
                        // Unsupported parameter type. Do not invoke the
                        // method with a null value. Let the caller fall back
                        // to setProperty(name, value) or handle the failure.
                        ok = false;
                        if (log.isDebugEnabled()) {
                            log.debug("IntrospectionUtils: Unknown type " +
                                    paramType.getName());
                        }
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
            if (setPropertyMethodBool != null || setPropertyMethodVoid != null) {
                Object[] params = new Object[2];
                params[0] = name;
                params[1] = value;
                if (setPropertyMethodBool != null) {
                    try {
                        return ((Boolean) setPropertyMethodBool.invoke(o, params)).booleanValue();
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
            log.warn("Unable to set property [" + name + "] with value ["
                    + value + "] on [" + o.getClass().getName() + "]", ex2);
        } catch (SecurityException ex1) {
            log.warn("SecurityException setting property [" + name
                    + "] with value [" + value + "] on ["
                    + o.getClass().getName() + "]", ex1);
        } catch (IllegalAccessException iae) {
            log.warn("IllegalAccessException setting property [" + name
                    + "] with value [" + value + "] on ["
                    + o.getClass().getName() + "]", iae);
        } catch (InvocationTargetException ie) {
            // In the spirit of ExceptionUtils.handleThrowable(), which is not
            // available to tomcat-jdbc.jar: propagate the fatal
            // VirtualMachineError from the setter and report everything else.
            Throwable cause = ie.getCause();
            if (cause instanceof VirtualMachineError) {
                throw (VirtualMachineError) cause;
            }
            log.warn("Setter for property [" + name + "] with value ["
                    + value + "] threw an exception on ["
                    + o.getClass().getName() + "]", ie);
        }
        return false;
    }

    /**
     * Capitalizes the first character of the given string.
     *
     * @param name The string to capitalize
     *
     * @return The capitalized string, or the original if null or empty
     */
    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

}
