/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.authenticator.jaspic;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.security.auth.message.config.AuthConfigProvider;
import jakarta.security.auth.message.config.RegistrationListener;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Globals;

public class TestAuthConfigFactoryImpl {

    private String oldCatalinaBase;
    private static final File TEST_CONFIG_FILE = new File("test/conf/jaspic-providers.xml");

    @Test
    public void testRegistrationNullLayer() {
        doTestRegistration(null,  "AC_1",  ":AC_1");
    }


    @Test
    public void testRegistrationNullAppContext() {
        doTestRegistration("L_1",  null,  "L_1:");
    }


    @Test
    public void testRegistrationNullLayerAndNullAppContext() {
        doTestRegistration(null,  null,  ":");
    }


    @Test
    public void testSearchNoMatch01() {
        doTestSearchOrder("foo", "bar", 1);
    }


    @Test
    public void testSearchNoMatch02() {
        doTestSearchOrder(null, "bar", 1);
    }


    @Test
    public void testSearchNoMatch03() {
        doTestSearchOrder("foo", null, 1);
    }


    @Test
    public void testSearchNoMatch04() {
        doTestSearchOrder(null, null, 1);
    }


    @Test
    public void testSearchOnlyAppContextMatch01() {
        doTestSearchOrder("foo", "AC_1", 2);
    }


    @Test
    public void testSearchOnlyAppContextMatch02() {
        doTestSearchOrder(null, "AC_1", 2);
    }


    @Test
    public void testSearchOnlyAppContextMatch03() {
        doTestSearchOrder("L_2", "AC_1", 2);
    }


    @Test
    public void testSearchOnlyLayerMatch01() {
        doTestSearchOrder("L_1", "bar", 3);
    }


    @Test
    public void testSearchOnlyLayerMatch02() {
        doTestSearchOrder("L_1", null, 3);
    }


    @Test
    public void testSearchOnlyLayerMatch03() {
        doTestSearchOrder("L_1", "AC_2", 3);
    }


    @Test
    public void testSearchBothMatch() {
        doTestSearchOrder("L_2", "AC_2", 4);
    }


    private void doTestSearchOrder(String layer, String appContext, int expected) {
        AuthConfigFactory factory = new AuthConfigFactoryImpl();
        AuthConfigProvider acp1 = new SimpleAuthConfigProvider(null, null);
        factory.registerConfigProvider(acp1, null, null, "1");
        AuthConfigProvider acp2 = new SimpleAuthConfigProvider(null, null);
        factory.registerConfigProvider(acp2, null, "AC_1", "2");
        AuthConfigProvider acp3 = new SimpleAuthConfigProvider(null, null);
        factory.registerConfigProvider(acp3, "L_1", null, "3");
        AuthConfigProvider acp4 = new SimpleAuthConfigProvider(null, null);
        factory.registerConfigProvider(acp4, "L_2", "AC_2", "4");

        AuthConfigProvider searchResult = factory.getConfigProvider(layer, appContext, null);
        int searchIndex;
        if (searchResult == acp1) {
            searchIndex = 1;
        } else if (searchResult == acp2) {
            searchIndex = 2;
        } else if (searchResult == acp3) {
            searchIndex = 3;
        } else if (searchResult == acp4) {
            searchIndex = 4;
        } else {
            searchIndex = -1;
        }
        Assert.assertEquals(expected, searchIndex);
    }


    private void doTestRegistration(String layer, String appContext, String expectedRegId) {
        AuthConfigFactory factory = new AuthConfigFactoryImpl();
        AuthConfigProvider acp1 = new SimpleAuthConfigProvider(null, null);
        SimpleRegistrationListener listener = new SimpleRegistrationListener(layer, appContext);

        String regId = factory.registerConfigProvider(acp1, layer, appContext, null);
        Assert.assertEquals(expectedRegId, regId);

        factory.getConfigProvider(layer, appContext, listener);
        factory.removeRegistration(regId);
        Assert.assertTrue(listener.wasCorrectlyCalled());

        listener.reset();
        factory.registerConfigProvider(acp1, layer, appContext, null);
        factory.getConfigProvider(layer, appContext, listener);
        // Replace it
        AuthConfigProvider acp2 = new SimpleAuthConfigProvider(null, null);
        factory.registerConfigProvider(acp2, layer, appContext, null);
        Assert.assertTrue(listener.wasCorrectlyCalled());
    }


    @Test
    public void testRegistrationInsertExact01() {
        doTestRegistrationInsert("L_3", "AC_2", "L_3", "AC_2");
    }


    @Test
    public void testRegistrationInsertExact02() {
        doTestRegistrationInsert("L_2", "AC_3", "L_2", "AC_3");
    }


    @Test
    public void testRegistrationInsertExact03() {
        doTestRegistrationInsert("L_4", "AC_4", "L_4", "AC_4");
    }


    @Test
    public void testRegistrationInsertAppContext01() {
        doTestRegistrationInsert(null, "AC_3", "L_2", "AC_3");
    }


    @Test
    public void testRegistrationInsertAppContext02() {
        doTestRegistrationInsert(null, "AC_4", "L_4", "AC_4");
    }


    @Test
    public void testRegistrationInsertLayer01() {
        doTestRegistrationInsert("L_4", null, "L_4", "AC_4");
    }


    private void doTestRegistrationInsert(String newLayer, String newAppContext,
            String expectedListenerLayer, String expectedListenerAppContext) {
        // Set up
        AuthConfigFactory factory = new AuthConfigFactoryImpl();
        AuthConfigProvider acp1 = new SimpleAuthConfigProvider(null, null);
        factory.registerConfigProvider(acp1, "L_1", "AC_1", null);
        AuthConfigProvider acp2 = new SimpleAuthConfigProvider(null, null);
        factory.registerConfigProvider(acp2, null, "AC_2", null);
        AuthConfigProvider acp3 = new SimpleAuthConfigProvider(null, null);
        factory.registerConfigProvider(acp3, "L_2", null, null);
        AuthConfigProvider acp4 = new SimpleAuthConfigProvider(null, null);
        factory.registerConfigProvider(acp4, null, null, null);

        SimpleRegistrationListener listener1 = new SimpleRegistrationListener("L_1", "AC_1");
        factory.getConfigProvider("L_1", "AC_1", listener1);
        SimpleRegistrationListener listener2 = new SimpleRegistrationListener("L_3", "AC_2");
        factory.getConfigProvider("L_3", "AC_2", listener2);
        SimpleRegistrationListener listener3 = new SimpleRegistrationListener("L_2", "AC_3");
        factory.getConfigProvider("L_2", "AC_3", listener3);
        SimpleRegistrationListener listener4 = new SimpleRegistrationListener("L_4", "AC_4");
        factory.getConfigProvider("L_4", "AC_4", listener4);

        List<SimpleRegistrationListener> listeners = new ArrayList<>();
        listeners.add(listener1);
        listeners.add(listener2);
        listeners.add(listener3);
        listeners.add(listener4);

        // Register a new provider that will impact some existing registrations
        AuthConfigProvider acpNew = new SimpleAuthConfigProvider(null, null);
        factory.registerConfigProvider(acpNew, newLayer, newAppContext, null);

        // Check to see if the expected listener fired.
        for (SimpleRegistrationListener listener : listeners) {
            if (listener.wasCalled()) {
                Assert.assertEquals(listener.layer, expectedListenerLayer);
                Assert.assertEquals(listener.appContext,  expectedListenerAppContext);
                Assert.assertTrue(listener.wasCorrectlyCalled());
            } else {
                Assert.assertFalse((listener.layer.equals(expectedListenerLayer) &&
                        listener.appContext.equals(expectedListenerAppContext)));
            }
        }
    }


    @Test
    public void testDetachListenerNonexistingRegistration() {
        AuthConfigFactory factory = new AuthConfigFactoryImpl();
        AuthConfigProvider acp1 = new SimpleAuthConfigProvider(null, null);
        String registrationId = factory.registerConfigProvider(acp1, "L_1", "AC_1", null);

        SimpleRegistrationListener listener1 = new SimpleRegistrationListener("L_1", "AC_1");
        factory.getConfigProvider("L_1", "AC_1", listener1);

        factory.removeRegistration(registrationId);
        String[] registrationIds = factory.detachListener(listener1, "L_1", "AC_1");
        Assert.assertTrue(registrationIds.length == 0);
    }


    @Test
    public void testDetachListener() {
        AuthConfigFactory factory = new AuthConfigFactoryImpl();
        AuthConfigProvider acp1 = new SimpleAuthConfigProvider(null, null);
        String registrationId = factory.registerConfigProvider(acp1, "L_1", "AC_1", null);

        SimpleRegistrationListener listener1 = new SimpleRegistrationListener("L_1", "AC_1");
        factory.getConfigProvider("L_1", "AC_1", listener1);

        String[] registrationIds = factory.detachListener(listener1, "L_1", "AC_1");
        Assert.assertTrue(registrationIds.length == 1);
        Assert.assertEquals(registrationId, registrationIds[0]);
    }


    @Test
    public void testRegistrationNullListener() {
        AuthConfigFactory factory = new AuthConfigFactoryImpl();
        AuthConfigProvider acp1 = new SimpleAuthConfigProvider(null, null);
        String registrationId = factory.registerConfigProvider(acp1, "L_1", "AC_1", null);

        factory.getConfigProvider("L_1", "AC_1", null);

        boolean result = factory.removeRegistration(registrationId);
        Assert.assertTrue(result);
    }


    @Test
    public void testAllRegistrationIds() {
        AuthConfigFactory factory = new AuthConfigFactoryImpl();
        AuthConfigProvider acp1 = new SimpleAuthConfigProvider(null, null);
        String registrationId1 = factory.registerConfigProvider(acp1, "L_1", "AC_1", null);
        AuthConfigProvider acp2 = new SimpleAuthConfigProvider(null, null);
        String registrationId2 = factory.registerConfigProvider(acp2, "L_2", "AC_2", null);

        String[] registrationIds = factory.getRegistrationIDs(null);
        Assert.assertTrue(registrationIds.length == 2);
        Set<String> ids = new HashSet<>(Arrays.asList(registrationIds));
        Assert.assertTrue(ids.contains(registrationId1));
        Assert.assertTrue(ids.contains(registrationId2));
    }


    @Before
    public void setUp() {
        // set CATALINA_BASE to test so that the file with persistent providers will be written in test/conf folder
        oldCatalinaBase = System.getProperty(Globals.CATALINA_BASE_PROP);
        System.setProperty(Globals.CATALINA_BASE_PROP, "test");

        if (TEST_CONFIG_FILE.exists()) {
            if (!TEST_CONFIG_FILE.delete()) {
                Assert.fail("Failed to delete " + TEST_CONFIG_FILE);
            }
        }
    }


    @After
    public void cleanUp() {
        if (oldCatalinaBase != null ) {
            System.setProperty(Globals.CATALINA_BASE_PROP, oldCatalinaBase);
        } else {
            System.clearProperty(Globals.CATALINA_BASE_PROP);
        }

        if (TEST_CONFIG_FILE.exists()) {
            if (!TEST_CONFIG_FILE.delete()) {
                Assert.fail("Failed to delete " + TEST_CONFIG_FILE);
            }
        }
    }


    @Test
    public void testRemovePersistentRegistration() {
            AuthConfigFactory factory = new AuthConfigFactoryImpl();
            factory.registerConfigProvider(
                    SimpleAuthConfigProvider.class.getName(), null, "L_1", "AC_1", null);
            String registrationId2 = factory.registerConfigProvider(
                    SimpleAuthConfigProvider.class.getName(), null, "L_2", "AC_2", null);

            factory.removeRegistration(registrationId2);
            factory.refresh();

            String[] registrationIds = factory.getRegistrationIDs(null);
            for (String registrationId : registrationIds) {
                Assert.assertNotEquals(registrationId2, registrationId);
            }
    }


    @Test
    public void testRegistrationNullClassName() {
        doTestNullClassName(false, "L_1", "AC_1");
    }


    @Test
    public void testRegistrationNullClassOverrideExisting() {
        doTestNullClassName(true, "L_1", "AC_1");
    }


    @Test
    public void testRegistrationNullClassNullLayerNullAppContext() {
        doTestNullClassName(false, null, null);
    }


    private void doTestNullClassName(boolean shouldOverrideExistingProvider, String layer, String appContext) {
            AuthConfigFactory factory = new AuthConfigFactoryImpl();
            if (shouldOverrideExistingProvider) {
                factory.registerConfigProvider(SimpleAuthConfigProvider.class.getName(), null, layer, appContext, null);
            }
            String registrationId = factory.registerConfigProvider(null, null, layer, appContext, null);
            factory.refresh();

            String[] registrationIds = factory.getRegistrationIDs(null);
            Set<String> ids = new HashSet<>(Arrays.asList(registrationIds));
            Assert.assertTrue(ids.contains(registrationId));
            AuthConfigProvider provider = factory.getConfigProvider(layer, appContext, null);
            Assert.assertNull(provider);
    }


    private static class SimpleRegistrationListener implements RegistrationListener {

        private final String layer;
        private final String appContext;

        private boolean called = false;
        private String layerNotified;
        private String appContextNotified;

        public SimpleRegistrationListener(String layer, String appContext) {
            this.layer = layer;
            this.appContext = appContext;
        }

        @Override
        public void notify(String layer, String appContext) {
            called = true;
            layerNotified = layer;
            appContextNotified = appContext;
        }


        public boolean wasCalled() {
            return called;
        }


        public boolean wasCorrectlyCalled() {
            return called && areTheSame(layer, layerNotified) &&
                    areTheSame(appContext, appContextNotified);
        }


        public void reset() {
            called = false;
            layerNotified = null;
            appContextNotified = null;
        }


        private static boolean areTheSame(String a, String b) {
            if (a == null) {
                return b == null;
            }
            return a.equals(b);
        }
    }
}
