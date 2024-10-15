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
package jakarta.el;

import java.lang.ref.WeakReference;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class Util {

    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    /**
     * Checks whether the supplied Throwable is one that needs to be rethrown and swallows all others.
     *
     * @param t the Throwable to check
     */
    static void handleThrowable(Throwable t) {
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
        ResourceBundle bundle = ResourceBundle.getBundle("jakarta.el.LocalStrings", locale);
        try {
            String template = bundle.getString(name);
            if (props != null) {
                template = MessageFormat.format(template, props);
            }
            return template;
        } catch (MissingResourceException e) {
            return "Missing Resource: '" + name + "' for Locale " + locale.getDisplayName();
        }
    }


    private static final CacheValue nullTcclFactory = new CacheValue();
    private static final Map<CacheKey,CacheValue> factoryCache = new ConcurrentHashMap<>();

    /**
     * Provides a per class loader cache of ExpressionFactory instances without pinning any in memory as that could
     * trigger a memory leak.
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
            writeLock.lock();
            try {
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
     * Key used to cache default ExpressionFactory information per class loader. The class loader reference is never
     * {@code null}, because {@code null} tccl is handled separately.
     */
    private static class CacheKey {
        private final int hash;
        private final WeakReference<ClassLoader> ref;

        CacheKey(ClassLoader key) {
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

        CacheValue() {
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


    /*
     * This method duplicates code in org.apache.el.util.ReflectionUtil. When making changes keep the code in sync.
     */
    static Method findMethod(ELContext context, Class<?> clazz, Object base, String methodName, Class<?>[] paramTypes,
            Object[] paramValues) {

        if (clazz == null || methodName == null) {
            throw new MethodNotFoundException(
                    message(null, "util.method.notfound", clazz, methodName, paramString(paramTypes)));
        }

        if (paramTypes == null) {
            paramTypes = getTypesFromValues(paramValues);
        }

        Method[] methods = clazz.getMethods();

        List<Wrapper<Method>> wrappers = Wrapper.wrap(methods, methodName);

        Wrapper<Method> result = findWrapper(context, clazz, wrappers, methodName, paramTypes, paramValues);

        return getMethod(clazz, base, result.unWrap());
    }

    /*
     * This method duplicates code in org.apache.el.util.ReflectionUtil. When making changes keep the code in sync.
     */
    @SuppressWarnings("null")
    private static <T> Wrapper<T> findWrapper(ELContext context, Class<?> clazz, List<Wrapper<T>> wrappers, String name,
            Class<?>[] paramTypes, Object[] paramValues) {

        Map<Wrapper<T>,MatchResult> candidates = new HashMap<>();

        int paramCount = paramTypes.length;

        for (Wrapper<T> w : wrappers) {
            Class<?>[] mParamTypes = w.getParameterTypes();
            int mParamCount;
            if (mParamTypes == null) {
                mParamCount = 0;
            } else {
                mParamCount = mParamTypes.length;
            }

            // Check the number of parameters
            // Multiple tests to improve readability
            if (!w.isVarArgs() && paramCount != mParamCount) {
                // Method has wrong number of parameters
                continue;
            }
            if (w.isVarArgs() && paramCount < mParamCount - 1) {
                // Method has wrong number of parameters
                continue;
            }
            if (w.isVarArgs() && paramCount == mParamCount && paramValues != null && paramValues.length > paramCount &&
                    !paramTypes[mParamCount - 1].isArray()) {
                // Method arguments don't match
                continue;
            }
            if (w.isVarArgs() && paramCount > mParamCount && paramValues != null && paramValues.length != paramCount) {
                // Might match a different varargs method
                continue;
            }
            if (!w.isVarArgs() && paramValues != null && paramCount != paramValues.length) {
                // Might match a different varargs method
                continue;
            }

            // Check the parameters match
            int exactMatch = 0;
            int assignableMatch = 0;
            int coercibleMatch = 0;
            int varArgsMatch = 0;
            boolean noMatch = false;
            for (int i = 0; i < mParamCount; i++) {
                // Can't be null
                if (w.isVarArgs() && i == (mParamCount - 1)) {
                    if (i == paramCount || (paramValues != null && paramValues.length == i)) {
                        // Var args defined but nothing is passed as varargs
                        // Use MAX_VALUE so this matches only if nothing else does
                        varArgsMatch = Integer.MAX_VALUE;
                        break;
                    }
                    Class<?> varType = mParamTypes[i].getComponentType();
                    for (int j = i; j < paramCount; j++) {
                        if (isAssignableFrom(paramTypes[j], varType)) {
                            assignableMatch++;
                            varArgsMatch++;
                        } else {
                            if (paramValues == null) {
                                noMatch = true;
                                break;
                            } else {
                                if (isCoercibleFrom(context, paramValues[j], varType)) {
                                    coercibleMatch++;
                                    varArgsMatch++;
                                } else {
                                    noMatch = true;
                                    break;
                                }
                            }
                        }
                        // Don't treat a varArgs match as an exact match, it can
                        // lead to a varArgs method matching when the result
                        // should be ambiguous
                    }
                } else {
                    if (mParamTypes[i].equals(paramTypes[i])) {
                        exactMatch++;
                    } else if (paramTypes[i] != null && isAssignableFrom(paramTypes[i], mParamTypes[i])) {
                        assignableMatch++;
                    } else {
                        if (paramValues == null) {
                            noMatch = true;
                            break;
                        } else {
                            if (isCoercibleFrom(context, paramValues[i], mParamTypes[i])) {
                                coercibleMatch++;
                            } else {
                                noMatch = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (noMatch) {
                continue;
            }

            // If a method is found where every parameter matches exactly,
            // and no vars args are present, return it
            if (exactMatch == paramCount && varArgsMatch == 0) {
                return w;
            }

            candidates.put(w, new MatchResult(w.isVarArgs(), exactMatch, assignableMatch, coercibleMatch, varArgsMatch,
                    w.isBridge()));
        }

        // Look for the method that has the highest number of parameters where
        // the type matches exactly
        MatchResult bestMatch = new MatchResult(true, 0, 0, 0, 0, true);
        Wrapper<T> match = null;
        boolean multiple = false;
        for (Map.Entry<Wrapper<T>,MatchResult> entry : candidates.entrySet()) {
            int cmp = entry.getValue().compareTo(bestMatch);
            if (cmp > 0 || match == null) {
                bestMatch = entry.getValue();
                match = entry.getKey();
                multiple = false;
            } else if (cmp == 0) {
                multiple = true;
            }
        }
        if (multiple) {
            if (bestMatch.getExactCount() == paramCount - 1) {
                // Only one parameter is not an exact match - try using the
                // super class
                match = resolveAmbiguousWrapper(candidates.keySet(), paramTypes);
            } else {
                match = null;
            }

            if (match == null) {
                // If multiple methods have the same matching number of parameters
                // the match is ambiguous so throw an exception
                throw new MethodNotFoundException(
                        message(null, "util.method.ambiguous", clazz, name, paramString(paramTypes)));
            }
        }

        // Handle case where no match at all was found
        if (match == null) {
            throw new MethodNotFoundException(
                    message(null, "util.method.notfound", clazz, name, paramString(paramTypes)));
        }

        return match;
    }


    private static String paramString(Class<?>[] types) {
        if (types != null) {
            StringBuilder sb = new StringBuilder();
            for (Class<?> type : types) {
                if (type == null) {
                    sb.append("null, ");
                } else {
                    sb.append(type.getName()).append(", ");
                }
            }
            if (sb.length() > 2) {
                sb.setLength(sb.length() - 2);
            }
            return sb.toString();
        }
        return null;
    }


    /*
     * This method duplicates code in org.apache.el.util.ReflectionUtil. When making changes keep the code in sync.
     */
    private static <T> Wrapper<T> resolveAmbiguousWrapper(Set<Wrapper<T>> candidates, Class<?>[] paramTypes) {
        // Identify which parameter isn't an exact match
        Wrapper<T> w = candidates.iterator().next();

        int nonMatchIndex = 0;
        Class<?> nonMatchClass = null;

        for (int i = 0; i < paramTypes.length; i++) {
            if (w.getParameterTypes()[i] != paramTypes[i]) {
                nonMatchIndex = i;
                nonMatchClass = paramTypes[i];
                break;
            }
        }

        if (nonMatchClass == null) {
            // Null will always be ambiguous
            return null;
        }

        for (Wrapper<T> c : candidates) {
            if (c.getParameterTypes()[nonMatchIndex] == paramTypes[nonMatchIndex]) {
                // Methods have different non-matching parameters
                // Result is ambiguous
                return null;
            }
        }

        // Can't be null
        Class<?> superClass = nonMatchClass.getSuperclass();
        while (superClass != null) {
            for (Wrapper<T> c : candidates) {
                if (c.getParameterTypes()[nonMatchIndex].equals(superClass)) {
                    // Found a match
                    return c;
                }
            }
            superClass = superClass.getSuperclass();
        }

        // Treat instances of Number as a special case
        Wrapper<T> match = null;
        if (Number.class.isAssignableFrom(nonMatchClass)) {
            for (Wrapper<T> c : candidates) {
                Class<?> candidateType = c.getParameterTypes()[nonMatchIndex];
                if (Number.class.isAssignableFrom(candidateType) || candidateType.isPrimitive()) {
                    if (match == null) {
                        match = c;
                    } else {
                        // Match still ambiguous
                        match = null;
                        break;
                    }
                }
            }
        }

        return match;
    }


    /*
     * This method duplicates code in org.apache.el.util.ReflectionUtil. When making changes keep the code in sync.
     */
    static boolean isAssignableFrom(Class<?> src, Class<?> target) {
        // src will always be an object
        // Short-cut. null is always assignable to an object and in EL null
        // can always be coerced to a valid value for a primitive
        if (src == null) {
            return true;
        }

        Class<?> targetClass;
        if (target.isPrimitive()) {
            if (target == Boolean.TYPE) {
                targetClass = Boolean.class;
            } else if (target == Character.TYPE) {
                targetClass = Character.class;
            } else if (target == Byte.TYPE) {
                targetClass = Byte.class;
            } else if (target == Short.TYPE) {
                targetClass = Short.class;
            } else if (target == Integer.TYPE) {
                targetClass = Integer.class;
            } else if (target == Long.TYPE) {
                targetClass = Long.class;
            } else if (target == Float.TYPE) {
                targetClass = Float.class;
            } else {
                targetClass = Double.class;
            }
        } else {
            targetClass = target;
        }
        return targetClass.isAssignableFrom(src);
    }


    /*
     * This method duplicates code in org.apache.el.util.ReflectionUtil. When making changes keep the code in sync.
     */
    private static boolean isCoercibleFrom(ELContext context, Object src, Class<?> target) {
        /*
         * TODO: This isn't pretty but it works. Significant refactoring would be required to avoid the exception. See
         * also OptionalELResolver.convertToType().
         */
        try {
            context.convertToType(src, target);
        } catch (ELException e) {
            return false;
        }
        return true;
    }


    private static Class<?>[] getTypesFromValues(Object[] values) {
        if (values == null) {
            return EMPTY_CLASS_ARRAY;
        }

        Class<?> result[] = new Class<?>[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                result[i] = null;
            } else {
                result[i] = values[i].getClass();
            }
        }
        return result;
    }


    /*
     * This method duplicates code in org.apache.el.util.ReflectionUtil. When making changes keep the code in sync.
     */
    static Method getMethod(Class<?> type, Object base, Method m) {
        if (m == null || (Modifier.isPublic(type.getModifiers()) &&
                (Modifier.isStatic(m.getModifiers()) && canAccess(null, m) || canAccess(base, m)))) {
            return m;
        }
        Class<?>[] interfaces = type.getInterfaces();
        Method mp = null;
        for (Class<?> iface : interfaces) {
            try {
                mp = iface.getMethod(m.getName(), m.getParameterTypes());
                mp = getMethod(mp.getDeclaringClass(), base, mp);
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
                mp = getMethod(mp.getDeclaringClass(), base, mp);
                if (mp != null) {
                    return mp;
                }
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        }
        return null;
    }


    static Constructor<?> findConstructor(ELContext context, Class<?> clazz, Class<?>[] paramTypes,
            Object[] paramValues) {

        String methodName = "<init>";

        if (clazz == null) {
            throw new MethodNotFoundException(
                    message(null, "util.method.notfound", null, methodName, paramString(paramTypes)));
        }

        if (paramTypes == null) {
            paramTypes = getTypesFromValues(paramValues);
        }

        Constructor<?>[] constructors = clazz.getConstructors();

        List<Wrapper<Constructor<?>>> wrappers = Wrapper.wrap(constructors);

        Wrapper<Constructor<?>> wrapper = findWrapper(context, clazz, wrappers, methodName, paramTypes, paramValues);

        Constructor<?> constructor = wrapper.unWrap();

        if (!Modifier.isPublic(clazz.getModifiers()) || !canAccess(null, constructor)) {
            throw new MethodNotFoundException(
                    message(null, "util.method.notfound", clazz, methodName, paramString(paramTypes)));
        }

        return constructor;
    }


    static boolean canAccess(Object base, AccessibleObject accessibleObject) {
        try {
            return accessibleObject.canAccess(base);
        } catch (IllegalArgumentException iae) {
            return false;
        }
    }


    static Object[] buildParameters(ELContext context, Class<?>[] parameterTypes, boolean isVarArgs, Object[] params) {
        Object[] parameters = null;
        if (parameterTypes.length > 0) {
            parameters = new Object[parameterTypes.length];
            int paramCount;
            if (params == null) {
                params = EMPTY_OBJECT_ARRAY;
            }
            paramCount = params.length;
            if (isVarArgs) {
                int varArgIndex = parameterTypes.length - 1;
                // First argCount-1 parameters are standard
                for (int i = 0; (i < varArgIndex); i++) {
                    parameters[i] = context.convertToType(params[i], parameterTypes[i]);
                }
                // Last parameter is the varargs
                Class<?> varArgClass = parameterTypes[varArgIndex].getComponentType();
                final Object varargs = Array.newInstance(varArgClass, (paramCount - varArgIndex));
                for (int i = (varArgIndex); i < paramCount; i++) {
                    Array.set(varargs, i - varArgIndex, context.convertToType(params[i], varArgClass));
                }
                parameters[varArgIndex] = varargs;
            } else {
                parameters = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    parameters[i] = context.convertToType(params[i], parameterTypes[i]);
                }
            }
        }
        return parameters;
    }


    private abstract static class Wrapper<T> {

        public static List<Wrapper<Method>> wrap(Method[] methods, String name) {
            List<Wrapper<Method>> result = new ArrayList<>();
            for (Method method : methods) {
                if (method.getName().equals(name)) {
                    result.add(new MethodWrapper(method));
                }
            }
            return result;
        }

        public static List<Wrapper<Constructor<?>>> wrap(Constructor<?>[] constructors) {
            List<Wrapper<Constructor<?>>> result = new ArrayList<>();
            for (Constructor<?> constructor : constructors) {
                result.add(new ConstructorWrapper(constructor));
            }
            return result;
        }

        public abstract T unWrap();

        public abstract Class<?>[] getParameterTypes();

        public abstract boolean isVarArgs();

        public abstract boolean isBridge();
    }


    private static class MethodWrapper extends Wrapper<Method> {
        private final Method m;

        MethodWrapper(Method m) {
            this.m = m;
        }

        @Override
        public Method unWrap() {
            return m;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return m.getParameterTypes();
        }

        @Override
        public boolean isVarArgs() {
            return m.isVarArgs();
        }

        @Override
        public boolean isBridge() {
            return m.isBridge();
        }
    }

    private static class ConstructorWrapper extends Wrapper<Constructor<?>> {
        private final Constructor<?> c;

        ConstructorWrapper(Constructor<?> c) {
            this.c = c;
        }

        @Override
        public Constructor<?> unWrap() {
            return c;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return c.getParameterTypes();
        }

        @Override
        public boolean isVarArgs() {
            return c.isVarArgs();
        }

        @Override
        public boolean isBridge() {
            return false;
        }
    }

    /*
     * This class duplicates code in org.apache.el.util.ReflectionUtil. When making changes keep the code in sync.
     */
    private static class MatchResult implements Comparable<MatchResult> {

        private final boolean varArgs;
        private final int exactCount;
        private final int assignableCount;
        private final int coercibleCount;
        private final int varArgsCount;
        private final boolean bridge;

        MatchResult(boolean varArgs, int exactCount, int assignableCount, int coercibleCount, int varArgsCount,
                boolean bridge) {
            this.varArgs = varArgs;
            this.exactCount = exactCount;
            this.assignableCount = assignableCount;
            this.coercibleCount = coercibleCount;
            this.varArgsCount = varArgsCount;
            this.bridge = bridge;
        }

        public boolean isVarArgs() {
            return varArgs;
        }

        public int getExactCount() {
            return exactCount;
        }

        public int getAssignableCount() {
            return assignableCount;
        }

        public int getCoercibleCount() {
            return coercibleCount;
        }

        public int getVarArgsCount() {
            return varArgsCount;
        }

        public boolean isBridge() {
            return bridge;
        }

        @Override
        public int compareTo(MatchResult o) {
            // Non-varArgs always beats varArgs
            int cmp = Boolean.compare(o.isVarArgs(), this.isVarArgs());
            if (cmp == 0) {
                cmp = Integer.compare(this.getExactCount(), o.getExactCount());
                if (cmp == 0) {
                    cmp = Integer.compare(this.getAssignableCount(), o.getAssignableCount());
                    if (cmp == 0) {
                        cmp = Integer.compare(this.getCoercibleCount(), o.getCoercibleCount());
                        if (cmp == 0) {
                            // Fewer var args matches are better
                            cmp = Integer.compare(o.getVarArgsCount(), this.getVarArgsCount());
                            if (cmp == 0) {
                                // The nature of bridge methods is such that it actually
                                // doesn't matter which one we pick as long as we pick
                                // one. That said, pick the 'right' one (the non-bridge
                                // one) anyway.
                                cmp = Boolean.compare(o.isBridge(), this.isBridge());
                            }
                        }
                    }
                }
            }
            return cmp;
        }

        @Override
        public boolean equals(Object o) {
            return o == this || (null != o && this.getClass().equals(o.getClass()) &&
                    ((MatchResult) o).getExactCount() == this.getExactCount() &&
                    ((MatchResult) o).getAssignableCount() == this.getAssignableCount() &&
                    ((MatchResult) o).getCoercibleCount() == this.getCoercibleCount() &&
                    ((MatchResult) o).getVarArgsCount() == this.getVarArgsCount() &&
                    ((MatchResult) o).isVarArgs() == this.isVarArgs() &&
                    ((MatchResult) o).isBridge() == this.isBridge());
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + assignableCount;
            result = prime * result + (bridge ? 1231 : 1237);
            result = prime * result + coercibleCount;
            result = prime * result + exactCount;
            result = prime * result + (varArgs ? 1231 : 1237);
            result = prime * result + varArgsCount;
            return result;
        }
    }
}
