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
package org.apache.tomcat.util.modeler;

import java.util.Collections;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

public class TestNoDescriptorRegistry {

    private static final ObjectName ONAME;

    static {
        try {
            ONAME = new ObjectName("Tomcat:type=Tester,name=NoJmx");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGetMBeanInfoThrows() throws Exception {
        MBeanServer server = new NoDescriptorRegistry().getMBeanServer();
        Assert.assertThrows(InstanceNotFoundException.class, () -> server.getMBeanInfo(ONAME));
    }

    @Test
    public void testInvokeThrows() throws Exception {
        MBeanServer server = new NoDescriptorRegistry().getMBeanServer();
        Assert.assertThrows(InstanceNotFoundException.class,
                () -> server.invoke(ONAME, "isDeployed", new Object[] { "ctx" }, new String[] { "java.lang.String" }));
    }

    @Test
    public void testGetAttributeThrows() throws Exception {
        MBeanServer server = new NoDescriptorRegistry().getMBeanServer();
        Assert.assertThrows(InstanceNotFoundException.class, () -> server.getAttribute(ONAME, "name"));
    }

    @Test
    public void testGetObjectInstanceThrows() throws Exception {
        MBeanServer server = new NoDescriptorRegistry().getMBeanServer();
        Assert.assertThrows(InstanceNotFoundException.class, () -> server.getObjectInstance(ONAME));
    }

    @Test
    public void testRegistryGetMethodInfoThrows() throws Exception {
        Registry registry = new NoDescriptorRegistry();
        Assert.assertThrows(InstanceNotFoundException.class, () -> registry.getMethodInfo(ONAME, "test", 0));
    }

    @Test
    public void testRegistryGetTypeInfoReturnsNull() throws Exception {
        // Overridden by NoDescriptorRegistry, must not throw or fail
        Registry registry = new NoDescriptorRegistry();
        Assert.assertNull(registry.getType(ONAME, "name"));
        Assert.assertNull(registry.getMethodInfo(ONAME, "test"));
    }

    @Test
    public void testCountsAndQueries() throws Exception {
        MBeanServer server = new NoDescriptorRegistry().getMBeanServer();
        Assert.assertEquals(Integer.valueOf(0), server.getMBeanCount());
        Assert.assertFalse(server.isRegistered(ONAME));
        Assert.assertTrue(server.queryNames(null, null).isEmpty());
        Assert.assertTrue(server.queryMBeans(null, null).isEmpty());
        Assert.assertEquals(0, server.getDomains().length);
    }

    @Test
    public void testUnregisterIsSilentlyIgnoredViaRegistry() throws Exception {
        // unregisterComponent is a no-op for NoDescriptorRegistry and must not
        // reach the MBeanServer methods that now throw
        Registry registry = new NoDescriptorRegistry();
        registry.unregisterComponent(ONAME);
        registry.unregisterComponent(ONAME.getCanonicalName());
        registry.registerComponent(new Object(), ONAME, null);
        registry.registerComponent(new Object(), ONAME.getCanonicalName(), null);
        registry.invoke(Collections.emptyList(), "test", false);
    }
}
