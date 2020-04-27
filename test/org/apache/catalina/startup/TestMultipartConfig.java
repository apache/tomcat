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
package org.apache.catalina.startup;

import java.lang.reflect.Method;

import javax.servlet.MultipartConfigElement;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.core.StandardWrapper;
import org.apache.tomcat.util.descriptor.web.MultipartDef;
import org.apache.tomcat.util.descriptor.web.ServletDef;
import org.apache.tomcat.util.descriptor.web.WebXml;

public class TestMultipartConfig {
    @Test
    public void testNoMultipartConfig() throws Exception {
        StandardWrapper servlet =  config(null);

        MultipartConfigElement mce = servlet.getMultipartConfigElement();

        Assert.assertNull(mce);
    }

    @Test
    public void testDefaultMultipartConfig() throws Exception {
        MultipartDef multipartDef = new MultipartDef();
        // Do not set any attributes on multipartDef: expect defaults

        StandardWrapper servlet =  config(multipartDef);
        MultipartConfigElement mce = servlet.getMultipartConfigElement();

        Assert.assertNotNull(mce);
        Assert.assertEquals("", mce.getLocation());
        Assert.assertEquals(-1, mce.getMaxFileSize());
        Assert.assertEquals(-1, mce.getMaxRequestSize());
        Assert.assertEquals(0, mce.getFileSizeThreshold());
    }

    @Test
    public void testPartialMultipartConfigMaxFileSize() throws Exception {
        MultipartDef multipartDef = new MultipartDef();
        multipartDef.setMaxFileSize("1024");

        StandardWrapper servlet =  config(multipartDef);
        MultipartConfigElement mce = servlet.getMultipartConfigElement();

        Assert.assertNotNull(mce);
        Assert.assertEquals("", mce.getLocation());
        Assert.assertEquals(1024, mce.getMaxFileSize());
        Assert.assertEquals(-1, mce.getMaxRequestSize());
        Assert.assertEquals(0, mce.getFileSizeThreshold());
    }

    @Test
    public void testPartialMultipartConfigMaxReqeustSize() throws Exception {
        MultipartDef multipartDef = new MultipartDef();
        multipartDef.setMaxRequestSize("10240");

        StandardWrapper servlet =  config(multipartDef);
        MultipartConfigElement mce = servlet.getMultipartConfigElement();

        Assert.assertNotNull(mce);
        Assert.assertEquals("", mce.getLocation());
        Assert.assertEquals(-1, mce.getMaxFileSize());
        Assert.assertEquals(10240, mce.getMaxRequestSize());
        Assert.assertEquals(0, mce.getFileSizeThreshold());
    }

    @Test
    public void testPartialMultipartConfigFileSizeThreshold() throws Exception {
        MultipartDef multipartDef = new MultipartDef();
        multipartDef.setFileSizeThreshold("24");

        StandardWrapper servlet =  config(multipartDef);
        MultipartConfigElement mce = servlet.getMultipartConfigElement();

        Assert.assertNotNull(mce);
        Assert.assertEquals("", mce.getLocation());
        Assert.assertEquals(-1, mce.getMaxFileSize());
        Assert.assertEquals(-1, mce.getMaxRequestSize());
        Assert.assertEquals(24, mce.getFileSizeThreshold());
    }

    @Test
    public void testCompleteMultipartConfig() throws Exception {
        MultipartDef multipartDef = new MultipartDef();
        multipartDef.setMaxFileSize("1024");
        multipartDef.setMaxRequestSize("10240");
        multipartDef.setFileSizeThreshold("24");
        multipartDef.setLocation("/tmp/foo");

        StandardWrapper servlet =  config(multipartDef);

        MultipartConfigElement mce = servlet.getMultipartConfigElement();

        Assert.assertNotNull(mce);
        Assert.assertEquals("/tmp/foo", mce.getLocation());
        Assert.assertEquals(1024, mce.getMaxFileSize());
        Assert.assertEquals(10240, mce.getMaxRequestSize());
        Assert.assertEquals(24, mce.getFileSizeThreshold());
    }

    private StandardWrapper config(MultipartDef multipartDef) throws Exception {
        MyContextConfig config = new MyContextConfig();

        WebXml webxml = new WebXml();

        ServletDef servletDef = new ServletDef();
        servletDef.setServletName("test");
        servletDef.setServletClass("org.apache.catalina.startup.ParamServlet");
        servletDef.setMultipartDef(multipartDef);
        webxml.addServlet(servletDef);

        Method m = ContextConfig.class.getDeclaredMethod("configureContext", WebXml.class);

        // Force our way in
        m.setAccessible(true);

        m.invoke(config, webxml);

        StandardWrapper servlet = (StandardWrapper)config.getContext().findChild("test");

        return servlet;
    }

    private static class MyContextConfig extends ContextConfig {
        public MyContextConfig() {
            CustomContext context = new CustomContext();
            super.context = context;
            context.setConfigured(false);
            context.setState(LifecycleState.STARTING_PREP);
            context.setName("test");

            Connector connector = new Connector();
            StandardService service = new StandardService();
            service.addConnector(connector);
            StandardEngine engine = new StandardEngine();
            engine.setService(service);
            Container parent = new StandardHost();
            parent.setParent(engine);
            super.context.setParent(parent);
            context.getState().equals(LifecycleState.STARTING_PREP);
        }
        public Context getContext() {
            return super.context;
        }
    }

    private static class CustomContext extends StandardContext {
        private volatile LifecycleState state;

        @Override
        public LifecycleState getState() {
            return state;
        }

        @Override
        public synchronized void setState(LifecycleState state) {
            this.state = state;
        }
    }

}
