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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.NamingResources;
import org.apache.tomcat.util.res.StringManager;

/**
 * A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.tomcat.util.descriptor.web.ContextEnvironment</code> component.
 */
public class ContextEnvironmentMBean extends BaseCatalinaMBean<ContextEnvironment> {

    /**
     * Default constructor for ContextEnvironmentMBean.
     */
    public ContextEnvironmentMBean() {
    }

    private static final Log log = LogFactory.getLog(ContextEnvironmentMBean.class);
    private static final StringManager sm = StringManager.getManager(ContextEnvironmentMBean.class);

    @Override
    public void setAttribute(Attribute attribute)
            throws AttributeNotFoundException, MBeanException, ReflectionException {

        if (attribute == null) {
            throw new RuntimeOperationsException(new IllegalArgumentException(sm.getString("mBean.nullAttribute")),
                    sm.getString("mBean.nullAttribute"));
        }

        ContextEnvironment ce = doGetManagedResource();

        if ("name".equals(attribute.getName())) {
            // Updating the name actually needs removing and adding back the
            // component under the new name. Ignore the change, as the other
            // naming resource MBeans do.
            log.info(sm.getString("mBean.nameChange"));
            return;
        }

        String oldType = ce.getType();
        String oldValue = ce.getValue();
        String oldLookup = ce.getLookupName();

        super.setAttribute(attribute);

        // cannot use side effects. It's removed and added back each time
        // there is a modification in a resource.
        NamingResources nr = ce.getNamingResources();
        if (nr != null) {
            try {
                nr.removeEnvironment(ce.getName());
                nr.addEnvironment(ce);
            } catch (IllegalArgumentException iae) {
                // The change is not acceptable. Restore the previous state
                // before passing the failure to the caller, so the entry is
                // not lost.
                ce.setType(oldType);
                ce.setValue(oldValue);
                ce.setLookupName(oldLookup);
                nr.addEnvironment(ce);
                throw iae;
            }
        }
    }
}
