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

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link StoreRegistry}.
 */
public class TestStoreRegistry {

    @Test
    public void testDefaultValues() {
        StoreRegistry registry = new StoreRegistry();

        Assert.assertEquals("UTF-8", registry.getEncoding());
        Assert.assertNull(registry.getName());
        Assert.assertNull(registry.getVersion());
    }


    @Test
    public void testGetSetProperties() {
        StoreRegistry registry = new StoreRegistry();

        registry.setName("Tomcat");
        Assert.assertEquals("Tomcat", registry.getName());

        registry.setVersion("12.0");
        Assert.assertEquals("12.0", registry.getVersion());

        registry.setEncoding("ISO-8859-1");
        Assert.assertEquals("ISO-8859-1", registry.getEncoding());
    }


    @Test
    public void testRegisterAndFindDescription() {
        StoreRegistry registry = new StoreRegistry();

        StoreDescription desc = new StoreDescription();
        desc.setTag("Server");
        desc.setTagClass("org.apache.catalina.core.StandardServer");
        desc.setStoreFactoryClass("org.apache.catalina.storeconfig.StandardServerSF");

        registry.registerDescription(desc);

        StoreDescription found = registry.findDescription(
                "org.apache.catalina.core.StandardServer");
        Assert.assertNotNull(found);
        Assert.assertEquals("Server", found.getTag());
    }


    @Test
    public void testFindDescriptionByClass() {
        StoreRegistry registry = new StoreRegistry();

        StoreDescription desc = new StoreDescription();
        desc.setTag("TestTag");
        desc.setTagClass(StoreRegistry.class.getName());

        registry.registerDescription(desc);

        StoreDescription found = registry.findDescription(StoreRegistry.class);
        Assert.assertNotNull(found);
        Assert.assertEquals("TestTag", found.getTag());
    }


    @Test
    public void testFindDescriptionNotFound() {
        StoreRegistry registry = new StoreRegistry();

        Assert.assertNull(registry.findDescription("com.nonexistent.Class"));
    }


    @Test
    public void testUnregisterDescription() {
        StoreRegistry registry = new StoreRegistry();

        StoreDescription desc = new StoreDescription();
        desc.setTag("Server");
        desc.setTagClass("org.apache.catalina.core.StandardServer");

        registry.registerDescription(desc);

        StoreDescription removed = registry.unregisterDescription(desc);
        Assert.assertNotNull(removed);
        Assert.assertEquals("Server", removed.getTag());

        // Should be gone now
        Assert.assertNull(registry.findDescription(
                "org.apache.catalina.core.StandardServer"));
    }


    @Test
    public void testRegisterDescriptionWithId() {
        StoreRegistry registry = new StoreRegistry();

        StoreDescription desc = new StoreDescription();
        desc.setId("customId");
        desc.setTag("Custom");
        desc.setTagClass("org.example.Custom");

        registry.registerDescription(desc);

        StoreDescription found = registry.findDescription("customId");
        Assert.assertNotNull(found);
        Assert.assertEquals("Custom", found.getTag());
    }


    @Test
    public void testFindStoreFactoryNotRegistered() {
        StoreRegistry registry = new StoreRegistry();

        Assert.assertNull(registry.findStoreFactory("com.nonexistent.Class"));
        Assert.assertNull(registry.findStoreFactory(String.class));
    }


    @Test
    public void testFindStoreFactoryRegistered() {
        StoreRegistry registry = new StoreRegistry();

        StoreDescription desc = new StoreDescription();
        desc.setTagClass(StoreRegistry.class.getName());
        StoreFactoryBase factory = new StoreFactoryBase();
        desc.setStoreFactory(factory);

        registry.registerDescription(desc);

        IStoreFactory found = registry.findStoreFactory(StoreRegistry.class);
        Assert.assertNotNull(found);
        Assert.assertSame(factory, found);
    }
}
