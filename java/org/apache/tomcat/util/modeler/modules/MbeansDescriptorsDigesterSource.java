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
package org.apache.tomcat.util.modeler.modules;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;

public class MbeansDescriptorsDigesterSource extends ModelerSource
{
    private static final Log log =
            LogFactory.getLog(MbeansDescriptorsDigesterSource.class);
    private static final Object dLock = new Object();

    private Registry registry;
    private final List<ObjectName> mbeans = new ArrayList<>();
    private static Digester digester = null;

    private static Digester createDigester() {

        Digester digester = new Digester();
        digester.setNamespaceAware(false);
        digester.setValidating(false);
        URL url = Registry.getRegistry(null, null).getClass().getResource
            ("/org/apache/tomcat/util/modeler/mbeans-descriptors.dtd");
        if (url != null) {
            digester.register
            ("-//Apache Software Foundation//DTD Model MBeans Configuration File",
                    url.toString());
        }

        // Configure the parsing rules
        digester.addObjectCreate
            ("mbeans-descriptors/mbean",
            "org.apache.tomcat.util.modeler.ManagedBean");
        digester.addSetProperties
            ("mbeans-descriptors/mbean");
        digester.addSetNext
            ("mbeans-descriptors/mbean",
                "add",
            "java.lang.Object");

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/attribute",
            "org.apache.tomcat.util.modeler.AttributeInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/attribute");
        digester.addSetNext
            ("mbeans-descriptors/mbean/attribute",
                "addAttribute",
            "org.apache.tomcat.util.modeler.AttributeInfo");

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/notification",
            "org.apache.tomcat.util.modeler.NotificationInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/notification");
        digester.addSetNext
            ("mbeans-descriptors/mbean/notification",
                "addNotification",
            "org.apache.tomcat.util.modeler.NotificationInfo");

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/notification/descriptor/field",
            "org.apache.tomcat.util.modeler.FieldInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/notification/descriptor/field");
        digester.addSetNext
            ("mbeans-descriptors/mbean/notification/descriptor/field",
                "addField",
            "org.apache.tomcat.util.modeler.FieldInfo");

        digester.addCallMethod
            ("mbeans-descriptors/mbean/notification/notification-type",
                "addNotifType", 0);

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/operation",
            "org.apache.tomcat.util.modeler.OperationInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/operation");
        digester.addSetNext
            ("mbeans-descriptors/mbean/operation",
                "addOperation",
            "org.apache.tomcat.util.modeler.OperationInfo");

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/operation/descriptor/field",
            "org.apache.tomcat.util.modeler.FieldInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/operation/descriptor/field");
        digester.addSetNext
            ("mbeans-descriptors/mbean/operation/descriptor/field",
                "addField",
            "org.apache.tomcat.util.modeler.FieldInfo");

        digester.addObjectCreate
            ("mbeans-descriptors/mbean/operation/parameter",
            "org.apache.tomcat.util.modeler.ParameterInfo");
        digester.addSetProperties
            ("mbeans-descriptors/mbean/operation/parameter");
        digester.addSetNext
            ("mbeans-descriptors/mbean/operation/parameter",
                "addParameter",
            "org.apache.tomcat.util.modeler.ParameterInfo");

        return digester;

    }

    public void setRegistry(Registry reg) {
        this.registry=reg;
    }


    public void setSource( Object source ) {
        this.source=source;
    }

    @Override
    public List<ObjectName> loadDescriptors( Registry registry, String type,
            Object source) throws Exception {
        setRegistry(registry);
        setSource(source);
        execute();
        return mbeans;
    }

    public void execute() throws Exception {
        if (registry == null) {
            registry = Registry.getRegistry(null, null);
        }

        InputStream stream = (InputStream) source;

        List<ManagedBean> loadedMbeans = new ArrayList<>();
        synchronized(dLock) {
            if (digester == null) {
                digester = createDigester();
            }

            // Process the input file to configure our registry
            try {
                // Push our registry object onto the stack
                digester.push(loadedMbeans);
                digester.parse(stream);
            } catch (Exception e) {
                log.error(sm.getString("modules.digesterParseError"), e);
                throw e;
            } finally {
                digester.reset();
            }

        }
        for (ManagedBean loadedMbean : loadedMbeans) {
            registry.addManagedBean(loadedMbean);
        }
    }
}
