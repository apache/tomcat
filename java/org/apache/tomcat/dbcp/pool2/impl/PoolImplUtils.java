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
package org.apache.tomcat.dbcp.pool2.impl;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;

/**
 * Implementation specific utilities.
 *
 * @since 2.0
 */
final class PoolImplUtils {

    /**
     * Identifies the concrete type of object that an object factory creates.
     *
     * @param factoryClass The factory to examine
     * @return the type of object the factory creates
     */
    @SuppressWarnings("rawtypes")
    static Class<?> getFactoryType(final Class<? extends PooledObjectFactory> factoryClass) {
        final Class<PooledObjectFactory> type = PooledObjectFactory.class;
        final Object genericType = getGenericType(type, factoryClass);
        if (genericType instanceof Integer) {
            // POOL-324 org.apache.commons.pool2.impl.GenericObjectPool.getFactoryType() throws
            // java.lang.ClassCastException
            //
            // A bit hackish, but we must handle cases when getGenericType() does not return a concrete types.
            final ParameterizedType pi = getParameterizedType(type, factoryClass);
            if (pi != null) {
                final Type[] bounds = ((TypeVariable) pi.getActualTypeArguments()[((Integer) genericType).intValue()])
                        .getBounds();
                if (bounds != null && bounds.length > 0) {
                    final Type bound0 = bounds[0];
                    if (bound0 instanceof Class) {
                        return (Class<?>) bound0;
                    }
                }
            }
            // last resort: Always return a Class
            return Object.class;
        }
        return (Class<?>) genericType;
    }

    /**
     * Gets the concrete type used by an implementation of an interface that uses a generic type.
     *
     * @param type The interface that defines a generic type
     * @param clazz The class that implements the interface with a concrete type
     * @param <T> The interface type
     * @return concrete type used by the implementation
     */
    private static <T> Object getGenericType(final Class<T> type, final Class<? extends T> clazz) {
        if (type == null || clazz == null) {
            // Error will be logged further up the call stack
            return null;
        }

        // Look to see if this class implements the generic interface
        final ParameterizedType pi = getParameterizedType(type, clazz);
        if (pi != null) {
            return getTypeParameter(clazz, pi.getActualTypeArguments()[0]);
        }

        // Interface not found on this class. Look at the superclass.
        @SuppressWarnings("unchecked")
        final Class<? extends T> superClass = (Class<? extends T>) clazz.getSuperclass();

        final Object result = getGenericType(type, superClass);
        if (result instanceof Class<?>) {
            // Superclass implements interface and defines explicit type for generic
            return result;
        }
        if (result instanceof Integer) {
            // Superclass implements interface and defines unknown type for generic
            // Map that unknown type to the generic types defined in this class
            final ParameterizedType superClassType = (ParameterizedType) clazz.getGenericSuperclass();
            return getTypeParameter(clazz, superClassType.getActualTypeArguments()[((Integer) result).intValue()]);
        }
        // Error will be logged further up the call stack
        return null;
    }

    /**
     * Gets the matching parameterized type or null.
     *
     * @param type The interface that defines a generic type.
     * @param clazz The class that implements the interface with a concrete type.
     * @param <T> The interface type.
     * @return the matching parameterized type or null.
     */
    private static <T> ParameterizedType getParameterizedType(final Class<T> type, final Class<? extends T> clazz) {
        for (final Type iface : clazz.getGenericInterfaces()) {
            // Only need to check interfaces that use generics
            if (iface instanceof ParameterizedType) {
                final ParameterizedType pi = (ParameterizedType) iface;
                // Look for the generic interface
                if (pi.getRawType() instanceof Class && type.isAssignableFrom((Class<?>) pi.getRawType())) {
                    return pi;
                }
            }
        }
        return null;
    }

    /**
     * For a generic parameter, return either the Class used or if the type is unknown, the index for the type in
     * definition of the class
     *
     * @param clazz defining class
     * @param argType the type argument of interest
     * @return An instance of {@link Class} representing the type used by the type parameter or an instance of
     *         {@link Integer} representing the index for the type in the definition of the defining class
     */
    private static Object getTypeParameter(final Class<?> clazz, final Type argType) {
        if (argType instanceof Class<?>) {
            return argType;
        }
        final TypeVariable<?>[] tvs = clazz.getTypeParameters();
        for (int i = 0; i < tvs.length; i++) {
            if (tvs[i].equals(argType)) {
                return Integer.valueOf(i);
            }
        }
        return null;
    }

    static boolean isPositive(final Duration delay) {
        return delay != null && !delay.isNegative() && !delay.isZero();
    }

    /**
     * Returns the greater of two {@code Instant} values. That is, the result is the argument closer to the value of
     * {@link Instant#MAX}. If the arguments have the same value, the result is that same value.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the larger of {@code a} and {@code b}.
     */
    static Instant max(final Instant a, final Instant b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    /**
     * Returns the smaller of two {@code Instant} values. That is, the result is the argument closer to the value of
     * {@link Instant#MIN}. If the arguments have the same value, the result is that same value.
     *
     * @param a an argument.
     * @param b another argument.
     * @return the smaller of {@code a} and {@code b}.
     */
    static Instant min(final Instant a, final Instant b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    /**
     * Returns a non-null duration, value if non-null, otherwise defaultValue.
     *
     * @param value May be null.
     * @param defaultValue May not be null/
     * @return value if non-null, otherwise defaultValue.
     */
    static Duration nonNull(final Duration value, final Duration defaultValue) {
        return value != null ? value : Objects.requireNonNull(defaultValue, "defaultValue");
    }

    /**
     * Converts a {@link TimeUnit} to a {@link ChronoUnit}.
     *
     * @param timeUnit A TimeUnit.
     * @return The corresponding ChronoUnit.
     */
    static ChronoUnit toChronoUnit(final TimeUnit timeUnit) {
        // TODO when using Java >= 9: Use TimeUnit.toChronoUnit().
        switch (Objects.requireNonNull(timeUnit)) {
            case NANOSECONDS:
                return ChronoUnit.NANOS;
            case MICROSECONDS:
                return ChronoUnit.MICROS;
            case MILLISECONDS:
                return ChronoUnit.MILLIS;
            case SECONDS:
                return ChronoUnit.SECONDS;
            case MINUTES:
                return ChronoUnit.MINUTES;
            case HOURS:
                return ChronoUnit.HOURS;
            case DAYS:
                return ChronoUnit.DAYS;
            default:
                throw new IllegalArgumentException(timeUnit.toString());
        }
    }

    /**
     * Converts am amount and TimeUnit into a Duration.
     *
     * @param amount the amount of the duration, measured in terms of the unit, positive or negative
     * @param timeUnit the unit that the duration is measured in, must have an exact duration, not null
     * @return a Duration.
     */
    static Duration toDuration(final long amount, final TimeUnit timeUnit) {
        return Duration.of(amount, toChronoUnit(timeUnit));
    }

}
