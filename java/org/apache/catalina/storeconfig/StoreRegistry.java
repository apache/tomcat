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

import java.util.HashMap;
import java.util.Map;

import javax.naming.directory.DirContext;

import org.apache.catalina.CredentialHandler;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterDeployer;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.ChannelSender;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.MessageListener;
import org.apache.catalina.tribes.transport.DataSender;
import org.apache.coyote.UpgradeProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.CookieProcessor;
import org.apache.tomcat.util.res.StringManager;

/**
 * Central StoreRegistry for all server.xml elements
 */
public class StoreRegistry {
    private static Log log = LogFactory.getLog(StoreRegistry.class);
    private static StringManager sm = StringManager.getManager(StoreRegistry.class);

    private Map<String, StoreDescription> descriptors = new HashMap<>();

    private String encoding = "UTF-8";

    private String name;

    private String version;

    // Access Information
    private static Class<?> interfaces[] = { CatalinaCluster.class,
            ChannelSender.class, ChannelReceiver.class, Channel.class,
            MembershipService.class, ClusterDeployer.class, Realm.class,
            Manager.class, DirContext.class, LifecycleListener.class,
            Valve.class, ClusterListener.class, MessageListener.class,
            DataSender.class, ChannelInterceptor.class, Member.class,
            WebResourceRoot.class, WebResourceSet.class,
            CredentialHandler.class, UpgradeProtocol.class,
            CookieProcessor.class };

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name The name to set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version The version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Find a description for id. Handle interface search when no direct match
     * found.
     *
     * @param id The class name
     * @return the description
     */
    public StoreDescription findDescription(String id) {
        if (log.isDebugEnabled()) {
            log.debug("search descriptor " + id);
        }
        StoreDescription desc = descriptors.get(id);
        if (desc == null) {
            Class<?> aClass = null;
            try {
                aClass = Class.forName(id, true, this.getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                log.error(sm.getString("registry.loadClassFailed", id), e);
            }
            if (aClass != null) {
                desc = descriptors.get(aClass.getName());
                for (int i = 0; desc == null && i < interfaces.length; i++) {
                    if (interfaces[i].isAssignableFrom(aClass)) {
                        desc = descriptors.get(interfaces[i].getName());
                    }
                }
            }
        }
        if (log.isDebugEnabled()) {
            if (desc != null) {
                log.debug("find descriptor " + id + "#" + desc.getTag() + "#"
                        + desc.getStoreFactoryClass());
            } else {
                log.debug(("Can't find descriptor for key " + id));
            }
        }
        return desc;
    }

    /**
     * Find Description by class.
     *
     * @param aClass The class
     * @return the description
     */
    public StoreDescription findDescription(Class<?> aClass) {
        return findDescription(aClass.getName());
    }

    /**
     * Find factory from class name.
     *
     * @param aClassName The class name
     * @return the factory
     */
    public IStoreFactory findStoreFactory(String aClassName) {
        StoreDescription desc = findDescription(aClassName);
        if (desc != null) {
            return desc.getStoreFactory();
        } else {
            return null;
        }

    }

    /**
     * Find factory from class.
     *
     * @param aClass The class
     * @return the factory
     */
    public IStoreFactory findStoreFactory(Class<?> aClass) {
        return findStoreFactory(aClass.getName());
    }

    /**
     * Register a new description.
     *
     * @param desc New description
     */
    public void registerDescription(StoreDescription desc) {
        String key = desc.getId();
        if (key == null || key.isEmpty()) {
            key = desc.getTagClass();
        }
        descriptors.put(key, desc);
        if (log.isDebugEnabled()) {
            log.debug("register store descriptor " + key + "#" + desc.getTag()
                    + "#" + desc.getTagClass());
        }
    }

    /**
     * Unregister a description.
     *
     * @param desc The description
     * @return the description, or <code>null</code> if it was not registered
     */
    public StoreDescription unregisterDescription(StoreDescription desc) {
        String key = desc.getId();
        if (key == null || "".equals(key)) {
            key = desc.getTagClass();
        }
        return descriptors.remove(key);
    }

    // Attributes

    /**
     * @return the encoding
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Set the encoding to use when writing the configuration files.
     * @param string The encoding
     */
    public void setEncoding(String string) {
        encoding = string;
    }

}
