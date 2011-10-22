/*
 */
package org.apache.tomcat.test.watchdog;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Refactoring of IntrospectionUtils and modeler dynamic bean.
 *
 * Unlike IntrospectionUtils, the method informations can be cached.
 * Also I hope this class will be simpler to use.
 * There is no static cache.
 *
 * @author Costin Manolache
 */
public class DynamicObject {
    // Based on MbeansDescriptorsIntrospectionSource

    private static Logger log = Logger.getLogger(DynamicObject.class.getName());

    private static Class<?> NO_PARAMS[] = new Class[0];


    private static String strArray[] = new String[0];

    private static Class<?>[] supportedTypes = new Class[] { Boolean.class,
            Boolean.TYPE, Byte.class, Byte.TYPE, Character.class,
            Character.TYPE, Short.class, Short.TYPE, Integer.class,
            Integer.TYPE, Long.class, Long.TYPE, Float.class, Float.TYPE,
            Double.class, Double.TYPE, String.class, strArray.getClass(),
            BigDecimal.class, BigInteger.class, AtomicInteger.class,
            AtomicLong.class, java.io.File.class, };


    private Class realClass;

    // Method or Field
    private Map<String, AccessibleObject> getAttMap;

    public DynamicObject(Class beanClass) {
        this.realClass = beanClass;
        initCache();
    }

    private void initCache() {
        Method methods[] = null;

        getAttMap = new HashMap<String, AccessibleObject>();

        methods = realClass.getMethods();
        for (int j = 0; j < methods.length; ++j) {
            if (ignorable(methods[j])) {
                continue;
            }
            String name = methods[j].getName();

            Class<?> params[] = methods[j].getParameterTypes();

            if (name.startsWith("get") && params.length == 0) {
                Class<?> ret = methods[j].getReturnType();
                if (!supportedType(ret)) {
                    if (log.isLoggable(Level.FINE))
                        log.fine("Unsupported type " + methods[j]);
                    continue;
                }
                name = unCapitalize(name.substring(3));

                getAttMap.put(name, methods[j]);
            } else if (name.startsWith("is") && params.length == 0) {
                Class<?> ret = methods[j].getReturnType();
                if (Boolean.TYPE != ret) {
                    if (log.isLoggable(Level.FINE))
                        log.fine("Unsupported type " + methods[j] + " " + ret);
                    continue;
                }
                name = unCapitalize(name.substring(2));

                getAttMap.put(name, methods[j]);
            }
        }
        // non-private AtomicInteger and AtomicLong - stats
        Field fields[] = realClass.getFields();
        for (int j = 0; j < fields.length; ++j) {
            if (fields[j].getType() == AtomicInteger.class) {
                getAttMap.put(fields[j].getName(), fields[j]);
            }
        }

    }

    public List<String> attributeNames() {
        return new ArrayList<String>(getAttMap.keySet());
    }


    public Object invoke(Object proxy, String method) throws Exception {
        Method executeM = null;
        Class<?> c = proxy.getClass();
        executeM = c.getMethod(method, NO_PARAMS);
        if (executeM == null) {
            throw new RuntimeException("No execute in " + proxy.getClass());
        }
        return executeM.invoke(proxy, (Object[]) null);
    }

    // TODO
//    public Object invoke(String method, Object[] params) {
//        return null;
//    }

    public Object getAttribute(Object o, String att) {
        AccessibleObject m = getAttMap.get(att);
        if (m instanceof Method) {
            try {
                return ((Method) m).invoke(o);
            } catch (Throwable e) {
                log.log(Level.INFO, "Error getting attribute " + realClass + " "
                        + att, e);
                return null;
            }
        } if (m instanceof Field) {
            if (((Field) m).getType() == AtomicInteger.class) {
                try {
                    Object value = ((Field) m).get(o);
                    return ((AtomicInteger) value).get();
                } catch (Throwable e) {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Set an object-type attribute.
     *
     * Use setProperty to use a string value and convert it to the
     * specific (primitive) type.
     */
    public boolean setAttribute(Object proxy, String name, Object value) {
        // TODO: use the cache...
        String methodName = "set" + capitalize(name);
        Method[] methods = proxy.getClass().getMethods();
        for (Method m : methods) {
            Class<?>[] paramT = m.getParameterTypes();
            if (methodName.equals(m.getName())
                    && paramT.length == 1
                    && (value == null || paramT[0].isAssignableFrom(value
                            .getClass()))) {
                try {
                    m.invoke(proxy, value);
                    return true;
                } catch (IllegalArgumentException e) {
                    log.severe("Error setting: " + name + " "
                            + proxy.getClass().getName() + " " + e);
                } catch (IllegalAccessException e) {
                    log.severe("Error setting: " + name + " "
                            + proxy.getClass().getName() + " " + e);
                } catch (InvocationTargetException e) {
                    log.severe("Error setting: " + name + " "
                            + proxy.getClass().getName() + " " + e);
                }
            }
        }
        return false;
    }

    public boolean setProperty(Object proxy, String name, String value) {
        // TODO: use the cache...
        String setter = "set" + capitalize(name);

        try {
            Method methods[] = proxy.getClass().getMethods();

            Method setPropertyMethod = null;

            // First, the ideal case - a setFoo( String ) method
            for (int i = 0; i < methods.length; i++) {
                if (ignorable(methods[i])) {
                    continue;
                }
                Class<?> paramT[] = methods[i].getParameterTypes();
                if (setter.equals(methods[i].getName()) && paramT.length == 1) {
                    if ("java.lang.String".equals(paramT[0].getName())) {
                        methods[i].invoke(proxy, new Object[] { value });
                        return true;
                    } else {
                        // match - find the type and invoke it
                        Class<?> paramType = methods[i].getParameterTypes()[0];
                        Object params[] = new Object[1];
                        params[0] = convert(value, paramType);
                        if (params[0] != null) {
                            methods[i].invoke(proxy, params);
                            return true;
                        }
                    }
                }
                // save "setProperty" for later
                if ("setProperty".equals(methods[i].getName()) &&
                        paramT.length == 2 &&
                        paramT[0] == String.class &&
                        paramT[1] == String.class) {
                    setPropertyMethod = methods[i];
                }
            }

            try {
                Field field = proxy.getClass().getField(name);
                if (field != null) {
                    Object conv = convert(value, field.getType());
                    if (conv != null) {
                        field.set(proxy, conv);
                        return true;
                    }
                }
            } catch (NoSuchFieldException e) {
                // ignore
            }

            // Ok, no setXXX found, try a setProperty("name", "value")
            if (setPropertyMethod != null) {
                Object params[] = new Object[2];
                params[0] = name;
                params[1] = value;
                setPropertyMethod.invoke(proxy, params);
                return true;
            }

        } catch (Throwable ex2) {
            log.log(Level.WARNING, "IAE " + proxy + " " + name + " " + value,
                    ex2);
        }
        return false;
    }

    // ----------- Helpers ------------------

    static Object convert(String object, Class<?> paramType) {
        Object result = null;
        if ("java.lang.String".equals(paramType.getName())) {
            result = object;
        } else if ("java.lang.Long".equals(paramType.getName())
                || "long".equals(paramType.getName())) {
            try {
                result = Long.parseLong(object);
            } catch (NumberFormatException ex) {
            }
            // Try a setFoo ( boolean )
        } else if ("java.lang.Integer".equals(paramType.getName())
                || "int".equals(paramType.getName())) {
            try {
                result = new Integer(object);
            } catch (NumberFormatException ex) {
            }
            // Try a setFoo ( boolean )
        } else if ("java.lang.Boolean".equals(paramType.getName())
                || "boolean".equals(paramType.getName())) {
            result = new Boolean(object);
        } else {
            log.info("Unknown type " + paramType.getName());
        }
        if (result == null) {
            throw new IllegalArgumentException("Can't convert argument: "
                    + object +  " to " + paramType );
        }
        return result;
    }

    /**
     * Converts the first character of the given String into lower-case.
     *
     * @param name
     *            The string to convert
     * @return String
     */
    static String unCapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * Check if this class is one of the supported types. If the class is
     * supported, returns true. Otherwise, returns false.
     *
     * @param ret
     *            The class to check
     * @return boolean True if class is supported
     */
    static boolean supportedType(Class<?> ret) {
        for (int i = 0; i < supportedTypes.length; i++) {
            if (ret == supportedTypes[i]) {
                return true;
            }
        }
        if (isBeanCompatible(ret)) {
            return true;
        }
        return false;
    }

    /**
     * Check if this class conforms to JavaBeans specifications. If the class is
     * conformant, returns true.
     *
     * @param javaType
     *            The class to check
     * @return boolean True if the class is compatible.
     */
    static boolean isBeanCompatible(Class<?> javaType) {
        // Must be a non-primitive and non array
        if (javaType.isArray() || javaType.isPrimitive()) {
            return false;
        }

        // Anything in the java or javax package that
        // does not have a defined mapping is excluded.
        if (javaType.getName().startsWith("java.")
                || javaType.getName().startsWith("javax.")) {
            return false;
        }

        try {
            javaType.getConstructor(new Class[] {});
        } catch (java.lang.NoSuchMethodException e) {
            return false;
        }

        // Make sure superclass is compatible
        Class<?> superClass = javaType.getSuperclass();
        if (superClass != null && superClass != java.lang.Object.class
                && superClass != java.lang.Exception.class
                && superClass != java.lang.Throwable.class) {
            if (!isBeanCompatible(superClass)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reverse of Introspector.decapitalize
     */
    static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    private boolean ignorable(Method method) {
        if (Modifier.isStatic(method.getModifiers()))
            return true;
        if (!Modifier.isPublic(method.getModifiers())) {
            return true;
        }
        if (method.getDeclaringClass() == Object.class)
            return true;
        return false;
    }


}
