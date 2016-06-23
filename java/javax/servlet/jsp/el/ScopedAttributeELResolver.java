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
package javax.servlet.jsp.el;

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.el.ELClass;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.ImportHandler;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.PageContext;

/**
*
* @since 2.1
*/
public class ScopedAttributeELResolver extends ELResolver {

    // Indicates if a performance short-cut is available
    private static final Class<?> AST_IDENTIFIER_KEY;

    static {
        Class<?> key = null;
        try {
            key = Class.forName("org.apache.el.parser.AstIdentifier");
        } catch (Exception e) {
            // Ignore: Expected if not running on Tomcat. Not a problem since
            //         this just allows a short-cut.
        }
        AST_IDENTIFIER_KEY = key;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        Object result = null;

        if (base == null) {
            context.setPropertyResolved(base, property);
            if (property != null) {
                String key = property.toString();
                PageContext page = (PageContext) context.getContext(JspContext.class);
                result = page.findAttribute(key);

                if (result == null) {
                    boolean resolveClass = true;
                    // Performance short-cut available when running on Tomcat
                    if (AST_IDENTIFIER_KEY != null) {
                        // Tomcat will set this key to Boolean.TRUE if the
                        // identifier is a stand-alone identifier (i.e.
                        // identifier) rather than part of an AstValue (i.e.
                        // identifier.something). Imports do not need to be
                        // checked if this is a stand-alone identifier
                        Boolean value = (Boolean) context.getContext(AST_IDENTIFIER_KEY);
                        if (value != null && value.booleanValue()) {
                            resolveClass = false;
                        }
                    }
                    // This might be the name of an imported class
                    ImportHandler importHandler = context.getImportHandler();
                    if (importHandler != null) {
                        Class<?> clazz = null;
                        if (resolveClass) {
                            clazz = importHandler.resolveClass(key);
                        }
                        if (clazz != null) {
                            result = new ELClass(clazz);
                        }
                        if (result == null) {
                            // This might be the name of an imported static field
                            clazz = importHandler.resolveStatic(key);
                            if (clazz != null) {
                                try {
                                    result = clazz.getField(key).get(null);
                                } catch (IllegalArgumentException | IllegalAccessException |
                                        NoSuchFieldException | SecurityException e) {
                                    // Most (all?) of these should have been
                                    // prevented by the checks when the import
                                    // was defined.
                                }
                            }
                        }
                    }
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
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {

        PageContext ctxt = (PageContext) context.getContext(JspContext.class);
        List<FeatureDescriptor> list = new ArrayList<>();
        Enumeration<String> e;
        Object value;
        String name;

        e = ctxt.getAttributeNamesInScope(PageContext.PAGE_SCOPE);
        while (e.hasMoreElements()) {
            name = e.nextElement();
            value = ctxt.getAttribute(name, PageContext.PAGE_SCOPE);
            FeatureDescriptor descriptor = new FeatureDescriptor();
            descriptor.setName(name);
            descriptor.setDisplayName(name);
            descriptor.setExpert(false);
            descriptor.setHidden(false);
            descriptor.setPreferred(true);
            descriptor.setShortDescription("page scoped attribute");
            descriptor.setValue("type", value.getClass());
            descriptor.setValue("resolvableAtDesignTime", Boolean.FALSE);
            list.add(descriptor);
        }

        e = ctxt.getAttributeNamesInScope(PageContext.REQUEST_SCOPE);
        while (e.hasMoreElements()) {
            name = e.nextElement();
            value = ctxt.getAttribute(name, PageContext.REQUEST_SCOPE);
            FeatureDescriptor descriptor = new FeatureDescriptor();
            descriptor.setName(name);
            descriptor.setDisplayName(name);
            descriptor.setExpert(false);
            descriptor.setHidden(false);
            descriptor.setPreferred(true);
            descriptor.setShortDescription("request scope attribute");
            descriptor.setValue("type", value.getClass());
            descriptor.setValue("resolvableAtDesignTime", Boolean.FALSE);
            list.add(descriptor);
        }

        if (ctxt.getSession() != null) {
            e = ctxt.getAttributeNamesInScope(PageContext.SESSION_SCOPE);
            while (e.hasMoreElements()) {
                name = e.nextElement();
                value = ctxt.getAttribute(name, PageContext.SESSION_SCOPE);
                FeatureDescriptor descriptor = new FeatureDescriptor();
                descriptor.setName(name);
                descriptor.setDisplayName(name);
                descriptor.setExpert(false);
                descriptor.setHidden(false);
                descriptor.setPreferred(true);
                descriptor.setShortDescription("session scoped attribute");
                descriptor.setValue("type", value.getClass());
                descriptor.setValue("resolvableAtDesignTime", Boolean.FALSE);
                list.add(descriptor);
            }
        }

        e = ctxt.getAttributeNamesInScope(PageContext.APPLICATION_SCOPE);
        while (e.hasMoreElements()) {
            name = e.nextElement();
            value = ctxt.getAttribute(name, PageContext.APPLICATION_SCOPE);
            FeatureDescriptor descriptor = new FeatureDescriptor();
            descriptor.setName(name);
            descriptor.setDisplayName(name);
            descriptor.setExpert(false);
            descriptor.setHidden(false);
            descriptor.setPreferred(true);
            descriptor.setShortDescription("application scoped attribute");
            descriptor.setValue("type", value.getClass());
            descriptor.setValue("resolvableAtDesignTime", Boolean.FALSE);
            list.add(descriptor);
        }
        return list.iterator();
    }

    @Override
    public Class<String> getCommonPropertyType(ELContext context, Object base) {
        if (base == null) {
            return String.class;
        }
        return null;
    }
}
