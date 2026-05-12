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
package org.apache.catalina.storeconfig;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;

/**
 * Test StoreRegistry behavior, particularly dynamic loading of optional classes like clustering.
 *
 * Verifies StoreRegistry uses the same dynamic loading pattern.
 */
public class TestStoreRegistry {

    /**
     * Test that clustering classes are dynamically loaded like other Tomcat components.
     *
     * StoreRegistry should initialize successfully whether clustering is available or not.
     * This matches the pattern used in Catalina.addClusterRuleSet().
     */
    @Test
    public void testClusteringClassesOptional() throws Exception {
        // Verify StoreRegistry initializes successfully with dynamic class loading
        StoreRegistry registry = new StoreRegistry();
        Assert.assertNotNull("Registry should initialize with dynamic loading", registry);

        // Trigger lazy loading of interfaces array
        Method getInterfacesMethod = StoreRegistry.class.getDeclaredMethod("getInterfaces");
        getInterfacesMethod.setAccessible(true);

        Class<?>[] interfaces = (Class<?>[]) getInterfacesMethod.invoke(null);
        Assert.assertNotNull("Interfaces should load dynamically", interfaces);

        // Test passes if we get here without ClassNotFoundException.
        // The actual number of interfaces loaded depends on whether clustering is available,
        // but we should always have at least the core 10 interfaces.
        Assert.assertTrue("Should have at least 10 core interfaces",
                interfaces.length >= 10);

        // Verify required core interfaces are always present
        boolean hasRealm = false;
        boolean hasManager = false;
        boolean hasValve = false;

        for (Class<?> iface : interfaces) {
            if (iface.equals(Realm.class)) {
                hasRealm = true;
            }
            if (iface.equals(Manager.class)) {
                hasManager = true;
            }
            if (iface.equals(Valve.class)) {
                hasValve = true;
            }
        }

        Assert.assertTrue("Should contain Realm interface", hasRealm);
        Assert.assertTrue("Should contain Manager interface", hasManager);
        Assert.assertTrue("Should contain Valve interface", hasValve);
    }

    /**
     * Test that findDescription works with interface inheritance and
     * dynamically loaded interfaces.
     */
    @Test
    public void testFindDescriptionWithDynamicInterfaces() throws Exception {
        StoreRegistry registry = new StoreRegistry();

        // Register a description for the Valve interface
        StoreDescription valveDesc = new StoreDescription();
        valveDesc.setId(Valve.class.getName());
        valveDesc.setTag("Valve");
        valveDesc.setTagClass(Valve.class.getName());
        registry.registerDescription(valveDesc);

        // AccessLogValve implements Valve interface - should find via dynamic interface matching
        String accessLogValveClass = "org.apache.catalina.valves.AccessLogValve";
        StoreDescription foundDesc = registry.findDescription(accessLogValveClass);

        Assert.assertNotNull("Should find description via interface matching", foundDesc);
        Assert.assertEquals("Should match Valve descriptor", "Valve", foundDesc.getTag());
    }
}
