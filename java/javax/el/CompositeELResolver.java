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
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public class CompositeELResolver extends ELResolver {

    private static final Class<?> SCOPED_ATTRIBUTE_EL_RESOLVER;
    private static final Set<String> KNOWN_NON_TYPE_CONVERTING_RESOLVERS = new HashSet<>();
    static {
        Class<?> clazz = null;
        try {
            clazz = Class.forName("javax.servlet.jsp.el.ScopedAttributeELResolver");
        } catch (ClassNotFoundException e) {
            // Ignore. This is expected if using the EL stand-alone
        }
        SCOPED_ATTRIBUTE_EL_RESOLVER = clazz;

        // EL API Resolvers
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add(ArrayELResolver.class.getName());
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add(BeanELResolver.class.getName());
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add(BeanNameELResolver.class.getName());
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add(ListELResolver.class.getName());
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add(MapELResolver.class.getName());
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add(ResourceBundleELResolver.class.getName());
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add(StaticFieldELResolver.class.getName());
        // JSP API Resolvers - referenced by name to avoid creating dependency
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add("jakarta.servlet.jsp.el.ImplicitObjectELResolver");
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add("jakarta.servlet.jsp.el.ScopedAttributeELResolver");
        // Tomcat internal resolvers - referenced by name to avoid creating dependency
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add("org.apache.jasper.el.JasperELResolver$GraalBeanELResolver");
        KNOWN_NON_TYPE_CONVERTING_RESOLVERS.add("org.apache.el.stream.StreamELResolverImpl");
    }

    private int resolversSize;
    private ELResolver[] resolvers;

    /*
     * Use a separate array for ELResolvers that might implement type conversion as a performance optimisation. See
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=68119
     */
    private int typeConvertersSize;
    private ELResolver[] typeConverters;

    public CompositeELResolver() {
        resolversSize = 0;
        resolvers = new ELResolver[8];

        typeConvertersSize = 0;
        typeConverters = new ELResolver[0];
    }

    public void add(ELResolver elResolver) {
        Objects.requireNonNull(elResolver);

        /*
         * resolversSize should never be larger than resolvers.length. If it ever is, the code will fail when execution
         * reaches System.arraycopy with an IndexOutOfBoundsException.
         */
        if (resolversSize >= resolvers.length) {
            ELResolver[] nr = new ELResolver[resolversSize * 2];
            System.arraycopy(resolvers, 0, nr, 0, resolversSize);
            resolvers = nr;
        }
        resolvers[resolversSize++] = elResolver;

        if (KNOWN_NON_TYPE_CONVERTING_RESOLVERS.contains(elResolver.getClass().getName())) {
            // Performance optimisation. ELResolver known not to perform type conversion
            return;
        }

        if (typeConvertersSize == 0) {
            typeConverters = new ELResolver[1];
        } else if (typeConvertersSize == typeConverters.length) {
            ELResolver[] expandedTypeConverters = new ELResolver[typeConvertersSize * 2];
            System.arraycopy(typeConverters, 0, expandedTypeConverters, 0, typeConvertersSize);
            typeConverters = expandedTypeConverters;
        }
        typeConverters[typeConvertersSize++] = elResolver;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        context.setPropertyResolved(false);
        int sz = resolversSize;
        for (int i = 0; i < sz; i++) {
            Object result = resolvers[i].getValue(context, base, property);
            if (context.isPropertyResolved()) {
                return result;
            }
        }
        return null;
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        context.setPropertyResolved(false);
        int sz = this.resolversSize;
        for (int i = 0; i < sz; i++) {
            Object obj = this.resolvers[i].invoke(context, base, method, paramTypes, params);
            if (context.isPropertyResolved()) {
                return obj;
            }
        }
        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        context.setPropertyResolved(false);
        int sz = this.resolversSize;
        for (int i = 0; i < sz; i++) {
            Class<?> type = this.resolvers[i].getType(context, base, property);
            if (context.isPropertyResolved()) {
                if (SCOPED_ATTRIBUTE_EL_RESOLVER != null &&
                        SCOPED_ATTRIBUTE_EL_RESOLVER.isAssignableFrom(resolvers[i].getClass())) {
                    // Special case since
                    // javax.servlet.jsp.el.ScopedAttributeELResolver will
                    // always return Object.class for type
                    Object value = resolvers[i].getValue(context, base, property);
                    if (value != null) {
                        return value.getClass();
                    }
                }
                return type;
            }
        }
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        context.setPropertyResolved(false);
        int sz = this.resolversSize;
        for (int i = 0; i < sz; i++) {
            this.resolvers[i].setValue(context, base, property, value);
            if (context.isPropertyResolved()) {
                return;
            }
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        context.setPropertyResolved(false);
        int sz = this.resolversSize;
        for (int i = 0; i < sz; i++) {
            boolean readOnly = this.resolvers[i].isReadOnly(context, base, property);
            if (context.isPropertyResolved()) {
                return readOnly;
            }
        }
        return false;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return new FeatureIterator(context, base, this.resolvers, this.resolversSize);
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        Class<?> commonType = null;
        int sz = this.resolversSize;
        for (int i = 0; i < sz; i++) {
            Class<?> type = this.resolvers[i].getCommonPropertyType(context, base);
            if (type != null && (commonType == null || commonType.isAssignableFrom(type))) {
                commonType = type;
            }
        }
        return commonType;
    }

    @Override
    public Object convertToType(ELContext context, Object obj, Class<?> type) {
        context.setPropertyResolved(false);
        int sz = typeConvertersSize;
        for (int i = 0; i < sz; i++) {
            Object result = this.typeConverters[i].convertToType(context, obj, type);
            if (context.isPropertyResolved()) {
                return result;
            }
        }
        return null;
    }

    private static final class FeatureIterator implements Iterator<FeatureDescriptor> {

        private final ELContext context;

        private final Object base;

        private final ELResolver[] resolvers;

        private final int size;

        private Iterator<FeatureDescriptor> itr;

        private int idx;

        private FeatureDescriptor next;

        FeatureIterator(ELContext context, Object base, ELResolver[] resolvers, int size) {
            this.context = context;
            this.base = base;
            this.resolvers = resolvers;
            this.size = size;

            this.idx = 0;
            this.guaranteeIterator();
        }

        private void guaranteeIterator() {
            while (this.itr == null && this.idx < this.size) {
                this.itr = this.resolvers[this.idx].getFeatureDescriptors(this.context, this.base);
                this.idx++;
            }
        }

        @Override
        public boolean hasNext() {
            if (this.next != null) {
                return true;
            }
            if (this.itr != null) {
                while (this.next == null && itr.hasNext()) {
                    this.next = itr.next();
                }
            } else {
                return false;
            }
            if (this.next == null) {
                this.itr = null;
                this.guaranteeIterator();
            }
            return hasNext();
        }

        @Override
        public FeatureDescriptor next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            FeatureDescriptor result = this.next;
            this.next = null;
            return result;

        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
