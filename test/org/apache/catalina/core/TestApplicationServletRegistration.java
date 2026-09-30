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
package org.apache.catalina.core;

import jakarta.servlet.ServletRegistration;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestApplicationServletRegistration extends TomcatBaseTest {

    @Test
    public void testUrlPatternEncoded() {
        doTestUrlPattern(false, "/servlet%");
    }

    @Test
    public void testUrlPatternDecoded() {
        doTestUrlPattern(true, "/servlet%25");
    }

    private void doTestUrlPattern(boolean urlPatternsProvidedInDecodedForm, String expectedPattern) {
        StandardContext context = new StandardContext();
        context.setUrlPatternsProvidedInDecodedForm(urlPatternsProvidedInDecodedForm);

        Wrapper wrapper = context.createWrapper();
        wrapper.setName("servlet");
        context.addChild(wrapper);

        ApplicationServletRegistration registration = new ApplicationServletRegistration(wrapper, context);
        Assert.assertTrue(registration.addMapping("/servlet%25").isEmpty());
        Assert.assertEquals("servlet", context.findServletMapping(expectedPattern));
    }


    @Test
    public void testAddMappingNullAndEmptyPatterns() {
        StandardContext context = new StandardContext();

        Wrapper wrapper = context.createWrapper();
        wrapper.setName("servlet");
        context.addChild(wrapper);

        ApplicationServletRegistration registration = new ApplicationServletRegistration(wrapper, context);

        try {
            registration.addMapping((String[]) null);
            Assert.fail("Expected an IllegalArgumentException for null patterns");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            registration.addMapping();
            Assert.fail("Expected an IllegalArgumentException for an empty pattern array");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            registration.addMapping("");
            Assert.fail("Expected an IllegalArgumentException for an empty pattern");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }


    @Test
    public void testAddMappingWhileContextAvailable() {
        CustomContext context = new CustomContext();
        context.setState(LifecycleState.NEW);

        Wrapper wrapper = context.createWrapper();
        wrapper.setName("servlet");
        context.addChild(wrapper);

        context.setState(LifecycleState.STARTED);

        ApplicationServletRegistration registration = new ApplicationServletRegistration(wrapper, context);

        try {
            registration.addMapping("/servlet");
            Assert.fail("Expected an IllegalStateException once the context is available");
        } catch (IllegalStateException e) {
            // Expected
        }
    }


    @Test
    public void testModificationAfterContextInitialised() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = getProgrammaticRootContext();
        Tomcat.addServlet(root, "servlet", new TesterServlet());
        root.addServletMapping("/test", "servlet");

        tomcat.start();

        ServletRegistration registration =
                root.getServletContext().getServletRegistration("servlet");

        try {
            registration.setInitParameter("param", "value");
            Assert.fail("Expected an IllegalStateException after initialisation");
        } catch (IllegalStateException e) {
            // Expected
        }

        try {
            registration.addMapping("/other");
            Assert.fail("Expected an IllegalStateException after initialisation");
        } catch (IllegalStateException e) {
            // Expected
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
