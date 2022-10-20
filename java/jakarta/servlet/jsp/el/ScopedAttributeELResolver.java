/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet.jsp.el;

import java.util.Objects;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.servlet.jsp.JspContext;
import jakarta.servlet.jsp.PageContext;

/**
 * An ELResolver for working with JSP scoped attributes which may have page,
 * request, session or application scope.
 *
 * @since JSP 2.1
 */
public class ScopedAttributeELResolver extends ELResolver {

    /**
     * Default constructor.
     */
    public ScopedAttributeELResolver() {
        super();
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        Object result = null;

        if (base == null) {
            if (property != null) {
                String key = property.toString();
                PageContext page = (PageContext) context.getContext(JspContext.class);
                result = page.findAttribute(key);

                if (result != null) {
                    context.setPropertyResolved(base, property);
                }
            }
        }

        return result;
    }

    @Override
    public Class<Object> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base == null) {
            context.setPropertyResolved(base, property);
            return Object.class;
        }

        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        Objects.requireNonNull(context);

        if (base == null) {
            context.setPropertyResolved(base, property);
            if (property != null) {
                String key = property.toString();
                PageContext page = (PageContext) context.getContext(JspContext.class);
                int scope = page.getAttributesScope(key);
                if (scope != 0) {
                    page.setAttribute(key, value, scope);
                } else {
                    page.setAttribute(key, value);
                }
            }
        }
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        if (base == null) {
            context.setPropertyResolved(base, property);
        }

        return false;
    }

    @Override
    public Class<String> getCommonPropertyType(ELContext context, Object base) {
        if (base == null) {
            return String.class;
        }
        return null;
    }
}
