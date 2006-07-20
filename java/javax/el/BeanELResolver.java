/*
 * Copyright 2006 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class BeanELResolver extends ELResolver {

	private final boolean readOnly;

	private final ConcurrentCache<String, BeanProperties> cache = new ConcurrentCache<String, BeanProperties>(
			1000);

	public BeanELResolver() {
		this.readOnly = false;
	}

	public BeanELResolver(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public Object getValue(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}
		if (base == null || property == null) {
			return null;
		}

		context.setPropertyResolved(true);
		Method m = this.property(context, base, property).read(context);
		try {
			return m.invoke(base, (Object[]) null);
		} catch (IllegalAccessException e) {
			throw new ELException(e);
		} catch (InvocationTargetException e) {
			throw new ELException(message(context, "propertyReadError",
					new Object[] { base.getClass().getName(),
							property.toString() }), e.getCause());
		} catch (Exception e) {
			throw new ELException(e);
		}
	}

	public Class<?> getType(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}
		if (base == null || property == null) {
			return null;
		}

		context.setPropertyResolved(true);
		return this.property(context, base, property).getType();
	}

	public void setValue(ELContext context, Object base, Object property,
			Object value) throws NullPointerException,
			PropertyNotFoundException, PropertyNotWritableException,
			ELException {
		if (context == null) {
			throw new NullPointerException();
		}
		if (base == null || property == null) {
			return;
		}

		context.setPropertyResolved(true);

		if (this.readOnly) {
			throw new PropertyNotWritableException(message(context,
					"resolverNotWriteable", new Object[] { base.getClass()
							.getName() }));
		}

		Method m = this.property(context, base, property).write(context);
		try {
			m.invoke(base, new Object[] { value });
		} catch (IllegalAccessException e) {
			throw new ELException(e);
		} catch (InvocationTargetException e) {
			throw new ELException(message(context, "propertyWriteError",
					new Object[] { base.getClass().getName(),
							property.toString() }), e.getCause());
		} catch (Exception e) {
			throw new ELException(e);
		}
	}

	public boolean isReadOnly(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}
		if (base == null || property == null) {
			return false;
		}

		context.setPropertyResolved(true);
		return this.readOnly
				|| this.property(context, base, property).isReadOnly();
	}

	public Iterator getFeatureDescriptors(ELContext context, Object base) {
		if (context == null) {
			throw new NullPointerException();
		}

		if (base == null) {
			return null;
		}

		try {
			BeanInfo info = Introspector.getBeanInfo(base.getClass());
			PropertyDescriptor[] pds = info.getPropertyDescriptors();
			for (int i = 0; i < pds.length; i++) {
				pds[i].setValue(RESOLVABLE_AT_DESIGN_TIME, Boolean.TRUE);
				pds[i].setValue(TYPE, pds[i].getPropertyType());
			}
			return Arrays.asList(pds).iterator();
		} catch (IntrospectionException e) {
			//
		}

		return null;
	}

	public Class<?> getCommonPropertyType(ELContext context, Object base) {
		if (context == null) {
			throw new NullPointerException();
		}

		if (base != null) {
			return Object.class;
		}

		return null;
	}

	private final static class BeanProperties {
		private final Map<String, BeanProperty> properties;

		private final Class<?> type;

		public BeanProperties(Class<?> type) throws ELException {
			this.type = type;
			this.properties = new HashMap<String, BeanProperty>();
			try {
				BeanInfo info = Introspector.getBeanInfo(this.type);
				PropertyDescriptor[] pds = info.getPropertyDescriptors();
				for (int i = 0; i < pds.length; i++) {
					this.properties.put(pds[i].getName(), new BeanProperty(
							type, pds[i]));
				}
			} catch (IntrospectionException ie) {
				throw new ELException(ie);
			}
		}

		public BeanProperty get(ELContext ctx, String name) {
			BeanProperty property = this.properties.get(name);
			if (property == null) {
				throw new PropertyNotFoundException(message(ctx,
						"propertyNotFound",
						new Object[] { type.getName(), name }));
			}
			return property;
		}
        
        public Class<?> getType() {
            return type;
        }
	}

	private final static class BeanProperty {
		private final Class type;

		private final Class owner;

		private final PropertyDescriptor descriptor;

		private Method read;

		private Method write;

		public BeanProperty(Class owner, PropertyDescriptor descriptor) {
			this.owner = owner;
			this.descriptor = descriptor;
			this.type = descriptor.getPropertyType();
		}

		public Class<?> getType() {
			return this.type;
		}

		public boolean isReadOnly() {
			return this.write != null
					|| null == getMethod(type, descriptor.getWriteMethod());
		}

		public Method write(ELContext ctx) {
			if (this.write == null) {
				this.write = getMethod(this.owner, descriptor.getWriteMethod());
				if (this.write == null) {
					throw new PropertyNotFoundException(message(ctx,
							"propertyNotWritable", new Object[] {
									type.getName(), descriptor.getName() }));
				}
			}
			return this.write;
		}

		public Method read(ELContext ctx) {
			if (this.read == null) {
				this.read = getMethod(this.owner, descriptor.getReadMethod());
				if (this.read == null) {
					throw new PropertyNotFoundException(message(ctx,
							"propertyNotReadable", new Object[] {
									type.getName(), descriptor.getName() }));
				}
			}
			return this.read;
		}
	}

	private final BeanProperty property(ELContext ctx, Object base,
			Object property) {
		Class<?> type = base.getClass();
		String prop = property.toString();

		BeanProperties props = this.cache.get(type.getName());
		if (props == null || type != props.getType()) {
			props = new BeanProperties(type);
			this.cache.put(type.getName(), props);
		}

		return props.get(ctx, prop);
	}

	private final static Method getMethod(Class type, Method m) {
		if (m == null || Modifier.isPublic(type.getModifiers())) {
			return m;
		}
		Class[] inf = type.getInterfaces();
		Method mp = null;
		for (int i = 0; i < inf.length; i++) {
			try {
				mp = inf[i].getMethod(m.getName(), (Class[]) m.getParameterTypes());
				mp = getMethod(mp.getDeclaringClass(), mp);
				if (mp != null) {
					return mp;
				}
			} catch (NoSuchMethodException e) {
			}
		}
		Class sup = type.getSuperclass();
		if (sup != null) {
			try {
				mp = sup.getMethod(m.getName(), (Class[]) m.getParameterTypes());
				mp = getMethod(mp.getDeclaringClass(), mp);
				if (mp != null) {
					return mp;
				}
			} catch (NoSuchMethodException e) {
			}
		}
		return null;
	}
	
	private final static class ConcurrentCache<K,V> {

		private final int size;
		private final Map<K,V> eden;
		private final Map<K,V> longterm;
		
		public ConcurrentCache(int size) {
			this.size = size;
			this.eden = new ConcurrentHashMap<K,V>(size);
			this.longterm = new WeakHashMap<K,V>(size);
		}
		
		public V get(K key) {
			V value = this.eden.get(key);
			if (value == null) {
				value = this.longterm.get(key);
				if (value != null) {
					this.eden.put(key, value);
				}
			}
			return value;
		}
		
		public void put(K key, V value) {
			if (this.eden.size() >= this.size) {
				this.longterm.putAll(this.eden);
				this.eden.clear();
			}
			this.eden.put(key, value);
		}

	}
}
