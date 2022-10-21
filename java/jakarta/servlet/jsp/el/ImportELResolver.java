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

import jakarta.el.ELClass;
import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.ImportHandler;

/**
 * Providers resolution of imports and static imports in the Jakarta Server
 * Pages ELResolver chain.
 *
 * @since JSP 3.1
 */
public class ImportELResolver extends ELResolver {

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

    /**
     * Default constructor.
     */
    public ImportELResolver() {
        super();
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        Object result = null;

        if (base == null) {
            if (property != null) {
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

                ImportHandler importHandler = context.getImportHandler();
                if (importHandler != null) {
                    String key = property.toString();
                    Class<?> clazz = null;
                    if (resolveClass) {
                        clazz = importHandler.resolveClass(key);
                        if (clazz != null) {
                            result = new ELClass(clazz);
                        }
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

        if (result != null) {
            context.setPropertyResolved(base, property);
        }

        return result;
    }

    @Override
    public Class<Object> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        // In normal usage, ScopedAttributeELResolver will have responded.
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        Objects.requireNonNull(context);
        // In normal usage, ScopedAttributeELResolver will have responded.
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        // In normal usage, ScopedAttributeELResolver will have responded.
        return false;
    }

    @Override
    public Class<String> getCommonPropertyType(ELContext context, Object base) {
        // In normal usage, ScopedAttributeELResolver will have responded.
        return null;
    }
}
