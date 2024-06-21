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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standard ELResolver for working with JavaBeans.
 */
public class BeanELResolver extends ELResolver {

    private static final int CACHE_SIZE;
    private static final String CACHE_SIZE_PROP = "org.apache.el.BeanELResolver.CACHE_SIZE";

    static {
        CACHE_SIZE = Integer.getInteger(CACHE_SIZE_PROP, 1000).intValue();
    }

    private final boolean readOnly;

    private final ConcurrentCache<String,BeanProperties> cache = new ConcurrentCache<>(CACHE_SIZE);

    /**
     * Creates a writable instance of the standard JavaBean resolver.
     */
    public BeanELResolver() {
        this.readOnly = false;
    }

    /**
     * Creates an instance of the standard JavaBean resolver.
     *
     * @param readOnly {@code true} if the created instance should be read-only otherwise false.
     */
    public BeanELResolver(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);
        if (base == null || property == null) {
            return null;
        }

        context.setPropertyResolved(base, property);
        BeanProperty beanProperty = property(context, base, property);

        if (readOnly || beanProperty.isReadOnly(base)) {
            return null;
        }

        return beanProperty.getPropertyType();
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);
        if (base == null || property == null) {
            return null;
        }

        context.setPropertyResolved(base, property);
        Method m = this.property(context, base, property).read(context, base);
        try {
            return m.invoke(base, (Object[]) null);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            Util.handleThrowable(cause);
            throw new ELException(
                    Util.message(context, "propertyReadError", base.getClass().getName(), property.toString()), cause);
        } catch (Exception e) {
            throw new ELException(e);
        }
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        Objects.requireNonNull(context);
        if (base == null || property == null) {
            return;
        }

        context.setPropertyResolved(base, property);

        if (this.readOnly) {
            throw new PropertyNotWritableException(
                    Util.message(context, "resolverNotWritable", base.getClass().getName()));
        }

        Method m = this.property(context, base, property).write(context, base);
        try {
            m.invoke(base, value);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            Util.handleThrowable(cause);
            throw new ELException(
                    Util.message(context, "propertyWriteError", base.getClass().getName(), property.toString()), cause);
        } catch (Exception e) {
            throw new ELException(e);
        }
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        Objects.requireNonNull(context);
        if (base == null || method == null) {
            return null;
        }

        ExpressionFactory factory = ELManager.getExpressionFactory();

        String methodName = factory.coerceToType(method, String.class);

        // Find the matching method
        Method matchingMethod = Util.findMethod(context, base.getClass(), base, methodName, paramTypes, params);

        Object[] parameters =
                Util.buildParameters(context, matchingMethod.getParameterTypes(), matchingMethod.isVarArgs(), params);

        Object result = null;
        try {
            result = matchingMethod.invoke(base, parameters);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new ELException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            Util.handleThrowable(cause);
            throw new ELException(cause);
        }

        context.setPropertyResolved(base, method);
        return result;
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);
        if (base == null || property == null) {
            return false;
        }

        context.setPropertyResolved(base, property);
        return this.readOnly || this.property(context, base, property).isReadOnly(base);
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        if (base != null) {
            return Object.class;
        }

        return null;
    }

    abstract static class BeanProperties {
        protected final Map<String,BeanProperty> properties;
        protected final Class<?> type;

        BeanProperties(Class<?> type) throws ELException {
            this.type = type;
            this.properties = new HashMap<>();
        }

        private BeanProperty get(ELContext ctx, String name) {
            BeanProperty property = this.properties.get(name);
            if (property == null) {
                throw new PropertyNotFoundException(Util.message(ctx, "propertyNotFound", type.getName(), name));
            }
            return property;
        }

        private Class<?> getType() {
            return type;
        }
    }

    abstract static class BeanProperty {
        private final Class<?> type;

        private final Class<?> owner;

        private Method read;

        private Method write;

        BeanProperty(Class<?> owner, Class<?> type) {
            this.owner = owner;
            this.type = type;
        }

        public Class<?> getPropertyType() {
            return this.type;
        }

        public boolean isReadOnly(Object base) {
            return this.write == null && (null == (this.write = Util.getMethod(this.owner, base, getWriteMethod())));
        }

        private Method write(ELContext ctx, Object base) {
            if (this.write == null) {
                this.write = Util.getMethod(this.owner, base, getWriteMethod());
                if (this.write == null) {
                    throw new PropertyNotWritableException(
                            Util.message(ctx, "propertyNotWritable", new Object[] { owner.getName(), getName() }));
                }
            }
            return this.write;
        }

        private Method read(ELContext ctx, Object base) {
            if (this.read == null) {
                this.read = Util.getMethod(this.owner, base, getReadMethod());
                if (this.read == null) {
                    throw new PropertyNotFoundException(
                            Util.message(ctx, "propertyNotReadable", new Object[] { owner.getName(), getName() }));
                }
            }
            return this.read;
        }

        abstract Method getWriteMethod();

        abstract Method getReadMethod();

        abstract String getName();
    }

    private BeanProperty property(ELContext ctx, Object base, Object property) {
        Class<?> type = base.getClass();
        String prop = property.toString();

        BeanProperties props = this.cache.get(type.getName());
        if (props == null || type != props.getType()) {
            props = BeanSupport.getInstance().getBeanProperties(type);
            this.cache.put(type.getName(), props);
        }

        return props.get(ctx, prop);
    }

    private static final class ConcurrentCache<K, V> {

        private final int size;
        private final Map<K,V> eden;
        private final Map<K,V> longterm;

        ConcurrentCache(int size) {
            this.size = size;
            this.eden = new ConcurrentHashMap<>(size);
            this.longterm = new WeakHashMap<>(size);
        }

        public V get(K key) {
            V value = this.eden.get(key);
            if (value == null) {
                synchronized (longterm) {
                    value = this.longterm.get(key);
                }
                if (value != null) {
                    this.eden.put(key, value);
                }
            }
            return value;
        }

        public void put(K key, V value) {
            if (this.eden.size() >= this.size) {
                synchronized (longterm) {
                    this.longterm.putAll(this.eden);
                }
                this.eden.clear();
            }
            this.eden.put(key, value);
        }

    }
}
