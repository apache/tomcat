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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link StoreFactoryBase}.
 */
public class TestStoreFactoryBase {

    @Test
    public void testGetInfo() {
        StoreFactoryBase factory = new StoreFactoryBase();
        Assert.assertNotNull(factory.getInfo());
        Assert.assertTrue(factory.getInfo().contains("StoreFactoryBase"));
    }


    @Test
    public void testGetSetStoreAppender() {
        StoreFactoryBase factory = new StoreFactoryBase();

        Assert.assertNotNull(factory.getStoreAppender());

        StoreAppender customAppender = new StoreAppender();
        factory.setStoreAppender(customAppender);
        Assert.assertSame(customAppender, factory.getStoreAppender());
    }


    @Test
    public void testGetSetRegistry() {
        StoreFactoryBase factory = new StoreFactoryBase();

        Assert.assertNull(factory.getRegistry());

        StoreRegistry registry = new StoreRegistry();
        factory.setRegistry(registry);
        Assert.assertSame(registry, factory.getRegistry());
    }


    @Test
    public void testStoreXMLHead() {
        StoreFactoryBase factory = new StoreFactoryBase();
        StoreRegistry registry = new StoreRegistry();
        factory.setRegistry(registry);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        factory.storeXMLHead(pw);
        pw.flush();

        String result = sw.toString();
        Assert.assertTrue(result.contains("<?xml version=\"1.0\""));
        Assert.assertTrue(result.contains("encoding=\"UTF-8\""));
    }


    @Test
    public void testStoreXMLHeadCustomEncoding() {
        StoreFactoryBase factory = new StoreFactoryBase();
        StoreRegistry registry = new StoreRegistry();
        registry.setEncoding("ISO-8859-1");
        factory.setRegistry(registry);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        factory.storeXMLHead(pw);
        pw.flush();

        Assert.assertTrue(sw.toString().contains("encoding=\"ISO-8859-1\""));
    }


    @Test
    public void testStoreWithDescription() throws Exception {
        StoreFactoryBase factory = new StoreFactoryBase();
        StoreRegistry registry = new StoreRegistry();
        factory.setRegistry(registry);

        // Register a description for SimpleBean
        StoreDescription desc = new StoreDescription();
        desc.setTag("Connector");
        desc.setTagClass(SimpleBean.class.getName());
        desc.setStandard(true);
        desc.setAttributes(false);
        desc.setChildren(false);
        desc.setStoreFactory(factory);

        registry.registerDescription(desc);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        factory.store(pw, 0, new SimpleBean());
        pw.flush();

        String result = sw.toString();
        Assert.assertTrue("Should contain tag", result.contains("<Connector"));
        Assert.assertTrue("Should be self-closing", result.contains("/>"));
    }


    @Test
    public void testStoreWithChildren() throws Exception {
        StoreFactoryBase factory = new StoreFactoryBase();
        StoreRegistry registry = new StoreRegistry();
        factory.setRegistry(registry);

        StoreDescription desc = new StoreDescription();
        desc.setTag("Server");
        desc.setTagClass(SimpleBean.class.getName());
        desc.setStandard(true);
        desc.setAttributes(false);
        desc.setChildren(true);
        desc.setStoreFactory(factory);

        registry.registerDescription(desc);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        factory.store(pw, 0, new SimpleBean());
        pw.flush();

        String result = sw.toString();
        Assert.assertTrue("Should have open tag", result.contains("<Server>"));
        Assert.assertTrue("Should have close tag", result.contains("</Server>"));
    }


    @Test
    public void testStoreNoDescription() throws Exception {
        StoreFactoryBase factory = new StoreFactoryBase();
        StoreRegistry registry = new StoreRegistry();
        factory.setRegistry(registry);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // No description registered — should log warning but not throw
        factory.store(pw, 0, new SimpleBean());
        pw.flush();

        // No output should be generated
        Assert.assertEquals("", sw.toString());
    }


    @Test
    public void testStoreChildrenDefault() throws Exception {
        StoreFactoryBase factory = new StoreFactoryBase();
        StoreRegistry registry = new StoreRegistry();
        factory.setRegistry(registry);

        StoreDescription desc = new StoreDescription();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // Default storeChildren does nothing — verify it doesn't throw
        factory.storeChildren(pw, 0, new SimpleBean(), desc);
        pw.flush();

        Assert.assertEquals("", sw.toString());
    }


    /**
     * Simple JavaBean for testing.
     */
    public static class SimpleBean {
        private int port = 8080;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
