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

import java.util.Iterator;

/**
 * @author Jacob Hookom [jacob/hookom.net]
 *
 */
public abstract class ELResolver {

    public static final String TYPE = "type";

    public static final String RESOLVABLE_AT_DESIGN_TIME = "resolvableAtDesignTime";

    /**
     * @throws NullPointerException
     *              If the supplied context is <code>null</code>
     * @throws PropertyNotFoundException
     *              If the base/property combination provided to the resolver is
     *              one that the resolver can handle but no match was found or a
     *              match was found but was not readable
     * @throws ELException
     *              Wraps any exception throw whilst resolving the property
     */
    public abstract Object getValue(ELContext context, Object base,
            Object property);

    /**
     * @since EL 2.2
     */
    public Object invoke(@SuppressWarnings("unused") ELContext context,
            @SuppressWarnings("unused") Object base,
            @SuppressWarnings("unused") Object method,
            @SuppressWarnings("unused") Class<?>[] paramTypes,
            @SuppressWarnings("unused") Object[] params) {
        return null;
    }

    /**
     * @throws NullPointerException
     *              If the supplied context is <code>null</code>
     * @throws PropertyNotFoundException
     *              If the base/property combination provided to the resolver is
     *              one that the resolver can handle but no match was found or a
     *              match was found but was not readable
     * @throws ELException
     *              Wraps any exception throw whilst resolving the property
     */
    public abstract Class<?> getType(ELContext context, Object base,
            Object property);

    /**
     * @throws NullPointerException
     *              If the supplied context is <code>null</code>
     * @throws PropertyNotFoundException
     *              If the base/property combination provided to the resolver is
     *              one that the resolver can handle but no match was found
     * @throws PropertyNotWritableException
     *              If the base/property combination provided to the resolver is
     *              one that the resolver can handle but the property was not
     *              writable
     * @throws ELException
     *              Wraps any exception throw whilst resolving the property
     */
    public abstract void setValue(ELContext context, Object base,
            Object property, Object value);

    /**
     * @throws NullPointerException
     *              If the supplied context is <code>null</code>
     * @throws PropertyNotFoundException
     *              If the base/property combination provided to the resolver is
     *              one that the resolver can handle but no match was found
     * @throws ELException
     *              Wraps any exception throw whilst resolving the property
     */
    public abstract boolean isReadOnly(ELContext context, Object base,
            Object property);

    public abstract Iterator<java.beans.FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base);

    public abstract Class<?> getCommonPropertyType(ELContext context,
            Object base);

    /**
     * Converts the given object to the given type. This default implementation
     * always returns <code>null</code>.
     *
     * @since EL 3.0
     */
    public Object convertToType(@SuppressWarnings("unused") ELContext context,
            @SuppressWarnings("unused") Object obj,
            @SuppressWarnings("unused") Class<?> type) {
        return null;
    }
}
