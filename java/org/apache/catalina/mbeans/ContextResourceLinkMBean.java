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

import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.apache.tomcat.util.descriptor.web.NamingResources;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>
 * A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.tomcat.util.descriptor.web.ContextResourceLink</code> component.
 * </p>
 *
 * @author Amy Roh
 */
public class ContextResourceLinkMBean extends BaseCatalinaMBean<ContextResourceLink> {

    private static final StringManager sm = StringManager.getManager(ContextResourceLinkMBean.class);

    @Override
    public Object getAttribute(String name) throws AttributeNotFoundException, MBeanException, ReflectionException {

        // Validate the input parameters
        if (name == null) {
            throw new RuntimeOperationsException(new IllegalArgumentException(sm.getString("mBean.nullName")),
                    sm.getString("mBean.nullName"));
        }

        ContextResourceLink cl = doGetManagedResource();

        String value = null;
        if ("global".equals(name)) {
            return cl.getGlobal();
        } else if ("description".equals(name)) {
            return cl.getDescription();
        } else if ("name".equals(name)) {
            return cl.getName();
        } else if ("type".equals(name)) {
            return cl.getType();
        } else {
            value = (String) cl.getProperty(name);
            if (value == null) {
                throw new AttributeNotFoundException(sm.getString("mBean.attributeNotFound", name));
            }
        }

        return value;
    }


    @Override
    public void setAttribute(Attribute attribute)
            throws AttributeNotFoundException, MBeanException, ReflectionException {

        // Validate the input parameters
        if (attribute == null) {
            throw new RuntimeOperationsException(new IllegalArgumentException(sm.getString("mBean.nullAttribute")),
                    sm.getString("mBean.nullAttribute"));
        }

        String name = attribute.getName();
        Object value = attribute.getValue();
        if (name == null) {
            throw new RuntimeOperationsException(new IllegalArgumentException(sm.getString("mBean.nullName")),
                    sm.getString("mBean.nullName"));
        }

        ContextResourceLink crl = doGetManagedResource();

        if ("global".equals(name)) {
            crl.setGlobal((String) value);
        } else if ("description".equals(name)) {
            crl.setDescription((String) value);
        } else if ("name".equals(name)) {
            crl.setName((String) value);
        } else if ("type".equals(name)) {
            crl.setType((String) value);
        } else {
            crl.setProperty(name, "" + value);
        }

        // cannot use side-effects. It's removed and added back each time
        // there is a modification in a resource.
        NamingResources nr = crl.getNamingResources();
        nr.removeResourceLink(crl.getName());
        nr.addResourceLink(crl);
    }
}
