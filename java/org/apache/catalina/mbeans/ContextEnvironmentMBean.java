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

import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.NamingResources;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.tomcat.util.descriptor.web.ContextEnvironment</code> component.</p>
 *
 * @author Amy Roh
 */
public class ContextEnvironmentMBean extends BaseCatalinaMBean<ContextEnvironment> {


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

        super.setAttribute(attribute);

        ContextEnvironment ce = doGetManagedResource();

        // cannot use side-effects.  It's removed and added back each time
        // there is a modification in a resource.
        NamingResources nr = ce.getNamingResources();
        nr.removeEnvironment(ce.getName());
        nr.addEnvironment(ce);
    }
}
