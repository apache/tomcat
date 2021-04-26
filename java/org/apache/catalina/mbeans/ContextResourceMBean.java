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
package org.apache.catalina.mbeans;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.RuntimeOperationsException;

import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.NamingResources;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.tomcat.util.descriptor.web.ContextResource</code> component.</p>
 *
 * @author Amy Roh
 */
public class ContextResourceMBean extends BaseCatalinaMBean<ContextResource> {

    private static final StringManager sm = StringManager.getManager(ContextResourceMBean.class);

    /**
     * Obtain and return the value of a specific attribute of this MBean.
     *
     * @param name Name of the requested attribute
     *
     * @exception AttributeNotFoundException if this attribute is not
     *  supported by this MBean
     * @exception MBeanException if the initializer of an object
     *  throws an exception
     * @exception ReflectionException if a Java reflection exception
     *  occurs when invoking the getter
     */
    @Override
    public Object getAttribute(String name) throws AttributeNotFoundException, MBeanException,
            ReflectionException {

        // Validate the input parameters
        if (name == null) {
            throw new RuntimeOperationsException(
                    new IllegalArgumentException(sm.getString("mBean.nullName")),
                    sm.getString("mBean.nullName"));
        }

        ContextResource cr = doGetManagedResource();

        String value = null;
        if ("auth".equals(name)) {
            return cr.getAuth();
        } else if ("description".equals(name)) {
            return cr.getDescription();
        } else if ("name".equals(name)) {
            return cr.getName();
        } else if ("scope".equals(name)) {
            return cr.getScope();
        } else if ("type".equals(name)) {
            return cr.getType();
        } else {
            value = (String) cr.getProperty(name);
            if (value == null) {
                throw new AttributeNotFoundException(sm.getString("mBean.attributeNotFound", name));
            }
        }

        return value;
    }


    /**
     * Set the value of a specific attribute of this MBean.
     *
     * @param attribute The identification of the attribute to be set
     *  and the new value
     *
     * @exception AttributeNotFoundException if this attribute is not
     *  supported by this MBean
     * @exception MBeanException if the initializer of an object
     *  throws an exception
     * @exception ReflectionException if a Java reflection exception
     *  occurs when invoking the getter
     */
     @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, MBeanException,
            ReflectionException {

        // Validate the input parameters
        if (attribute == null) {
            throw new RuntimeOperationsException(
                    new IllegalArgumentException(sm.getString("mBean.nullAttribute")),
                    sm.getString("mBean.nullAttribute"));
        }
        String name = attribute.getName();
        Object value = attribute.getValue();
        if (name == null) {
            throw new RuntimeOperationsException(
                    new IllegalArgumentException(sm.getString("mBean.nullName")),
                    sm.getString("mBean.nullName"));
        }

        ContextResource cr = doGetManagedResource();

        if ("auth".equals(name)) {
            cr.setAuth((String)value);
        } else if ("description".equals(name)) {
            cr.setDescription((String)value);
        } else if ("name".equals(name)) {
            cr.setName((String)value);
        } else if ("scope".equals(name)) {
            cr.setScope((String)value);
        } else if ("type".equals(name)) {
            cr.setType((String)value);
        } else {
            cr.setProperty(name, "" + value);
        }

        // cannot use side-effects.  It's removed and added back each time
        // there is a modification in a resource.
        NamingResources nr = cr.getNamingResources();
        nr.removeResource(cr.getName());
        nr.addResource(cr);
    }
}
