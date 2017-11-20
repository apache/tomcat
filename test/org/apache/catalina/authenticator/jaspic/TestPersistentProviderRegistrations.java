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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.authenticator.jaspic.PersistentProviderRegistrations.Provider;
import org.apache.catalina.authenticator.jaspic.PersistentProviderRegistrations.Providers;

public class TestPersistentProviderRegistrations {

    @Test
    public void testLoadEmpty() {
        File f = new File("test/conf/jaspic-test-01.xml");
        Providers result = PersistentProviderRegistrations.loadProviders(f);
        Assert.assertEquals(0,  result.getProviders().size());
    }


    @Test
    public void testLoadSimple() {
        File f = new File("test/conf/jaspic-test-02.xml");
        Providers result = PersistentProviderRegistrations.loadProviders(f);
        validateSimple(result);
    }


    private void validateSimple(Providers providers) {
        Assert.assertEquals(1,  providers.getProviders().size());
        Provider p = providers.getProviders().get(0);
        Assert.assertEquals("a", p.getClassName());
        Assert.assertEquals("b", p.getLayer());
        Assert.assertEquals("c", p.getAppContext());
        Assert.assertEquals("d", p.getDescription());

        Assert.assertEquals(2,  p.getProperties().size());
        Assert.assertEquals("f", p.getProperties().get("e"));
        Assert.assertEquals("h", p.getProperties().get("g"));
    }


    @Test
    public void testSaveSimple() {
        File f = new File("test/conf/jaspic-test-03.xml");
        if (f.exists()) {
            Assert.assertTrue(f.delete());
        }

        // Create a config and write it out
        Providers start = new Providers();
        Provider p = new Provider();
        p.setClassName("a");
        p.setLayer("b");
        p.setAppContext("c");
        p.setDescription("d");
        p.addProperty("e", "f");
        p.addProperty("g", "h");
        start.addProvider(p);
        PersistentProviderRegistrations.writeProviders(start, f);

        // Read it back
        Providers end = PersistentProviderRegistrations.loadProviders(f);

        validateSimple(end);

        if (f.exists()) {
            f.delete();
        }
    }


    @Test
    public void testLoadProviderWithoutLayerAndAC() {
        File f = new File("test/conf/jaspic-test-04.xml");
        Providers providers = PersistentProviderRegistrations.loadProviders(f);
        validateNoLayerAndAC(providers);
    }


    private void validateNoLayerAndAC(Providers providers) {
        Assert.assertEquals(1,  providers.getProviders().size());
        Provider p = providers.getProviders().get(0);
        Assert.assertEquals("a", p.getClassName());
        Assert.assertNull(p.getLayer());
        Assert.assertNull(p.getAppContext());
        Assert.assertEquals("d", p.getDescription());
    }


    @Test
    public void testSaveProviderWithoutLayerAndAC() {
        File f = new File("test/conf/jaspic-test-05.xml");
        if (f.exists()) {
            Assert.assertTrue(f.delete());
        }

        // Create a config and write it out
        Providers initialProviders = new Providers();
        Provider p = new Provider();
        p.setClassName("a");
        p.setDescription("d");
        initialProviders.addProvider(p);
        PersistentProviderRegistrations.writeProviders(initialProviders, f);

        // Read it back
        Providers loadedProviders = PersistentProviderRegistrations.loadProviders(f);

        try {
            validateNoLayerAndAC(loadedProviders);
        } finally {
            if (f.exists()) {
                f.delete();
            }
        }
    }
}
