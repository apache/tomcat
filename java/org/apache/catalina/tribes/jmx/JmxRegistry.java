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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.JmxChannel;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class JmxRegistry {

    private static final Log log = LogFactory.getLog(JmxRegistry.class);
    protected static final StringManager sm = StringManager.getManager(JmxRegistry.class);
    private static ConcurrentHashMap<String, JmxRegistry> registryCache = new ConcurrentHashMap<>();

    private MBeanServer mbserver = ManagementFactory.getPlatformMBeanServer();
    private ObjectName baseOname = null;

    private JmxRegistry() {
    }

    public static JmxRegistry getRegistry(Channel channel) {
        if (channel == null || channel.getName() == null) {
            return null;
        }
        JmxRegistry registry = registryCache.get(channel.getName());
        if (registry != null) {
            return registry;
        }

        if (!(channel instanceof JmxChannel)) {
            return null;
        }
        JmxChannel jmxChannel = (JmxChannel) channel;
        if (!jmxChannel.isJmxEnabled()) {
            return null;
        }
        ObjectName baseOn = createBaseObjectName(jmxChannel.getJmxDomain(),
                jmxChannel.getJmxPrefix(), channel.getName());
        if (baseOn == null) {
            return null;
        }
        // create registry
        registry = new JmxRegistry();
        registry.baseOname = baseOn;
        // It doesn't matter if existing object gets over-written. This object
        // holds minimal state and that state will be the same for all objects
        // created for the same channel.
        registryCache.put(channel.getName(), registry);
        return registry;
    }

    public static void removeRegistry(Channel channel, boolean clear) {
        JmxRegistry registry = registryCache.get(channel.getName());
        if (registry == null) {
            return;
        }
        if (clear) {
            registry.clearMBeans();
        }
        registryCache.remove(channel.getName());
    }

    private static ObjectName createBaseObjectName(String domain, String prefix, String name) {
        if (domain == null) {
            log.warn(sm.getString("jmxRegistry.no.domain"));
            return null;
        }
        ObjectName on = null;
        StringBuilder sb = new StringBuilder(domain);
        sb.append(':');
        sb.append(prefix);
        sb.append("type=Channel,channel=");
        sb.append(name);
        try {
            on = new ObjectName(sb.toString());
        } catch (MalformedObjectNameException e) {
            log.error(sm.getString("jmxRegistry.objectName.failed", sb.toString()), e);
        }
        return on;
    }

    public ObjectName registerJmx(String keyprop, Object bean) {
        if (mbserver == null) {
            return null;
        }
        String oNameStr = baseOname.toString() + keyprop;
        ObjectName oName = null;
        try {
            oName = new ObjectName(oNameStr);
            if (mbserver.isRegistered(oName)) {
                mbserver.unregisterMBean(oName);
            }
            mbserver.registerMBean(bean, oName);
        } catch (NotCompliantMBeanException e) {
            log.warn(sm.getString("jmxRegistry.registerJmx.notCompliant", bean), e);
            return null;
        } catch (MalformedObjectNameException e) {
            log.error(sm.getString("jmxRegistry.objectName.failed", oNameStr), e);
            return null;
        } catch (Exception e) {
            log.error(sm.getString("jmxRegistry.registerJmx.failed", bean, oNameStr), e);
            return null;
        }
        return oName;
    }

    public void unregisterJmx(ObjectName oname) {
        if (oname ==null) {
            return;
        }
        try {
            mbserver.unregisterMBean(oname);
        } catch (InstanceNotFoundException e) {
            log.warn(sm.getString("jmxRegistry.unregisterJmx.notFound", oname), e);
        } catch (Exception e) {
            log.warn(sm.getString("jmxRegistry.unregisterJmx.failed", oname), e);
        }
    }

    private void clearMBeans() {
        String query = baseOname.toString() + ",*";
        try {
            ObjectName name = new ObjectName(query);
            Set<ObjectName> onames = mbserver.queryNames(name, null);
            for (ObjectName objectName : onames) {
                unregisterJmx(objectName);
            }
        } catch (MalformedObjectNameException e) {
            log.error(sm.getString("jmxRegistry.objectName.failed", query), e);
        }
    }

}
