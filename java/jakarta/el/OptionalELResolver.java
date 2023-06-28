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

import java.util.Objects;
import java.util.Optional;

/**
 * Defines property resolution behaviour on {@link Optional}s.
 * <p>
 * This resolver handles base objects that are instances of {@link Optional}.
 * <p>
 * If the {@link Optional#isEmpty()} is {@code true} for the base object and the property is {@code null} then the
 * resulting value is {@code null}.
 * <p>
 * If the {@link Optional#isEmpty()} is {@code true} for the base object and the property is not {@code null} then the
 * resulting value is the base object (an empty {@link Optional}).
 * <p>
 * If the {@link Optional#isPresent()} is {@code true} for the base object and the property is {@code null} then the
 * resulting value is the result of calling {@link Optional#get()} on the base object.
 * <p>
 * If the {@link Optional#isPresent()} is {@code true} for the base object and the property is not {@code null} then the
 * resulting value is the result of calling {@link ELResolver#getValue(ELContext, Object, Object)} using the
 * {@link ELResolver} obtained from {@link ELContext#getELResolver()} with the following parameters:
 * <ul>
 * <li>The {@link ELContext} is the current context</li>
 * <li>The base object is the result of calling {@link Optional#get()} on the current base object
 * <li>
 * <li>The property object is the current property object</li>
 * </ul>
 * <p>
 * This resolver is always a read-only resolver.
 */
public class OptionalELResolver extends ELResolver {

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof Optional) {
            context.setPropertyResolved(base, property);
            if (((Optional<?>) base).isEmpty()) {
                if (property == null) {
                    return null;
                } else {
                    return base;
                }
            } else {
                if (property == null) {
                    return ((Optional<?>) base).get();
                } else {
                    Object resolvedBase = ((Optional<?>) base).get();
                    return context.getELResolver().getValue(context, resolvedBase, property);
                }
            }
        }

        return null;
    }


    /**
     * {@inheritDoc}
     * <p>
     * If the base object is an {@link Optional} this method always returns {@code null} since instances of this
     * resolver are always read-only.
     */
    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof Optional) {
            context.setPropertyResolved(base, property);
        }

        return null;
    }


    /**
     * {@inheritDoc}
     * <p>
     * If the base object is an {@link Optional} this method always throws a {@link PropertyNotWritableException} since
     * instances of this resolver are always read-only.
     */
    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        Objects.requireNonNull(context);

        if (base instanceof Optional) {
            throw new PropertyNotWritableException(
                    Util.message(context, "resolverNotWritable", base.getClass().getName()));
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * If the base object is an {@link Optional} this method always returns {@code true} since instances of this
     * resolver are always read-only.
     */
    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof Optional) {
            context.setPropertyResolved(base, property);
            return true;
        }

        return false;
    }


    /**
     * {@inheritDoc}
     * <p>
     * If the base object is an {@link Optional} this method always returns {@code Object.class}.
     */
    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        if (base instanceof Optional) {
            return Object.class;
        }

        return null;
    }


    @Override
    public <T> T convertToType(ELContext context, Object obj, Class<T> type) {
        Objects.requireNonNull(context);
        if (obj instanceof Optional) {
            if (((Optional<?>) obj).isPresent()) {
                Object value = ((Optional<?>) obj).get();
                // If the value is assignable to the required type, do so.
                if (type.isAssignableFrom(value.getClass())) {
                    context.setPropertyResolved(true);
                    @SuppressWarnings("unchecked")
                    T result = (T) value;
                    return result;
                }

                try {
                    Object convertedValue = context.convertToType(value, type);
                    context.setPropertyResolved(true);
                    @SuppressWarnings("unchecked")
                    T result = (T) convertedValue;
                    return result;
                } catch (ELException e) {
                    /*
                     * TODO: This isn't pretty but it works. Significant refactoring would be required to avoid the
                     * exception. See also Util.isCoercibleFrom().
                     */
                }
            } else {
                context.setPropertyResolved(true);
                return null;
            }
        }
        return null;
    }
}
