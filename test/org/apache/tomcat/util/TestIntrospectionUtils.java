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
package org.apache.tomcat.util;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.core.StandardContext;

public class TestIntrospectionUtils {

    // Test for all the classes and interfaces in StandardContext's type hierarchy

    @Test
    public void testIsInstanceStandardContext01() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "org.apache.catalina.core.StandardContext"));
    }


    @Test
    public void testIsInstanceStandardContext02() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "org.apache.catalina.util.LifecycleMBeanBase"));
    }


    @Test
    public void testIsInstanceStandardContext03() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "org.apache.catalina.util.LifecycleBase"));
    }


    @Test
    public void testIsInstanceStandardContext04() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "java.lang.Object"));
    }


    @Test
    public void testIsInstanceStandardContext05() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "org.apache.catalina.Lifecycle"));
    }


    @Test
    public void testIsInstanceStandardContext06() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "org.apache.catalina.JmxEnabled"));
    }


    @Test
    public void testIsInstanceStandardContext07() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "javax.management.MBeanRegistration"));
    }


    @Test
    public void testIsInstanceStandardContext08() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "org.apache.catalina.Container"));
    }


    @Test
    public void testIsInstanceStandardContext09() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "org.apache.tomcat.ContextBind"));
    }


    @Test
    public void testIsInstanceStandardContext10() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "javax.management.NotificationEmitter"));
    }


    @Test
    public void testIsInstanceStandardContext11() {
        Assert.assertTrue(IntrospectionUtils.isInstance(
                StandardContext.class, "javax.management.NotificationBroadcaster"));
    }

    // And one to check that non-matches return false


    @Test
    public void testIsInstanceStandardContext12() {
        Assert.assertFalse(IntrospectionUtils.isInstance(
                StandardContext.class, "com.example.Other"));
    }
}
