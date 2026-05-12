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

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link StoreDescription}.
 */
public class TestStoreDescription {

    @Test
    public void testDefaultValues() {
        StoreDescription desc = new StoreDescription();

        Assert.assertNull(desc.getTag());
        Assert.assertNull(desc.getTagClass());
        Assert.assertNull(desc.getStoreFactoryClass());
        Assert.assertNull(desc.getStoreFactory());
        Assert.assertNull(desc.getStoreWriterClass());
        Assert.assertNull(desc.getTransientAttributes());
        Assert.assertNull(desc.getTransientChildren());

        Assert.assertFalse(desc.isStandard());
        Assert.assertFalse(desc.isBackup());
        Assert.assertFalse(desc.isExternalAllowed());
        Assert.assertFalse(desc.isExternalOnly());
        Assert.assertFalse(desc.isDefault());
        Assert.assertFalse(desc.isStoreSeparate());
        Assert.assertFalse(desc.isChildren());
        Assert.assertTrue(desc.isAttributes());
    }


    @Test
    public void testGetSetProperties() {
        StoreDescription desc = new StoreDescription();

        desc.setTag("Server");
        Assert.assertEquals("Server", desc.getTag());

        desc.setTagClass("org.apache.catalina.core.StandardServer");
        Assert.assertEquals("org.apache.catalina.core.StandardServer", desc.getTagClass());

        desc.setStandard(true);
        Assert.assertTrue(desc.isStandard());

        desc.setBackup(true);
        Assert.assertTrue(desc.isBackup());

        desc.setExternalAllowed(true);
        Assert.assertTrue(desc.isExternalAllowed());

        desc.setExternalOnly(true);
        Assert.assertTrue(desc.isExternalOnly());

        desc.setDefault(true);
        Assert.assertTrue(desc.isDefault());

        desc.setAttributes(false);
        Assert.assertFalse(desc.isAttributes());

        desc.setChildren(true);
        Assert.assertTrue(desc.isChildren());

        desc.setStoreSeparate(true);
        Assert.assertTrue(desc.isStoreSeparate());

        desc.setStoreFactoryClass("org.apache.catalina.storeconfig.StandardServerSF");
        Assert.assertEquals("org.apache.catalina.storeconfig.StandardServerSF",
                desc.getStoreFactoryClass());

        desc.setStoreWriterClass("org.example.Writer");
        Assert.assertEquals("org.example.Writer", desc.getStoreWriterClass());
    }


    @Test
    public void testIdReturnsTagClassWhenIdIsNull() {
        StoreDescription desc = new StoreDescription();
        desc.setTagClass("org.apache.catalina.core.StandardServer");

        Assert.assertEquals("org.apache.catalina.core.StandardServer", desc.getId());

        desc.setId("customId");
        Assert.assertEquals("customId", desc.getId());
    }


    @Test
    public void testTransientAttributes() {
        StoreDescription desc = new StoreDescription();

        Assert.assertFalse(desc.isTransientAttribute("available"));

        desc.addTransientAttribute("available");
        desc.addTransientAttribute("configFile");

        Assert.assertTrue(desc.isTransientAttribute("available"));
        Assert.assertTrue(desc.isTransientAttribute("configFile"));
        Assert.assertFalse(desc.isTransientAttribute("unknown"));

        Assert.assertNotNull(desc.getTransientAttributes());
        Assert.assertEquals(2, desc.getTransientAttributes().size());

        desc.removeTransientAttribute("available");
        Assert.assertFalse(desc.isTransientAttribute("available"));
        Assert.assertEquals(1, desc.getTransientAttributes().size());
    }


    @Test
    public void testTransientChildren() {
        StoreDescription desc = new StoreDescription();

        Assert.assertFalse(desc.isTransientChild("org.example.Child"));

        desc.addTransientChild("org.example.Child");
        desc.addTransientChild("org.example.Other");

        Assert.assertTrue(desc.isTransientChild("org.example.Child"));
        Assert.assertTrue(desc.isTransientChild("org.example.Other"));
        Assert.assertFalse(desc.isTransientChild("org.example.Unknown"));

        Assert.assertNotNull(desc.getTransientChildren());
        Assert.assertEquals(2, desc.getTransientChildren().size());

        desc.removeTransientChild("org.example.Child");
        Assert.assertFalse(desc.isTransientChild("org.example.Child"));
    }


    @Test
    public void testSetTransientAttributesList() {
        StoreDescription desc = new StoreDescription();

        List<String> attrs = new ArrayList<>();
        attrs.add("attr1");
        attrs.add("attr2");
        desc.setTransientAttributes(attrs);

        Assert.assertEquals(attrs, desc.getTransientAttributes());
        Assert.assertTrue(desc.isTransientAttribute("attr1"));
    }


    @Test
    public void testSetTransientChildrenList() {
        StoreDescription desc = new StoreDescription();

        List<String> children = new ArrayList<>();
        children.add("child1");
        children.add("child2");
        desc.setTransientChildren(children);

        Assert.assertEquals(children, desc.getTransientChildren());
        Assert.assertTrue(desc.isTransientChild("child1"));
    }


    @Test
    public void testRemoveTransientAttributeWhenNull() {
        StoreDescription desc = new StoreDescription();
        // Should not throw NPE
        desc.removeTransientAttribute("anything");
    }


    @Test
    public void testRemoveTransientChildWhenNull() {
        StoreDescription desc = new StoreDescription();
        // Should not throw NPE
        desc.removeTransientChild("anything");
    }


    @Test
    public void testStoreFactory() {
        StoreDescription desc = new StoreDescription();
        StoreFactoryBase factory = new StoreFactoryBase();

        desc.setStoreFactory(factory);
        Assert.assertSame(factory, desc.getStoreFactory());
    }
}
