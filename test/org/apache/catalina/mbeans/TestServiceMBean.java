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
 * Tests for {@link ServiceMBean}.
 */
public class TestServiceMBean extends TomcatBaseTest {

    @Test
    public void testServiceMBeanRegistered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("", getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        ObjectName serviceName = new ObjectName("Tomcat:type=Service");
        Assert.assertTrue("Service MBean should be registered",
                mbeanServer.isRegistered(serviceName));

        tomcat.stop();
        tomcat.destroy();
    }


    @Test
    public void testServiceMBeanInfo() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("", getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        ObjectName serviceName = new ObjectName("Tomcat:type=Service");

        // MBeanInfo should be accessible
        Assert.assertNotNull(mbeanServer.getMBeanInfo(serviceName));

        tomcat.stop();
        tomcat.destroy();
    }


    @Test
    public void testServerMBeanRegistered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("", getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        ObjectName serverName = new ObjectName("Tomcat:type=Server");
        Assert.assertTrue("Server MBean should be registered",
                mbeanServer.isRegistered(serverName));

        Object serverInfo = mbeanServer.getAttribute(serverName, "serverInfo");
        Assert.assertNotNull("Server info should not be null", serverInfo);
        Assert.assertTrue("Server info should contain Tomcat",
                serverInfo.toString().contains("Tomcat"));

        tomcat.stop();
        tomcat.destroy();
    }


    @Test
    public void testEngineMBeanRegistered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("", getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        ObjectName engineName = new ObjectName("Tomcat:type=Engine");
        Assert.assertTrue("Engine MBean should be registered",
                mbeanServer.isRegistered(engineName));

        tomcat.stop();
        tomcat.destroy();
    }


    @Test
    public void testHostMBeanRegistered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("", getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        Set<ObjectName> hosts = mbeanServer.queryNames(
                new ObjectName("Tomcat:type=Host,*"), null);
        Assert.assertFalse("At least one Host MBean should be registered",
                hosts.isEmpty());

        tomcat.stop();
        tomcat.destroy();
    }
}
