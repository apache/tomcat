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
package org.apache.catalina.storeconfig;

import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;

/**
 * <p>
 * Rule that creates a new <code>IStoreFactory</code> instance, and associates
 * it with the top object on the stack (which must implement
 * <code>IStoreFactory</code>).
 * </p>
 */

public class StoreFactoryRule extends Rule {

    // ----------------------------------------------------------- Constructors

    /**
     * Construct a new instance of this Rule.
     *
     * @param storeFactoryClass
     *            Default name of the StoreFactory implementation class to be
     *            created
     * @param attributeName
     *            Name of the attribute that optionally includes an override
     *            name of the IStoreFactory class
     * @param storeAppenderClass The store appender class
     * @param appenderAttributeName The attribute name for the store
     *  appender class
     */
    public StoreFactoryRule(String storeFactoryClass, String attributeName,
            String storeAppenderClass, String appenderAttributeName) {

        this.storeFactoryClass = storeFactoryClass;
        this.attributeName = attributeName;
        this.appenderAttributeName = appenderAttributeName;
        this.storeAppenderClass = storeAppenderClass;

    }

    // ----------------------------------------------------- Instance Variables

    /**
     * The attribute name of an attribute that can override the implementation
     * class name.
     */
    private String attributeName;

    private String appenderAttributeName;

    /**
     * The name of the <code>IStoreFactory</code> implementation class.
     */
    private String storeFactoryClass;

    private String storeAppenderClass;

    // --------------------------------------------------------- Public Methods

    /**
     * Handle the beginning of an XML element.
     *
     * @param namespace XML namespace
     * @param name The element name
     * @param attributes The attributes of this element
     * @exception Exception if a processing error occurs
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {

        IStoreFactory factory = (IStoreFactory) newInstance(attributeName,
                storeFactoryClass, attributes);
        StoreAppender storeAppender = (StoreAppender) newInstance(
                appenderAttributeName, storeAppenderClass, attributes);
        factory.setStoreAppender(storeAppender);

        // Add this StoreFactory to our associated component
        StoreDescription desc = (StoreDescription) digester.peek(0);
        StoreRegistry registry = (StoreRegistry) digester.peek(1);
        factory.setRegistry(registry);
        desc.setStoreFactory(factory);

    }

    /**
     * Create new instance from attribute className!
     *
     * @param attr class Name attribute
     * @param defaultName Default Class
     * @param attributes current digester attribute elements
     * @return new configured object instance
     * @throws ReflectiveOperationException Error creating an instance
     */
    protected Object newInstance(String attr, String defaultName,
            Attributes attributes) throws ReflectiveOperationException {
        String className = defaultName;
        if (attr != null) {
            String value = attributes.getValue(attr);
            if (value != null) {
                className = value;
            }
        }
        Class<?> clazz = Class.forName(className);
        return clazz.getConstructor().newInstance();
    }
}