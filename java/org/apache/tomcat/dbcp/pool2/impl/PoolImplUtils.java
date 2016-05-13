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

import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;

/**
 * Implementation specific utilities.
 *
 * @since 2.0
 */
class PoolImplUtils {

    /**
     * Identifies the concrete type of object that an object factory creates.
     *
     * @param factory The factory to examine
     *
     * @return the type of object the factory creates
     */
    @SuppressWarnings("rawtypes")
    static Class<?> getFactoryType(final Class<? extends PooledObjectFactory> factory) {
        return (Class<?>) getGenericType(PooledObjectFactory.class, factory);
    }


    /**
     * Obtain the concrete type used by an implementation of an interface that
     * uses a generic type.
     *
     * @param type  The interface that defines a generic type
     * @param clazz The class that implements the interface with a concrete type
     * @param <T>   The interface type
     *
     * @return concrete type used by the implementation
     */
    private static <T> Object getGenericType(final Class<T> type,
            final Class<? extends T> clazz) {

        // Look to see if this class implements the generic interface

        // Get all the interfaces
        final Type[] interfaces = clazz.getGenericInterfaces();
        for (final Type iface : interfaces) {
            // Only need to check interfaces that use generics
            if (iface instanceof ParameterizedType) {
                final ParameterizedType pi = (ParameterizedType) iface;
                // Look for the generic interface
                if (pi.getRawType() instanceof Class) {
                    if (type.isAssignableFrom((Class<?>) pi.getRawType())) {
                        return getTypeParameter(
                                clazz, pi.getActualTypeArguments()[0]);
                    }
                }
            }
        }

        // Interface not found on this class. Look at the superclass.
        @SuppressWarnings("unchecked")
        final
        Class<? extends T> superClazz =
                (Class<? extends T>) clazz.getSuperclass();

        final Object result = getGenericType(type, superClazz);
        if (result instanceof Class<?>) {
            // Superclass implements interface and defines explicit type for
            // generic
            return result;
        } else if (result instanceof Integer) {
            // Superclass implements interface and defines unknown type for
            // generic
            // Map that unknown type to the generic types defined in this class
            final ParameterizedType superClassType =
                    (ParameterizedType) clazz.getGenericSuperclass();
            return getTypeParameter(clazz,
                    superClassType.getActualTypeArguments()[
                            ((Integer) result).intValue()]);
        } else {
            // Error will be logged further up the call stack
            return null;
        }
    }


    /**
     * For a generic parameter, return either the Class used or if the type
     * is unknown, the index for the type in definition of the class
     *
     * @param clazz defining class
     * @param argType the type argument of interest
     *
     * @return An instance of {@link Class} representing the type used by the
     *         type parameter or an instance of {@link Integer} representing
     *         the index for the type in the definition of the defining class
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
}
