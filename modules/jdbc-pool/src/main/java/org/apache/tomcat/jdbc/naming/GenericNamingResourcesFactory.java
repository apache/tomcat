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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
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
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Simple way of configuring generic resources by using reflection. Example usage:
 *
 * <pre>
 * <code>
 * &lt;Resource factory=&quot;org.apache.tomcat.jdbc.naming.GenericNamingResourcesFactory&quot;
 *              name=&quot;jdbc/test&quot;
 *              type=&quot;org.apache.derby.jdbc.ClientXADataSource&quot;
 *              databaseName=&quot;sample&quot;
 *              createDatabase=&quot;create&quot;
 *              serverName=&quot;localhost&quot;
 *              port=&quot;1527&quot;/&gt;
 * </code>
 * </pre>
 * <p>
 * The factory accepts any {@link Reference}, not only the container-managed
 * {@code ResourceRef} that a {@code <Resource>} element produces. The
 * referenced class is loaded with the factory's own class loader first and
 * the thread context class loader second, and a new instance built by its
 * public no-argument constructor is created for every lookup. Reference
 * metadata the container attaches ({@code factory}, {@code singleton},
 * {@code scope}, {@code auth}, {@code description} and {@code forceString})
 * is skipped; every other address is treated as a bean property.
 * <p>
 * A property is applied through a {@code setFoo(String)} setter when one
 * exists, otherwise through a single-argument setter whose parameter type the
 * configured value can be built from: {@code int}, {@code long},
 * {@code short}, {@code byte}, {@code float}, {@code double}, {@code char}
 * and {@code boolean} - each in its primitive and boxed form - and
 * {@code java.net.InetAddress}. Booleans accept only {@code true} or
 * {@code false}, ignoring case and surrounding whitespace - not the
 * anything-but-{@code true}-is-{@code false} coercion of
 * {@code org.apache.naming.factory.BeanFactory} - and an address carrying no
 * content is applied to nothing: missing content is not a configured value.
 * When no {@code setFoo} setter exists at all, a two-argument
 * {@code setProperty(name, value)} property bag is consulted; when one
 * exists but its type cannot be built from the configured value, the bag is
 * not consulted - what the bean's own setter rejects must not be silently
 * absorbed there.
 * <p>
 * A property that cannot be applied - no usable setter, a value the setter
 * type cannot be built from, or a setter that throws - fails the lookup with
 * a {@code NamingException}, so a partially configured resource never
 * reaches a caller and, in particular, is never cached by the container for
 * a singleton resource.
 */
public class GenericNamingResourcesFactory implements ObjectFactory {

    /** Default constructor. */
    public GenericNamingResourcesFactory() { }

    private static final Log log = LogFactory.getLog(GenericNamingResourcesFactory.class);

    /**
     * Reference metadata that Tomcat attaches to a {@code ResourceRef}; these are not bean properties of the target
     * object. Mirrors the list excluded by {@code org.apache.naming.factory.BeanFactory}. Inlined because
     * tomcat-jdbc.jar does not depend on catalina.jar.
     */
    private static final Set<String> RESERVED_ADDR_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("factory", "singleton", "scope", "auth", "description", "forceString")));

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment)
            throws Exception {
        if ((obj == null) || !(obj instanceof Reference)) {
            return null;
        }
        Reference ref = (Reference) obj;
        Enumeration<RefAddr> refs = ref.getAll();

        String type = ref.getClassName();
        if (type == null || type.isEmpty()) {
            throw new NamingException(
                    MessageFactory.get("genericNamingResourcesFactory.noResourceType", name));
        }
        Object o;
        try {
            Class<?> clazz = ClassLoaderUtil.loadClass(type, GenericNamingResourcesFactory.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader());
            o = clazz.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            NamingException ne =
                    new NamingException(MessageFactory.get("genericNamingResourcesFactory.classNotFound", type, name));
            ne.setRootCause(e);
            throw ne;
        } catch (InvocationTargetException e) {
            // The class has a no-argument constructor, but it threw.
            // ExceptionUtils.handleThrowable(e.getCause()) propagates a fatal
            // VirtualMachineError and accepts everything else for reporting.
            ExceptionUtils.handleThrowable(e.getCause());
            NamingException ne =
                    new NamingException(MessageFactory.get("genericNamingResourcesFactory.constructorThrew", type, name));
            ne.setRootCause(e);
            throw ne;
        } catch (ReflectiveOperationException e) {
            // No public no-arg constructor (NoSuchMethodException), or the
            // class cannot be instantiated (abstract, interface, illegal
            // access).
            NamingException ne =
                    new NamingException(MessageFactory.get("genericNamingResourcesFactory.noUsableConstructor",
                            type, name));
            ne.setRootCause(e);
            throw ne;
        }

        List<String> notApplied = new ArrayList<>();
        while (refs.hasMoreElements()) {
            RefAddr addr = refs.nextElement();
            String param = addr.getType();
            if (param == null || RESERVED_ADDR_TYPES.contains(param)) {
                // Reference metadata, not a configuration property
                continue;
            }
            String value = null;
            if (addr.getContent() != null) {
                value = addr.getContent().toString();
            }
            SetResult set = setProperty(o, param, value);
            if (set != SetResult.APPLIED) {
                notApplied.add(param);
                if (set == SetResult.UNREPORTED) {
                    // The property simply could not be converted or no usable
                    // setter was found; a matched setter that failed has
                    // already been reported by setProperty with its cause.
                    log.warn(MessageFactory.get("genericNamingResourcesFactory.propertyNotApplied", param, type));
                }
            }
        }
        // A half-configured object must not be handed to the caller: for a
        // singleton resource the container caches the returned object, so
        // every later lookup would silently get the misconfigured bean. Fail
        // the lookup instead.
        if (!notApplied.isEmpty()) {
            NamingException ne = new NamingException(MessageFactory.get(
                    "genericNamingResourcesFactory.propertiesNotApplied", notApplied, type, name));
            throw ne;
        }
        return o;
    }

    /**
     * Outcome of an attempt to apply one configured property.
     */
    private enum SetResult {
        /** The value reached the object. */
        APPLIED,
        /** The property could not be applied and the cause was reported. */
        REPORTED,
        /** The property could not be applied; the caller reports it. */
        UNREPORTED
    }

    private static SetResult setProperty(Object o, String name, String value) {
        if (log.isTraceEnabled()) {
            log.trace("setProperty(" + o.getClass() + " " + name + "=" + value + ")");
        }

        // Missing content is not a configured value: no setter - String or
        // typed - and no property bag is invoked with a fabricated null
        // that would wipe the configured default.
        if (value == null) {
            return SetResult.UNREPORTED;
        }

        String setter = "set" + capitalize(name);

        try {
            Method[] methods = o.getClass().getMethods();
            Method setPropertyMethodVoid = null;
            Method setPropertyMethodBool = null;

            // First, the ideal case - a setFoo( String ) method
            for (int i = 0; i < methods.length; i++) {
                Class<?>[] paramT = methods[i].getParameterTypes();
                if (setter.equals(methods[i].getName()) && paramT.length == 1 &&
                        "java.lang.String".equals(paramT[0].getName())) {

                    methods[i].invoke(o, new Object[] { value });
                    return SetResult.APPLIED;
                }
            }

            // No String setter: try a setFoo whose single parameter type the
            // value can be built from (int, long, short, byte, float, double,
            // char, boolean - primitive or boxed - or InetAddress). A setter
            // that matched but could not be fed is recorded, so that the
            // property bag below knows the bean has its own opinion about
            // this property.
            boolean typedSetterMatched = false;
            for (int i = 0; i < methods.length; i++) {
                boolean ok = true;
                if (setter.equals(methods[i].getName()) && methods[i].getParameterTypes().length == 1) {

                    // Match - find the type and invoke it
                    typedSetterMatched = true;
                    Class<?> paramType = methods[i].getParameterTypes()[0];
                    Object[] params = new Object[1];

                    // Try a setFoo ( int )
                    if ("java.lang.Integer".equals(paramType.getName()) || "int".equals(paramType.getName())) {
                        try {
                            params[0] = Integer.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                        // Try a setFoo ( long )
                    } else if ("java.lang.Long".equals(paramType.getName()) || "long".equals(paramType.getName())) {
                        try {
                            params[0] = Long.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                        // Try a setFoo ( short )
                    } else if ("java.lang.Short".equals(paramType.getName()) ||
                            "short".equals(paramType.getName())) {
                        try {
                            params[0] = Short.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                        // Try a setFoo ( byte )
                    } else if ("java.lang.Byte".equals(paramType.getName()) ||
                            "byte".equals(paramType.getName())) {
                        try {
                            params[0] = Byte.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                        // Try a setFoo ( float )
                    } else if ("java.lang.Float".equals(paramType.getName()) ||
                            "float".equals(paramType.getName())) {
                        try {
                            params[0] = Float.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                        // Try a setFoo ( double )
                    } else if ("java.lang.Double".equals(paramType.getName()) ||
                            "double".equals(paramType.getName())) {
                        try {
                            params[0] = Double.valueOf(value);
                        } catch (NumberFormatException ex) {
                            ok = false;
                        }
                        // Try a setFoo ( char or Character )
                    } else if ("java.lang.Character".equals(paramType.getName()) ||
                            "char".equals(paramType.getName())) {
                        // Take the first character of the value, like
                        // BeanFactory. Empty content has no first
                        // character: do not fabricate one.
                        if (value.isEmpty()) {
                            ok = false;
                        } else {
                            params[0] = Character.valueOf(value.charAt(0));
                        }

                        // Try a setFoo ( boolean )
                    } else if ("java.lang.Boolean".equals(paramType.getName()) ||
                            "boolean".equals(paramType.getName())) {
                        // Boolean.valueOf() reports everything it does not
                        // recognise, empty content included, as false.
                        // Anything that does not spell a boolean - empty
                        // content, whitespace-padded or misspelled values -
                        // is not a configured value: let the caller report
                        // the property as not applied instead of silently
                        // applying false. Same policy as the pool's own
                        // DataSourceFactory.parseBoolean().
                        String trimmed = value.trim();
                        if ("true".equalsIgnoreCase(trimmed)) {
                            params[0] = Boolean.TRUE;
                        } else if ("false".equalsIgnoreCase(trimmed)) {
                            params[0] = Boolean.FALSE;
                        } else {
                            ok = false;
                        }

                        // Try a setFoo ( InetAddress )
                    } else if ("java.net.InetAddress".equals(paramType.getName())) {
                        // getByName() documents the empty string as the
                        // loopback address instead of failing, and an empty
                        // host is not a request for loopback: do not invoke
                        // the setter with a fabricated address.
                        if (value.isEmpty()) {
                            ok = false;
                        } else {
                            try {
                                params[0] = InetAddress.getByName(value);
                            } catch (UnknownHostException exc) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Unable to resolve host name: " + value);
                                }
                                ok = false;
                            }
                        }

                        // Unknown type
                    } else {
                        // Unsupported parameter type: the value cannot be
                        // built for it, so the setter is not invoked with a
                        // fabricated null and the property is reported as not
                        // applied.
                        ok = false;
                        if (log.isDebugEnabled()) {
                            log.debug("Unknown type " + paramType.getName());
                        }
                    }

                    if (ok) {
                        methods[i].invoke(o, params);
                        return SetResult.APPLIED;
                    }
                }

                // Save "setProperty" for later, but only one that the two
                // string arguments below can actually be passed to: a
                // setProperty of any other signature is not a (name, value)
                // property bag, and invoking it reflectively with those
                // arguments would only fail with an IllegalArgumentException
                // that masks the "property was not applied" report.
                if ("setProperty".equals(methods[i].getName())) {
                    Class<?>[] paramT = methods[i].getParameterTypes();
                    if (paramT.length == 2 && paramT[0].isAssignableFrom(String.class)
                            && paramT[1].isAssignableFrom(String.class)) {
                        // A boxed Boolean bag refuses a property the same
                        // way a primitive boolean one does: it belongs in
                        // the bucket whose return value is evaluated, not
                        // in the fire-and-forget bucket.
                        if (methods[i].getReturnType() == Boolean.TYPE
                                || methods[i].getReturnType() == Boolean.class) {
                            setPropertyMethodBool = methods[i];
                        } else {
                            setPropertyMethodVoid = methods[i];
                        }
                    }
                }
            }

            // Ok, no setXXX found, try a setProperty("name", "value")
            // A setXXX matched but no supported overload converted the value:
            // consulting the bag here would silently absorb the value the
            // bean's own setter rejected and report success, leaving the real
            // bean property at its default - a partially configured resource.
            if (setPropertyMethodBool != null || setPropertyMethodVoid != null) {
                if (typedSetterMatched) {
                    return SetResult.UNREPORTED;
                }
                return applyPropertyBag(o, name, value,
                        setPropertyMethodBool, setPropertyMethodVoid);
            }

        } catch (IllegalArgumentException ex2) {
            log.warn(MessageFactory.get("genericNamingResourcesFactory.unableToSetProperty",
                    name, o.getClass().getName()), ex2);
            return SetResult.REPORTED;
        } catch (SecurityException ex1) {
            log.warn(MessageFactory.get("genericNamingResourcesFactory.securityExceptionSettingProperty",
                    name, o.getClass().getName()), ex1);
            return SetResult.REPORTED;
        } catch (IllegalAccessException iae) {
            log.warn(MessageFactory.get("genericNamingResourcesFactory.illegalAccessExceptionSettingProperty",
                    name, o.getClass().getName()), iae);
            return SetResult.REPORTED;
        } catch (InvocationTargetException ie) {
            // ExceptionUtils.handleThrowable() propagates the fatal
            // VirtualMachineError from the setter and accepts everything
            // else for reporting. The configured value is deliberately not
            // echoed: a failing password setter would otherwise write the
            // secret to the log.
            Throwable cause = ie.getCause();
            ExceptionUtils.handleThrowable(cause);
            log.warn(MessageFactory.get("genericNamingResourcesFactory.setterThrew",
                    name, o.getClass().getName()), ie);
            return SetResult.REPORTED;
        }
        return SetResult.UNREPORTED;
    }

    /**
     * Apply a configured property through the setProperty(name, value)
     * fallback. Called from inside setProperty's try, so every exception the
     * bags can raise - including the failure of the defensive fallback
     * below - reaches the caller's typed handlers and is reported as a
     * failed property, instead of escaping unconverted from within a catch
     * block the outer handlers cannot guard.
     */
    @SuppressWarnings("null") // voidBag can't be null where it is used
    private static SetResult applyPropertyBag(Object o, String name,
            String value, Method boolBag, Method voidBag)
            throws IllegalAccessException, InvocationTargetException {
        Object[] params = new Object[2];
        params[0] = name;
        params[1] = value;
        if (boolBag != null) {
            try {
                return Boolean.TRUE.equals(boolBag.invoke(o, params))
                        ? SetResult.APPLIED : SetResult.UNREPORTED;
            } catch (IllegalArgumentException biae) {
                // Defensive: the recorded method's parameter types were
                // checked when it was saved, so invoking it with the name
                // and value should not fail on the arguments; if it somehow
                // does, fall back to the other method.
                if (voidBag == null) {
                    throw biae;
                }
            }
        }
        // Reached with a usable void bag, or after the boolean bag refused
        // to run on the very arguments it was recorded for.
        voidBag.invoke(o, params);
        return SetResult.APPLIED;
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
