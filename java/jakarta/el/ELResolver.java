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

/**
 * @author Jacob Hookom [jacob/hookom.net]
 *
 */
public abstract class ELResolver {

    public static final String TYPE = "type";

    public static final String RESOLVABLE_AT_DESIGN_TIME = "resolvableAtDesignTime";

    /**
     * Obtain the value of the given property on the given object using the
     * given context.
     *
     * @param context The EL context for this evaluation
     * @param base The base object on which the property is to be found
     * @param property The property whose value is to be returned
     * @return the value of the provided property
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
     * Invokes a method on the the given object. This default implementation
     * always returns <code>null</code>.
     *
     * @param context    The EL context for this evaluation
     * @param base       The base object on which the method is to be found
     * @param method     The method to invoke
     * @param paramTypes The types of the parameters of the method to invoke
     * @param params     The parameters with which to invoke the method
     *
     * @return Always <code>null</code>
     *
     * @since EL 2.2
     */
    public Object invoke(ELContext context, Object base, Object method,
            Class<?>[] paramTypes, Object[] params) {
        return null;
    }

    /**
     * Obtain the type of the given property on the given object using the given
     * context.
     *
     * @param context The EL context for this evaluation
     * @param base The base object on which the property is to be found
     * @param property The property whose type is to be returned
     * @return the type of the provided property
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
     * Set the value of the given property on the given object using the given
     * context.
     *
     * @param context  The EL context for this evaluation
     * @param base     The base object on which the property is to be found
     * @param property The property whose value is to be set
     * @param value    The value to set the property to
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
     * Determine if the given property on the given object is read-only using
     * the given context.
     *
     * @param context The EL context for this evaluation
     * @param base The base object on which the property is to be found
     * @param property The property to be checked for read only status
     * @return <code>true</code> if the identified property is read only,
     *         otherwise <code>false</code>
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

    /**
     * Obtain the most common type that is acceptable for the given base object.
     *
     * @param context The context in which the examination takes place
     * @param base The object to examine
     *
     * @return {code null} if the most common type cannot be determine,
     *         otherwise the most common type
     */
    public abstract Class<?> getCommonPropertyType(ELContext context,
            Object base);

    /**
     * Converts the given object to the given type. This default implementation
     * always returns <code>null</code>.
     * @param <T>     The type to which the object should be converted
     *
     * @param context The EL context for this evaluation
     * @param obj     The object to convert
     * @param type    The type to which the object should be converted
     *
     * @return Always <code>null</code>
     *
     * @since EL 3.0
     */
    public <T> T convertToType(ELContext context, Object obj, Class<T> type) {
        context.setPropertyResolved(false);
        return null;
    }
}
