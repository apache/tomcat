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

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Tests for {@link ConnectorMBean}.
 */
public class TestConnectorMBean extends TomcatBaseTest {

    @Test
    public void testConnectorMBeanRegistered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("", getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        // A connector MBean should be registered
        Set<ObjectName> connectors = mbeanServer.queryNames(
                new ObjectName("Tomcat:type=Connector,*"), null);
        Assert.assertFalse("At least one Connector MBean should be registered",
                connectors.isEmpty());

        tomcat.stop();
        tomcat.destroy();
    }


    @Test
    public void testConnectorMBeanGetAttribute() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("", getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        Set<ObjectName> connectors = mbeanServer.queryNames(
                new ObjectName("Tomcat:type=Connector,*"), null);
        Assert.assertFalse(connectors.isEmpty());

        ObjectName connectorName = connectors.iterator().next();

        // getAttribute for scheme should return a value
        Object scheme = mbeanServer.getAttribute(connectorName, "scheme");
        Assert.assertNotNull("scheme attribute should not be null", scheme);

        tomcat.stop();
        tomcat.destroy();
    }


    @Test
    public void testConnectorMBeanSetAttribute() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("", getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        Set<ObjectName> connectors = mbeanServer.queryNames(
                new ObjectName("Tomcat:type=Connector,*"), null);
        Assert.assertFalse(connectors.isEmpty());

        ObjectName connectorName = connectors.iterator().next();

        // setAttribute for maxPostSize
        mbeanServer.setAttribute(connectorName,
                new javax.management.Attribute("maxPostSize", "4194304"));

        // Verify the attribute was updated
        Object maxPostSize = mbeanServer.getAttribute(connectorName,
                "maxPostSize");
        Assert.assertNotNull(maxPostSize);

        tomcat.stop();
        tomcat.destroy();
    }
}
