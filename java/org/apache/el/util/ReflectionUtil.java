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
package org.apache.el.util;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.el.ELException;
import javax.el.MethodNotFoundException;

import org.apache.el.lang.ELSupport;
import org.apache.el.lang.EvaluationContext;


/**
 * Utilities for Managing Serialization and Reflection
 *
 * @author Jacob Hookom [jacob@hookom.net]
 */
public class ReflectionUtil {

    protected static final String[] PRIMITIVE_NAMES = new String[] { "boolean",
            "byte", "char", "double", "float", "int", "long", "short", "void" };

    protected static final Class<?>[] PRIMITIVES = new Class[] { boolean.class,
            byte.class, char.class, double.class, float.class, int.class,
            long.class, short.class, Void.TYPE };

    private ReflectionUtil() {
        super();
    }

    public static Class<?> forName(String name) throws ClassNotFoundException {
        if (null == name || name.isEmpty()) {
            return null;
        }
        Class<?> c = forNamePrimitive(name);
        if (c == null) {
            if (name.endsWith("[]")) {
                String nc = name.substring(0, name.length() - 2);
                c = Class.forName(nc, true, getContextClassLoader());
                c = Array.newInstance(c, 0).getClass();
            } else {
                c = Class.forName(name, true, getContextClassLoader());
            }
        }
        return c;
    }

    protected static Class<?> forNamePrimitive(String name) {
        if (name.length() <= 8) {
            int p = Arrays.binarySearch(PRIMITIVE_NAMES, name);
            if (p >= 0) {
                return PRIMITIVES[p];
            }
        }
        return null;
    }

    /**
     * Converts an array of Class names to Class types.
     * @param s  The array of class names
     * @return An array of Class instance where the element at index i in the
     *         result is an instance of the class with the name at index i in
     *         the input
     * @throws ClassNotFoundException If a class of a given name cannot be found
     */
    public static Class<?>[] toTypeArray(String[] s) throws ClassNotFoundException {
        if (s == null) {
            return null;
        }
        Class<?>[] c = new Class[s.length];
        for (int i = 0; i < s.length; i++) {
            c[i] = forName(s[i]);
        }
        return c;
    }

    /**
     * Converts an array of Class types to Class names.
     * @param c The array of class instances
     * @return An array of Class names where the element at index i in the
     *         result is the name of the class instance at index i in the input
     */
    public static String[] toTypeNameArray(Class<?>[] c) {
        if (c == null) {
            return null;
        }
        String[] s = new String[c.length];
        for (int i = 0; i < c.length; i++) {
            s[i] = c[i].getName();
        }
        return s;
    }

    /**
     * Returns a method based on the criteria.
     * @param ctx the context in which the expression is being evaluated
     * @param base the object that owns the method
     * @param property the name of the method
     * @param paramTypes the parameter types to use
     * @param paramValues the parameter values
     * @return the method specified
     * @throws MethodNotFoundException If a method cannot be found that matches
     *         the given criteria
     */
    /*
     * This class duplicates code in javax.el.Util. When making changes keep
     * the code in sync.
     */
    @SuppressWarnings("null")
    public static Method getMethod(EvaluationContext ctx, Object base, Object property,
            Class<?>[] paramTypes, Object[] paramValues)
            throws MethodNotFoundException {

        if (base == null || property == null) {
            throw new MethodNotFoundException(MessageFactory.get(
                    "error.method.notfound", base, property,
                    paramString(paramTypes)));
        }

        String methodName = (property instanceof String) ? (String) property
                : property.toString();

        int paramCount;
        if (paramTypes == null) {
            paramCount = 0;
        } else {
            paramCount = paramTypes.length;
        }

        Method[] methods = base.getClass().getMethods();
        Map<Method,MatchResult> candidates = new HashMap<>();

        for (Method m : methods) {
            if (!m.getName().equals(methodName)) {
                // Method name doesn't match
                continue;
            }

            Class<?>[] mParamTypes = m.getParameterTypes();
            int mParamCount = mParamTypes.length;

            // Check the number of parameters
            // Multiple tests to improve readability
            if (!m.isVarArgs() && paramCount != mParamCount) {
                // Method has wrong number of parameters
                continue;
            }
            if (m.isVarArgs() && paramCount < mParamCount -1) {
                // Method has wrong number of parameters
                continue;
            }
            if (m.isVarArgs() && paramCount == mParamCount && paramValues != null &&
                    paramValues.length > paramCount && !paramTypes[mParamCount -1].isArray()) {
                // Method arguments don't match
                continue;
            }
            if (m.isVarArgs() && paramCount > mParamCount && paramValues != null &&
                    paramValues.length != paramCount) {
                // Might match a different varargs method
                continue;
            }
            if (!m.isVarArgs() && paramValues != null && paramCount != paramValues.length) {
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
                if (m.isVarArgs() && i == (mParamCount - 1)) {
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
                                if (isCoercibleFrom(ctx, paramValues[j], varType)) {
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
                            if (isCoercibleFrom(ctx, paramValues[i], mParamTypes[i])) {
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
                return getMethod(base.getClass(), base, m);
            }

            candidates.put(m, new MatchResult(exactMatch, assignableMatch, coercibleMatch, varArgsMatch, m.isBridge()));
        }

        // Look for the method that has the highest number of parameters where
        // the type matches exactly
        MatchResult bestMatch = new MatchResult(0, 0, 0, 0, false);
        Method match = null;
        boolean multiple = false;
        for (Map.Entry<Method, MatchResult> entry : candidates.entrySet()) {
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
            if (bestMatch.getExact() == paramCount - 1) {
                // Only one parameter is not an exact match - try using the
                // super class
                match = resolveAmbiguousMethod(candidates.keySet(), paramTypes);
            } else {
                match = null;
            }

            if (match == null) {
                // If multiple methods have the same matching number of parameters
                // the match is ambiguous so throw an exception
                throw new MethodNotFoundException(MessageFactory.get(
                        "error.method.ambiguous", base, property,
                        paramString(paramTypes)));
                }
        }

        // Handle case where no match at all was found
        if (match == null) {
            throw new MethodNotFoundException(MessageFactory.get(
                        "error.method.notfound", base, property,
                        paramString(paramTypes)));
        }

        return getMethod(base.getClass(), base, match);
    }

    /*
     * This class duplicates code in javax.el.Util. When making changes keep
     * the code in sync.
     */
    private static Method resolveAmbiguousMethod(Set<Method> candidates,
            Class<?>[] paramTypes) {
        // Identify which parameter isn't an exact match
        Method m = candidates.iterator().next();

        int nonMatchIndex = 0;
        Class<?> nonMatchClass = null;

        for (int i = 0; i < paramTypes.length; i++) {
            if (m.getParameterTypes()[i] != paramTypes[i]) {
                nonMatchIndex = i;
                nonMatchClass = paramTypes[i];
                break;
            }
        }

        if (nonMatchClass == null) {
            // Null will always be ambiguous
            return null;
        }

        for (Method c : candidates) {
           if (c.getParameterTypes()[nonMatchIndex] ==
                   paramTypes[nonMatchIndex]) {
               // Methods have different non-matching parameters
               // Result is ambiguous
               return null;
           }
        }

        // Can't be null
        Class<?> superClass = nonMatchClass.getSuperclass();
        while (superClass != null) {
            for (Method c : candidates) {
                if (c.getParameterTypes()[nonMatchIndex].equals(superClass)) {
                    // Found a match
                    return c;
                }
            }
            superClass = superClass.getSuperclass();
        }

        // Treat instances of Number as a special case
        Method match = null;
        if (Number.class.isAssignableFrom(nonMatchClass)) {
            for (Method c : candidates) {
                Class<?> candidateType = c.getParameterTypes()[nonMatchIndex];
                if (Number.class.isAssignableFrom(candidateType) ||
                        candidateType.isPrimitive()) {
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
     * This class duplicates code in javax.el.Util. When making changes keep
     * the code in sync.
     */
    private static boolean isAssignableFrom(Class<?> src, Class<?> target) {
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
     * This class duplicates code in javax.el.Util. When making changes keep
     * the code in sync.
     */
    private static boolean isCoercibleFrom(EvaluationContext ctx, Object src, Class<?> target) {
        // TODO: This isn't pretty but it works. Significant refactoring would
        //       be required to avoid the exception.
        try {
            ELSupport.coerceToType(ctx, src, target);
        } catch (ELException e) {
            return false;
        }
        return true;
    }


    /*
     * This class duplicates code in javax.el.Util. When making changes keep
     * the code in sync.
     */
    private static Method getMethod(Class<?> type, Object base, Method m) {
        JreCompat jreCompat = JreCompat.getInstance();
        // If base is null, method MUST be static
        // If base is non-null, method may be static or non-static
        if (m == null ||
                (Modifier.isPublic(type.getModifiers()) &&
                        (jreCompat.canAccess(base, m) || base != null && jreCompat.canAccess(null, m)))) {
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


    private static final String paramString(Class<?>[] types) {
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


    private static ClassLoader getContextClassLoader() {
        ClassLoader tccl;
        if (System.getSecurityManager() != null) {
            PrivilegedAction<ClassLoader> pa = new PrivilegedGetTccl();
            tccl = AccessController.doPrivileged(pa);
        } else {
            tccl = Thread.currentThread().getContextClassLoader();
        }

        return tccl;
    }


    private static class PrivilegedGetTccl implements PrivilegedAction<ClassLoader> {
        @Override
        public ClassLoader run() {
            return Thread.currentThread().getContextClassLoader();
        }
    }


    /*
     * This class duplicates code in javax.el.Util. When making changes keep
     * the code in sync.
     */
    private static class MatchResult implements Comparable<MatchResult> {

        private final int exact;
        private final int assignable;
        private final int coercible;
        private final int varArgs;
        private final boolean bridge;

        public MatchResult(int exact, int assignable, int coercible, int varArgs, boolean bridge) {
            this.exact = exact;
            this.assignable = assignable;
            this.coercible = coercible;
            this.varArgs = varArgs;
            this.bridge = bridge;
        }

        public int getExact() {
            return exact;
        }

        public int getAssignable() {
            return assignable;
        }

        public int getCoercible() {
            return coercible;
        }

        public int getVarArgs() {
            return varArgs;
        }

        public boolean isBridge() {
            return bridge;
        }

        @Override
        public int compareTo(MatchResult o) {
            int cmp = Integer.compare(this.getExact(), o.getExact());
            if (cmp == 0) {
                cmp = Integer.compare(this.getAssignable(), o.getAssignable());
                if (cmp == 0) {
                    cmp = Integer.compare(this.getCoercible(), o.getCoercible());
                    if (cmp == 0) {
                        // Fewer var args matches are better
                        cmp = Integer.compare(o.getVarArgs(), this.getVarArgs());
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
            return cmp;
        }

        @Override
        public boolean equals(Object o) {
            return o == this || (null != o &&
                    this.getClass().equals(o.getClass()) &&
                    ((MatchResult)o).getExact() == this.getExact() &&
                    ((MatchResult)o).getAssignable() == this.getAssignable() &&
                    ((MatchResult)o).getCoercible() == this.getCoercible() &&
                    ((MatchResult)o).getVarArgs() == this.getVarArgs() &&
                    ((MatchResult)o).isBridge() == this.isBridge());
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + assignable;
            result = prime * result + (bridge ? 1231 : 1237);
            result = prime * result + coercible;
            result = prime * result + exact;
            result = prime * result + varArgs;
            return result;
        }
    }
}
