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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An {@link ELResolver} that resolves properties on {@link java.util.Map} objects using
 * the property as a map key. When the base object is a {@code Map}, the property is used
 * directly as the key to look up or set values. This resolver supports read and write
 * operations, and can be configured as read-only to prevent modification of map entries.
 *
 * @since EL 2.1
 */
public class MapELResolver extends ELResolver {

    private static final Class<?> UNMODIFIABLE = Collections.unmodifiableMap(new HashMap<>()).getClass();

    private final boolean readOnly;

    /**
     * Constructs a MapELResolver that allows both read and write operations on maps.
     */
    public MapELResolver() {
        this.readOnly = false;
    }

    /**
     * Constructs a MapELResolver with the specified read-only setting. When read-only
     * is {@code true}, attempts to set values on map entries will throw a
     * {@link PropertyNotWritableException}.
     *
     * @param readOnly {@code true} if this resolver should be read-only, {@code false} otherwise
     */
    public MapELResolver(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof Map<?,?> map) {
            context.setPropertyResolved(base, property);

            if (readOnly || map.getClass() == UNMODIFIABLE) {
                return null;
            }

            return Object.class;
        }

        return null;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof Map<?,?>) {
            context.setPropertyResolved(base, property);
            return ((Map<?,?>) base).get(property);
        }

        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        Objects.requireNonNull(context);

        if (base instanceof Map<?,?>) {
            context.setPropertyResolved(base, property);

            if (this.readOnly) {
                throw new PropertyNotWritableException(
                        Util.message(context, "resolverNotWritable", base.getClass().getName()));
            }

            try {
                @SuppressWarnings("unchecked") // Must be OK
                Map<Object,Object> map = ((Map<Object,Object>) base);
                map.put(property, value);
            } catch (UnsupportedOperationException e) {
                throw new PropertyNotWritableException(e);
            }
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof Map<?,?>) {
            context.setPropertyResolved(base, property);
            return this.readOnly || UNMODIFIABLE.equals(base.getClass());
        }

        return this.readOnly;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        if (base instanceof Map<?,?>) {
            return Object.class;
        }
        return null;
    }

}
