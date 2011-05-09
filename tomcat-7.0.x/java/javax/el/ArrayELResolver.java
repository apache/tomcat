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

import java.beans.FeatureDescriptor;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;

public class ArrayELResolver extends ELResolver {

    private final boolean readOnly;

    public ArrayELResolver() {
        this.readOnly = false;
    }

    public ArrayELResolver(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property)
            throws NullPointerException, PropertyNotFoundException, ELException {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base != null && base.getClass().isArray()) {
            context.setPropertyResolved(true);
            int idx = coerce(property);
            if (idx < 0 || idx >= Array.getLength(base)) {
                return null;
            }
            return Array.get(base, idx);
        }

        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property)
            throws NullPointerException, PropertyNotFoundException, ELException {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base != null && base.getClass().isArray()) {
            context.setPropertyResolved(true);
            int idx = coerce(property);
            checkBounds(base, idx);
            return base.getClass().getComponentType();
        }

        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property,
            Object value) throws NullPointerException,
            PropertyNotFoundException, PropertyNotWritableException,
            ELException {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base != null && base.getClass().isArray()) {
            context.setPropertyResolved(true);

            if (this.readOnly) {
                throw new PropertyNotWritableException(message(context,
                        "resolverNotWriteable", new Object[] { base.getClass()
                                .getName() }));
            }

            int idx = coerce(property);
            checkBounds(base, idx);
            if (value != null &&
                    !base.getClass().getComponentType().isAssignableFrom(
                            value.getClass())) {
                throw new ClassCastException(message(context,
                        "objectNotAssignable",
                        new Object[] {value.getClass().getName(),
                        base.getClass().getComponentType().getName()}));
            }
            Array.set(base, idx, value);
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property)
            throws NullPointerException, PropertyNotFoundException, ELException {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base != null && base.getClass().isArray()) {
            context.setPropertyResolved(true);
            int idx = coerce(property);
            checkBounds(base, idx);
        }

        return this.readOnly;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        if (base != null && base.getClass().isArray()) {
            FeatureDescriptor[] descs = new FeatureDescriptor[Array.getLength(base)];
            for (int i = 0; i < descs.length; i++) {
                descs[i] = new FeatureDescriptor();
                descs[i].setDisplayName("["+i+"]");
                descs[i].setExpert(false);
                descs[i].setHidden(false);
                descs[i].setName(""+i);
                descs[i].setPreferred(true);
                descs[i].setValue(RESOLVABLE_AT_DESIGN_TIME, Boolean.FALSE);
                descs[i].setValue(TYPE, Integer.class);
            }
            return Arrays.asList(descs).iterator();
        }
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        if (base != null && base.getClass().isArray()) {
            return Integer.class;
        }
        return null;
    }

    private static final void checkBounds(Object base, int idx) {
        if (idx < 0 || idx >= Array.getLength(base)) {
            throw new PropertyNotFoundException(
                    new ArrayIndexOutOfBoundsException(idx).getMessage());
        }
    }

    private static final int coerce(Object property) {
        if (property instanceof Number) {
            return ((Number) property).intValue();
        }
        if (property instanceof Character) {
            return ((Character) property).charValue();
        }
        if (property instanceof Boolean) {
            return (((Boolean) property).booleanValue() ? 1 : 0);
        }
        if (property instanceof String) {
            return Integer.parseInt((String) property);
        }
        throw new IllegalArgumentException(property != null ?
                property.toString() : "null");
    }

}
