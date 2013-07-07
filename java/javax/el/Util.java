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
package javax.el;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class Util {

    /**
     * Checks whether the supplied Throwable is one that needs to be
     * rethrown and swallows all others.
     * @param t the Throwable to check
     */
    static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }


    static String message(ELContext context, String name, Object... props) {
        Locale locale = null;
        if (context != null) {
            locale = context.getLocale();
        }
        if (locale == null) {
            locale = Locale.getDefault();
            if (locale == null) {
                return "";
            }
        }
        ResourceBundle bundle = ResourceBundle.getBundle(
                "javax.el.LocalStrings", locale);
        try {
            String template = bundle.getString(name);
            if (props != null) {
                template = MessageFormat.format(template, props);
            }
            return template;
        } catch (MissingResourceException e) {
            return "Missing Resource: '" + name + "' for Locale "
                    + locale.getDisplayName();
        }
    }


    private static final CacheValue nullTcclFactory = new CacheValue();
    private static final ConcurrentMap<CacheKey, CacheValue> factoryCache =
            new ConcurrentHashMap<>();

    /**
     * Provides a per class loader cache of ExpressionFactory instances without
     * pinning any in memory as that could trigger a memory leak.
     */
    static ExpressionFactory getExpressionFactory() {

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        CacheValue cacheValue = null;
        ExpressionFactory factory = null;

        if (tccl == null) {
            cacheValue = nullTcclFactory;
        } else {
            CacheKey key = new CacheKey(tccl);
            cacheValue = factoryCache.get(key);
            if (cacheValue == null) {
                CacheValue newCacheValue = new CacheValue();
                cacheValue = factoryCache.putIfAbsent(key, newCacheValue);
                if (cacheValue == null) {
                    cacheValue = newCacheValue;
                }
            }
        }

        final Lock readLock = cacheValue.getLock().readLock();
        readLock.lock();
        try {
            factory = cacheValue.getExpressionFactory();
        } finally {
            readLock.unlock();
        }

        if (factory == null) {
            final Lock writeLock = cacheValue.getLock().writeLock();
            try {
                writeLock.lock();
                factory = cacheValue.getExpressionFactory();
                if (factory == null) {
                    factory = ExpressionFactory.newInstance();
                    cacheValue.setExpressionFactory(factory);
                }
            } finally {
                writeLock.unlock();
            }
        }

        return factory;
    }


    /**
     * Key used to cache default ExpressionFactory information per class
     * loader. The class loader reference is never {@code null}, because
     * {@code null} tccl is handled separately.
     */
    private static class CacheKey {
        private final int hash;
        private final WeakReference<ClassLoader> ref;

        public CacheKey(ClassLoader key) {
            hash = key.hashCode();
            ref = new WeakReference<>(key);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof CacheKey)) {
                return false;
            }
            ClassLoader thisKey = ref.get();
            if (thisKey == null) {
                return false;
            }
            return thisKey == ((CacheKey) obj).ref.get();
        }
    }

    private static class CacheValue {
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private WeakReference<ExpressionFactory> ref;

        public CacheValue() {
        }

        public ReadWriteLock getLock() {
            return lock;
        }

        public ExpressionFactory getExpressionFactory() {
            return ref != null ? ref.get() : null;
        }

        public void setExpressionFactory(ExpressionFactory factory) {
            ref = new WeakReference<>(factory);
        }
    }


    static Method findMethod(Class<?> clazz, String methodName,
            Class<?>[] paramTypes, Object[] params) {

        Method matchingMethod = null;

        if (paramTypes != null) {
            try {
                matchingMethod =
                    getMethod(clazz, clazz.getMethod(methodName, paramTypes));
            } catch (NoSuchMethodException e) {
                throw new MethodNotFoundException(e);
            }
        } else {
            int paramCount = 0;
            if (params != null) {
                paramCount = params.length;
            }
            Method[] methods = clazz.getMethods();
            for (Method m : methods) {
                if (methodName.equals(m.getName())) {
                    if (m.getParameterTypes().length == paramCount) {
                        // Same number of parameters - use the first match
                        matchingMethod = getMethod(clazz, m);
                        break;
                    }
                    if (m.isVarArgs()
                            && paramCount > m.getParameterTypes().length - 2) {
                        matchingMethod = getMethod(clazz, m);
                    }
                }
            }
            if (matchingMethod == null) {
                throw new MethodNotFoundException(
                        "Unable to find method [" + methodName + "] with ["
                        + paramCount + "] parameters");
            }
        }

        return matchingMethod;
    }


    static Method getMethod(Class<?> type, Method m) {
        if (m == null || Modifier.isPublic(type.getModifiers())) {
            return m;
        }
        Class<?>[] inf = type.getInterfaces();
        Method mp = null;
        for (int i = 0; i < inf.length; i++) {
            try {
                mp = inf[i].getMethod(m.getName(), m.getParameterTypes());
                mp = getMethod(mp.getDeclaringClass(), mp);
                if (mp != null) {
                    return mp;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }
        Class<?> sup = type.getSuperclass();
        if (sup != null) {
            try {
                mp = sup.getMethod(m.getName(), m.getParameterTypes());
                mp = getMethod(mp.getDeclaringClass(), mp);
                if (mp != null) {
                    return mp;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }
        return null;
    }


    static Constructor<?> findConstructor(Class<?> clazz, Class<?>[] paramTypes,
            Object[] params) {

        Constructor<?> match = null;

        if (paramTypes != null) {
            try {
                match = getConstructor(clazz, clazz.getConstructor(paramTypes));
            } catch (NoSuchMethodException e) {
                throw new MethodNotFoundException(e);
            }
        } else {
            int paramCount = 0;
            if (params != null) {
                paramCount = params.length;
            }
            Constructor<?>[] constructors = clazz.getConstructors();
            for (Constructor<?> c : constructors) {
                if (c.getParameterTypes().length == paramCount) {
                    // Same number of parameters - use the first match
                    match = getConstructor(clazz, c);
                    break;
                }
                if (c.isVarArgs()
                        && paramCount > c.getParameterTypes().length - 2) {
                    match = getConstructor(clazz, c);
                }
            }
            if (match == null) {
                throw new MethodNotFoundException(
                        "Unable to find constructor with [" + paramCount +
                        "] parameters");
            }
        }

        return match;
    }


    static Constructor<?> getConstructor(Class<?> type, Constructor<?> c) {
        if (c == null || Modifier.isPublic(type.getModifiers())) {
            return c;
        }
        Constructor<?> cp = null;
        Class<?> sup = type.getSuperclass();
        if (sup != null) {
            try {
                cp = sup.getConstructor(c.getParameterTypes());
                cp = getConstructor(cp.getDeclaringClass(), cp);
                if (cp != null) {
                    return cp;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }
        return null;
    }


    static Object[] buildParameters(Class<?>[] parameterTypes,
            boolean isVarArgs,Object[] params) {
        ExpressionFactory factory = getExpressionFactory();
        Object[] parameters = null;
        if (parameterTypes.length > 0) {
            parameters = new Object[parameterTypes.length];
            int paramCount = params.length;
            if (isVarArgs) {
                int varArgIndex = parameterTypes.length - 1;
                // First argCount-1 parameters are standard
                for (int i = 0; (i < varArgIndex); i++) {
                    parameters[i] = factory.coerceToType(params[i],
                            parameterTypes[i]);
                }
                // Last parameter is the varargs
                Class<?> varArgClass =
                    parameterTypes[varArgIndex].getComponentType();
                final Object varargs = Array.newInstance(
                    varArgClass,
                    (paramCount - varArgIndex));
                for (int i = (varArgIndex); i < paramCount; i++) {
                    Array.set(varargs, i - varArgIndex,
                            factory.coerceToType(params[i], varArgClass));
                }
                parameters[varArgIndex] = varargs;
            } else {
                parameters = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    parameters[i] = factory.coerceToType(params[i],
                            parameterTypes[i]);
                }
            }
        }
        return parameters;
    }
}
