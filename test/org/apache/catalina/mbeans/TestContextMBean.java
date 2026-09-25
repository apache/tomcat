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

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Tests for {@link ContextMBean} via MBeanServer.
 */
public class TestContextMBean extends TomcatBaseTest {

    @Test
    public void testContextMBeanRegistered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("/test",
                getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        Set<ObjectName> contexts = mbeanServer.queryNames(
                new ObjectName("Tomcat:j2eeType=WebModule,*"), null);
        Assert.assertFalse("At least one WebModule MBean should be registered",
                contexts.isEmpty());

        tomcat.stop();
        tomcat.destroy();
    }


    @Test
    public void testContextMBeanAttributes() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("",
                getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        Set<ObjectName> contexts = mbeanServer.queryNames(
                new ObjectName("Tomcat:j2eeType=WebModule,*"), null);
        Assert.assertFalse(contexts.isEmpty());

        ObjectName contextName = contexts.iterator().next();

        // Verify the MBeanInfo is accessible
        Assert.assertNotNull(mbeanServer.getMBeanInfo(contextName));

        tomcat.stop();
        tomcat.destroy();
    }


    @Test
    public void testNamingResourcesMBeanRegistered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("",
                getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        // NamingResources MBeans should be registered for each context
        Set<ObjectName> namingResources = mbeanServer.queryNames(
                new ObjectName("Tomcat:type=NamingResources,*"), null);
        Assert.assertFalse(
                "At least one NamingResources MBean should be registered",
                namingResources.isEmpty());

        tomcat.stop();
        tomcat.destroy();
    }


    @Test
    public void testValveMBeansRegistered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("",
                getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        // Valve MBeans should include at least StandardEngineValve
        Set<ObjectName> valves = mbeanServer.queryNames(
                new ObjectName("Tomcat:type=Valve,*"), null);
        Assert.assertFalse("Valve MBeans should be registered",
                valves.isEmpty());

        // Check for standard valve names
        boolean foundEngineValve = false;
        for (ObjectName on : valves) {
            if (on.toString().contains("StandardEngineValve")) {
                foundEngineValve = true;
                break;
            }
        }
        Assert.assertTrue("StandardEngineValve should be registered",
                foundEngineValve);

        tomcat.stop();
        tomcat.destroy();
    }


    @Test
    public void testMapperMBeanRegistered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.addContext("",
                getTemporaryDirectory().getAbsolutePath());
        tomcat.start();

        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        ObjectName mapperName = new ObjectName("Tomcat:type=Mapper");
        Assert.assertTrue("Mapper MBean should be registered",
                mbeanServer.isRegistered(mapperName));

        tomcat.stop();
        tomcat.destroy();
    }
}
