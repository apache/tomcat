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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ListELResolver extends ELResolver {

    private final boolean readOnly;

    // TODO - Handle the Lists created by List.of(). Multiple package private
    //        classes. Java 9 + so a back-port would require JreCompat.
    private static final Class<?> UNMODIFIABLE = Collections.unmodifiableList(new ArrayList<>()).getClass();

    public ListELResolver() {
        this.readOnly = false;
    }

    public ListELResolver(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof List<?>) {
            context.setPropertyResolved(base, property);
            List<?> list = (List<?>) base;
            int idx = coerce(property);
            if (idx < 0 || idx >= list.size()) {
                throw new PropertyNotFoundException(
                        new ArrayIndexOutOfBoundsException(idx).getMessage());
            }

            /*
             * Not perfect as a custom list implementation may be read-only but
             * consistent with isReadOnly().
             */
            if (list.getClass() == UNMODIFIABLE || readOnly) {
                return null;
            }

            return Object.class;
        }

        return null;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof List<?>) {
            context.setPropertyResolved(base, property);
            List<?> list = (List<?>) base;
            int idx = coerce(property);
            if (idx < 0 || idx >= list.size()) {
                return null;
            }
            return list.get(idx);
        }

        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property,
            Object value) {
        Objects.requireNonNull(context);

        if (base instanceof List<?>) {
            context.setPropertyResolved(base, property);
            @SuppressWarnings("unchecked") // Must be OK to cast to Object
            List<Object> list = (List<Object>) base;

            if (this.readOnly) {
                throw new PropertyNotWritableException(Util.message(context,
                        "resolverNotWritable", base.getClass().getName()));
            }

            int idx = coerce(property);
            try {
                list.set(idx, value);
            } catch (UnsupportedOperationException e) {
                throw new PropertyNotWritableException(e);
            } catch (IndexOutOfBoundsException e) {
                throw new PropertyNotFoundException(e);
            }
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base instanceof List<?>) {
            context.setPropertyResolved(base, property);
            List<?> list = (List<?>) base;
            try {
                int idx = coerce(property);
                if (idx < 0 || idx >= list.size()) {
                    throw new PropertyNotFoundException(
                            new ArrayIndexOutOfBoundsException(idx)
                                    .getMessage());
                }
            } catch (IllegalArgumentException e) {
                // ignore
            }
            return this.readOnly || UNMODIFIABLE.equals(list.getClass());
        }

        return this.readOnly;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        if (base instanceof List<?>) { // implies base != null
            return Integer.class;
        }
        return null;
    }

    private static final int coerce(Object property) {
        if (property instanceof Number) {
            return ((Number) property).intValue();
        }
        if (property instanceof Character) {
            return ((Character) property).charValue();
        }
        if (property instanceof Boolean) {
            return ((Boolean) property).booleanValue() ? 1 : 0;
        }
        if (property instanceof String) {
            return Integer.parseInt((String) property);
        }
        throw new IllegalArgumentException(property != null ?
                property.toString() : "null");
    }
}
