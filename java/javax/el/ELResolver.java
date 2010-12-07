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

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @author Jacob Hookom [jacob/hookom.net]
 *
 */
public abstract class ELResolver {
    
    static String message(ELContext context, String name, Object[] props) {
        Locale locale = context.getLocale();
        if (locale == null) {
            locale = Locale.getDefault();
            if (locale == null) {
                return "";
            }
        }
        ResourceBundle bundle = ResourceBundle.getBundle(
                "javax.el.LocalStrings", locale);
        try {
            String template = bundle.getString(name);
            if (props != null) {
                template = MessageFormat.format(template, props);
            }
            return template;
        } catch (MissingResourceException e) {
            return "Missing Resource: '" + name + "' for Locale "
                    + locale.getDisplayName();
        }
    }

    public static final String RESOLVABLE_AT_DESIGN_TIME = "resolvableAtDesignTime";
    
    public static final String TYPE = "type";
    
    public abstract Object getValue(ELContext context, Object base, Object property) throws NullPointerException, PropertyNotFoundException, ELException;
    
    public abstract Class<?> getType(ELContext context, Object base, Object property) throws NullPointerException, PropertyNotFoundException, ELException;
    
    public abstract void setValue(ELContext context, Object base, Object property, Object value) throws NullPointerException, PropertyNotFoundException, PropertyNotWritableException, ELException;

    public abstract boolean isReadOnly(ELContext context, Object base, Object property) throws NullPointerException, PropertyNotFoundException, ELException;
    
    public abstract Iterator<java.beans.FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base);
    
    public abstract Class<?> getCommonPropertyType(ELContext context, Object base);
    
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
}
