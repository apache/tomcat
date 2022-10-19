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
package org.apache.jasper.el;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.el.ArrayELResolver;
import jakarta.el.BeanELResolver;
import jakarta.el.CompositeELResolver;
import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.el.ListELResolver;
import jakarta.el.MapELResolver;
import jakarta.el.PropertyNotFoundException;
import jakarta.el.ResourceBundleELResolver;
import jakarta.el.StaticFieldELResolver;
import jakarta.servlet.jsp.el.ImplicitObjectELResolver;
import jakarta.servlet.jsp.el.ImportELResolver;
import jakarta.servlet.jsp.el.NotFoundELResolver;
import jakarta.servlet.jsp.el.ScopedAttributeELResolver;

import org.apache.jasper.runtime.ExceptionUtils;
import org.apache.jasper.runtime.JspRuntimeLibrary;

/**
 * Jasper-specific CompositeELResolver that optimizes certain functions to avoid
 * unnecessary resolver calls.
 */
public class JasperELResolver extends CompositeELResolver {

    // Keep aligned with class under test
    private static final int STANDARD_RESOLVERS_COUNT = 11;

    private AtomicInteger resolversSize = new AtomicInteger(0);
    private volatile ELResolver[] resolvers;
    private final int appResolversSize;

    public JasperELResolver(List<ELResolver> appResolvers,
            ELResolver streamResolver) {
        appResolversSize = appResolvers.size();
        resolvers = new ELResolver[appResolversSize + STANDARD_RESOLVERS_COUNT];

        add(new ImplicitObjectELResolver());
        for (ELResolver appResolver : appResolvers) {
            add(appResolver);
        }
        add(streamResolver);
        add(new StaticFieldELResolver());
        add(new MapELResolver());
        add(new ResourceBundleELResolver());
        add(new ListELResolver());
        add(new ArrayELResolver());
        if (JspRuntimeLibrary.GRAAL) {
            add(new GraalBeanELResolver());
        }
        add(new BeanELResolver());
        add(new ScopedAttributeELResolver());
        add(new ImportELResolver());
        add(new NotFoundELResolver());
    }

    @Override
    public synchronized void add(ELResolver elResolver) {
        super.add(elResolver);

        int size = resolversSize.get();

        if (resolvers.length > size) {
            resolvers[size] = elResolver;
        } else {
            ELResolver[] nr = new ELResolver[size + 1];
            System.arraycopy(resolvers, 0, nr, 0, size);
            nr[size] = elResolver;

            resolvers = nr;
        }
        resolversSize.incrementAndGet();
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property)
        throws NullPointerException, PropertyNotFoundException, ELException {
        context.setPropertyResolved(false);

        int start;
        Object result = null;

        if (base == null) {
            // call implicit and app resolvers
            int index = 1 /* implicit */ + appResolversSize;
            for (int i = 0; i < index; i++) {
                result = resolvers[i].getValue(context, base, property);
                if (context.isPropertyResolved()) {
                    return result;
                }
            }
            // skip stream, static and collection-based resolvers (map,
            // resource, list, array) and bean
            start = index + 7;
            if (JspRuntimeLibrary.GRAAL) {
                start++;
            }
        } else {
            // skip implicit resolver only
            start = 1;
        }

        int size = resolversSize.get();
        for (int i = start; i < size; i++) {
            result = resolvers[i].getValue(context, base, property);
            if (context.isPropertyResolved()) {
                return result;
            }
        }

        return null;
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method,
            Class<?>[] paramTypes, Object[] params) {
        String targetMethod = coerceToString(method);
        if (targetMethod.length() == 0) {
            throw new ELException(new NoSuchMethodException());
        }

        context.setPropertyResolved(false);

        Object result = null;

        // skip implicit and call app resolvers, stream resolver and static
        // resolver
        int index = 1 /* implicit */ + appResolversSize +
                2 /* stream + static */;
        for (int i = 1; i < index; i++) {
            result = resolvers[i].invoke(
                    context, base, targetMethod, paramTypes, params);
            if (context.isPropertyResolved()) {
                return result;
            }
        }

        // skip collection (map, resource, list, and array) resolvers
        index += 4;
        // call bean and the rest of resolvers
        int size = resolversSize.get();
        for (int i = index; i < size; i++) {
            result = resolvers[i].invoke(
                    context, base, targetMethod, paramTypes, params);
            if (context.isPropertyResolved()) {
                return result;
            }
        }

        return null;
    }

    /*
     * Copied from org.apache.el.lang.ELSupport#coerceToString(ELContext,Object)
     */
    private static final String coerceToString(final Object obj) {
        if (obj == null) {
            return "";
        } else if (obj instanceof String) {
            return (String) obj;
        } else if (obj instanceof Enum<?>) {
            return ((Enum<?>) obj).name();
        } else {
            return obj.toString();
        }
    }

    /**
     * Extend ELResolver for Graal to avoid bean info use if possible,
     * as BeanELResolver needs manual reflection configuration.
     */
    public static class GraalBeanELResolver extends ELResolver {

        @Override
        public Object getValue(ELContext context, Object base,
                Object property) {
            Object value = null;
            Method method = getReadMethod(base.getClass(), property.toString());
            if (method != null) {
                context.setPropertyResolved(base, property);
                try {
                    method.setAccessible(true);
                    value = method.invoke(base, (Object[]) null);
                } catch (Exception ex) {
                    Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
                    ExceptionUtils.handleThrowable(thr);
                }
            }
            return value;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property,
                Object value) {
            if (base == null) {
                return;
            }
            Method method = getWriteMethod(base.getClass(), property.toString(), value.getClass());
            if (method != null) {
                context.setPropertyResolved(base, property);
                try {
                    method.invoke(base, value);
                } catch (Exception ex) {
                    Throwable thr = ExceptionUtils.unwrapInvocationTargetException(ex);
                    ExceptionUtils.handleThrowable(thr);
                }
            }
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base,
                Object property) {
            Class<?> beanClass = base.getClass();
            String prop = property.toString();
            Method readMethod = getReadMethod(beanClass, prop);
            return readMethod == null || !(getWriteMethod(beanClass, prop, readMethod.getReturnType()) != null);
        }

        private static Method getReadMethod(Class<?> beanClass, String prop) {
            Method methods[] = beanClass.getMethods();
            String isGetter = "is" + capitalize(prop);
            String getter = "get" + capitalize(prop);
            for (Method method : methods) {
                if (method.getParameterCount() == 0) {
                    if (isGetter.equals(method.getName()) && method.getReturnType().equals(boolean.class)) {
                        return method;
                    } else if (getter.equals(method.getName())) {
                        return method;
                    }
                }
            }
            return null;
        }

        private static Method getWriteMethod(Class<?> beanClass, String prop, Class<?> valueClass) {
            String setter = "set" + capitalize(prop);
            Method methods[] = beanClass.getMethods();
            for (Method method : methods) {
                if (method.getParameterCount() == 1 && setter.equals(method.getName())
                        && (valueClass == null || valueClass.isAssignableFrom(method.getParameterTypes()[0]))) {
                    return method;
                }
            }
            return null;
        }

        private static String capitalize(String name) {
            if (name == null || name.length() == 0) {
                return name;
            }
            char chars[] = name.toCharArray();
            chars[0] = Character.toUpperCase(chars[0]);
            return new String(chars);
        }

        @Override
        public Class<?> getType(ELContext context, Object base,
                Object property) {
            return null;
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return null;
        }
    }
}