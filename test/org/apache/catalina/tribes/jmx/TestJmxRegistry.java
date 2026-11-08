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
package org.apache.catalina.tribes.jmx;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.GroupChannel;

public class TestJmxRegistry {

    public interface TestInterceptorMBean {
        // the presence of the companion interface is what makes the interceptor
        // registerable as a Standard MBean, as for the production interceptors
    }

    public static class TestInterceptor extends ChannelInterceptorBase implements TestInterceptorMBean {
    }

    /*
     * A component ObjectName is a stale hint: the registry can be gone when the component stops,
     * because it is removed when the channel MBean is deregistered externally. If the JMX
     * configuration is still valid, the registry is simply recreated and the MBean unregistered.
     */
    @Test
    public void testStopRecreatesRegistryWhenStillConfigured() throws Exception {
        GroupChannel channel = new GroupChannel();
        channel.setName("TestJmxRegistry-" + System.nanoTime());
        MBeanServer mbserver = ManagementFactory.getPlatformMBeanServer();
        try {
            TestInterceptor interceptor = new TestInterceptor();
            interceptor.setChannel(channel);
            interceptor.start(Channel.DEFAULT);
            ObjectName bean = new ObjectName("ClusterChannel:type=Channel,channel=" + channel.getName() +
                    ",component=Interceptor,interceptorName=TestInterceptor");
            Assert.assertTrue(mbserver.isRegistered(bean));

            JmxRegistry.removeRegistry(channel, false);

            interceptor.stop(Channel.DEFAULT);
            Assert.assertNull(interceptor.getChannel());
            Assert.assertFalse(mbserver.isRegistered(bean));
        } finally {
            JmxRegistry.removeRegistry(channel, true);
        }
    }

    /*
     * If the JMX configuration is invalidated after the registry was removed, the components
     * still have to complete their stop and their cleanup. Previously, the missing registry
     * surfaced as an uncaught NullPointerException.
     */
    @Test
    public void testStopWithoutRegistryDoesNotThrow() throws Exception {
        GroupChannel channel = new GroupChannel();
        channel.setName("TestJmxRegistry-" + System.nanoTime());
        MBeanServer mbserver = ManagementFactory.getPlatformMBeanServer();
        ObjectName bean = new ObjectName("ClusterChannel:type=Channel,channel=" + channel.getName() +
                ",component=Interceptor,interceptorName=TestInterceptor");
        try {
            TestInterceptor interceptor = new TestInterceptor();
            interceptor.setChannel(channel);
            interceptor.start(Channel.DEFAULT);
            Assert.assertTrue(mbserver.isRegistered(bean));

            // Simulate the channel MBean being deregistered externally, which removes the
            // registry, followed by an invalidation of the JMX configuration
            JmxRegistry.removeRegistry(channel, false);
            channel.setJmxEnabled(false);

            interceptor.stop(Channel.DEFAULT);
            Assert.assertNull(interceptor.getChannel());
        } finally {
            // Without a registry the stop path cannot unregister the bean any more, clean it up
            // here so the shared MBean server is not polluted
            if (mbserver.isRegistered(bean)) {
                mbserver.unregisterMBean(bean);
            }
            JmxRegistry.removeRegistry(channel, true);
        }
    }

    /*
     * Removing the registry of a channel that never had one, or that cannot have one, must degrade
     * to a no-op, as getRegistry does, rather than throw.
     */
    @Test
    public void testRemoveRegistryNullGuards() throws Exception {
        JmxRegistry.removeRegistry(null, false);
        JmxRegistry.removeRegistry(null, true);

        GroupChannel channel = new GroupChannel();
        JmxRegistry.removeRegistry(channel, false);
        JmxRegistry.removeRegistry(channel, true);
    }
}
