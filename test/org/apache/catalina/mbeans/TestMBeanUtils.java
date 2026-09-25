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

import javax.management.MBeanServer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Tests for {@link MBeanUtils}.
 */
public class TestMBeanUtils {

    @Test
    public void testCreateRegistry() {
        Registry registry = MBeanUtils.createRegistry();
        Assert.assertNotNull("Registry should not be null", registry);
    }


    @Test
    public void testCreateRegistrySingleton() {
        Registry registry1 = MBeanUtils.createRegistry();
        Registry registry2 = MBeanUtils.createRegistry();
        Assert.assertSame("createRegistry should return singleton",
                registry1, registry2);
    }


    @Test
    public void testCreateServer() {
        MBeanServer server = MBeanUtils.createServer();
        Assert.assertNotNull("MBeanServer should not be null", server);
    }


    @Test
    public void testCreateServerSingleton() {
        MBeanServer server1 = MBeanUtils.createServer();
        MBeanServer server2 = MBeanUtils.createServer();
        Assert.assertSame("createServer should return singleton",
                server1, server2);
    }


    @Test
    public void testRegistryContainsManagedBeans() {
        Registry registry = MBeanUtils.createRegistry();

        // These managed beans should be loaded from mbeans-descriptors.xml
        ManagedBean serverBean = registry.findManagedBean("StandardServer");
        Assert.assertNotNull("StandardServer managed bean should exist",
                serverBean);

        ManagedBean engineBean = registry.findManagedBean("StandardEngine");
        Assert.assertNotNull("StandardEngine managed bean should exist",
                engineBean);

        ManagedBean hostBean = registry.findManagedBean("StandardHost");
        Assert.assertNotNull("StandardHost managed bean should exist",
                hostBean);

        ManagedBean contextBean = registry.findManagedBean("StandardContext");
        Assert.assertNotNull("StandardContext managed bean should exist",
                contextBean);
    }


    @Test
    public void testRegistryContainsConnectorBean() {
        Registry registry = MBeanUtils.createRegistry();

        ManagedBean connectorBean = registry.findManagedBean("CoyoteConnector");
        Assert.assertNotNull("CoyoteConnector managed bean should exist",
                connectorBean);
    }


    @Test
    public void testRegistryContainsUserDatabaseBeans() {
        Registry registry = MBeanUtils.createRegistry();

        ManagedBean userDbBean = registry.findManagedBean("MemoryUserDatabase");
        Assert.assertNotNull("MemoryUserDatabase managed bean should exist",
                userDbBean);

        ManagedBean groupBean = registry.findManagedBean("Group");
        Assert.assertNotNull("Group managed bean should exist", groupBean);

        ManagedBean roleBean = registry.findManagedBean("Role");
        Assert.assertNotNull("Role managed bean should exist", roleBean);

        ManagedBean userBean = registry.findManagedBean("User");
        Assert.assertNotNull("User managed bean should exist", userBean);
    }


    @Test
    public void testRegistryContainsValveBeans() {
        Registry registry = MBeanUtils.createRegistry();

        ManagedBean accessLogBean = registry.findManagedBean("AccessLogValve");
        Assert.assertNotNull("AccessLogValve managed bean should exist",
                accessLogBean);
    }


    @Test
    public void testRegistryContainsRealmBeans() {
        Registry registry = MBeanUtils.createRegistry();

        ManagedBean realmBean = registry.findManagedBean("LockOutRealm");
        Assert.assertNotNull("LockOutRealm managed bean should exist",
                realmBean);
    }
}
