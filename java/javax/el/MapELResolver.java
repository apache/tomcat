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

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MapELResolver extends ELResolver {

	private final static Class UNMODIFIABLE = Collections.unmodifiableMap(
			new HashMap()).getClass();

	private final boolean readOnly;

	public MapELResolver() {
		this.readOnly = false;
	}

	public MapELResolver(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public Object getValue(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		if (base instanceof Map) {
			context.setPropertyResolved(true);
			return ((Map) base).get(property);
		}
		
		return null;
	}

	public Class<?> getType(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		if (base instanceof Map) {
			context.setPropertyResolved(true);
			Object obj = ((Map) base).get(property);
			return (obj != null) ? obj.getClass() : null;
		}
		
		return null;
	}

	public void setValue(ELContext context, Object base, Object property,
			Object value) throws NullPointerException,
			PropertyNotFoundException, PropertyNotWritableException,
			ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		if (base instanceof Map) {
			context.setPropertyResolved(true);

			if (this.readOnly) {
				throw new PropertyNotWritableException(message(context,
						"resolverNotWriteable", new Object[] { base.getClass()
								.getName() }));
			}

			try {
				((Map) base).put(property, value);
			} catch (UnsupportedOperationException e) {
				throw new PropertyNotWritableException(e);
			}
		}
	}

	public boolean isReadOnly(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		if (base instanceof Map) {
			context.setPropertyResolved(true);
			return this.readOnly || UNMODIFIABLE.equals(base.getClass());
		}
		
		return this.readOnly;
	}

	public Iterator getFeatureDescriptors(ELContext context, Object base) {
		if (base instanceof Map) {
			Iterator itr = ((Map) base).keySet().iterator();
			List feats = new ArrayList();
			Object key;
			FeatureDescriptor desc;
			while (itr.hasNext()) {
				key = itr.next();
				desc = new FeatureDescriptor();
				desc.setDisplayName(key.toString());
				desc.setExpert(false);
				desc.setHidden(false);
				desc.setName(key.toString());
				desc.setPreferred(true);
				desc.setValue(RESOLVABLE_AT_DESIGN_TIME, Boolean.FALSE);
				desc.setValue(TYPE, key.getClass());
				feats.add(desc);
			}
			return feats.iterator();
		}
		return null;
	}

	public Class<?> getCommonPropertyType(ELContext context, Object base) {
		if (base instanceof Map) {
			return Object.class;
		}
		return null;
	}

}
