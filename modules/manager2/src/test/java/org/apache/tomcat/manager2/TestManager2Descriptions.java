/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.manager2;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.modeler.AttributeInfo;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;

/**
 * The {@code manager2.attr.<type>.<name>} messages of the standard attributes (connector, engine, host, ...)
 * override the English descriptions of the MBean descriptors shipped with the container so that they can be
 * translated. Copying the text means it can drift when the container changes it: this test compares every seeded
 * message with the live descriptor value.
 */
public class TestManager2Descriptions {

    private static final Map<String,String> DESCRIPTOR_BEANS = Map.of( //
            "server", "org.apache.catalina.core.StandardServer", //
            "service", "org.apache.catalina.core.StandardService", //
            "engine", "org.apache.catalina.core.StandardEngine", //
            "host", "org.apache.catalina.core.StandardHost", //
            "context", "org.apache.catalina.core.StandardContext", //
            "wrapper", "org.apache.catalina.core.StandardWrapper", //
            "connector", "org.apache.catalina.connector.Connector", //
            "executor", "org.apache.catalina.core.StandardThreadExecutor", //
            "cluster", "org.apache.catalina.ha.tcp.SimpleTcpCluster", //
            "manager", "org.apache.catalina.session.StandardManager");


    @Test
    public void seededAttributeDescriptionsMatchTheDescriptors() {
        Registry registry = Registry.getRegistry(null);
        ClassLoader cl = TestManager2Descriptions.class.getClassLoader();
        for (String pkg : new String[] { "org.apache.catalina.core", "org.apache.catalina.connector",
                "org.apache.catalina.session", "org.apache.catalina.ha.tcp" }) {
            registry.loadDescriptors(pkg, cl);
        }

        ResourceBundle bundle = ResourceBundle.getBundle(Constants.Package + ".LocalStrings", Locale.ROOT, cl);
        int checked = 0;
        for (String key : bundle.keySet()) {
            if (!key.startsWith("manager2.attr.")) {
                continue;
            }
            String rest = key.substring("manager2.attr.".length());
            int dot = rest.indexOf('.');
            String type = rest.substring(0, dot);
            String name = rest.substring(dot + 1);
            String bean = DESCRIPTOR_BEANS.get(type);
            if (bean == null) {
                // Scope of an explicitly defined attribute table (see
                // ConfigApiServlet): no descriptor to compare against.
                continue;
            }
            ManagedBean managed = registry.findManagedBean(bean);
            Assert.assertNotNull("descriptor for " + bean, managed);
            AttributeInfo attribute = null;
            for (AttributeInfo info : managed.getAttributes()) {
                if (info.getName().equals(name)) {
                    attribute = info;
                    break;
                }
            }
            Assert.assertNotNull(type + "." + name + " is not a descriptor attribute", attribute);
            Assert.assertEquals(key, attribute.getDescription(), bundle.getString(key));
            checked++;
        }
        Assert.assertTrue("no seeded descriptor descriptions found", checked > 100);
    }
}
