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

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.coyote.tomcat5.CoyoteConnector</code> component.</p>
 *
 * @author Amy Roh
 */
public class ConnectorMBean extends ClassNameMBean<Connector> {

    private static final StringManager sm = StringManager.getManager(ConnectorMBean.class);

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

        Connector connector = doGetManagedResource();
        return IntrospectionUtils.getProperty(connector, name);
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
            throw new RuntimeOperationsException(new IllegalArgumentException(
                    sm.getString("mBean.nullAttribute")), sm.getString("mBean.nullAttribute"));
        }
        String name = attribute.getName();
        Object value = attribute.getValue();
        if (name == null) {
            throw new RuntimeOperationsException(
                    new IllegalArgumentException(sm.getString("mBean.nullName")),
                    sm.getString("mBean.nullName"));
        }

        Connector connector = doGetManagedResource();
        IntrospectionUtils.setProperty(connector, name, String.valueOf(value));
    }
}
