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

import javax.management.DynamicMBean;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Load and Register StoreConfig MBean <i>Catalina:type=StoreConfig,resource="url"</i>
 */
public class StoreConfigLifecycleListener implements LifecycleListener {
    private static Log log = LogFactory
            .getLog(StoreConfigLifecycleListener.class);

    /**
     * The configuration information registry for our managed beans.
     */
    protected final Registry registry = MBeanUtils.createRegistry();


    IStoreConfig storeConfig;

    private String storeConfigClass = "org.apache.catalina.storeconfig.StoreConfig";

    private String storeRegistry = null;

    /*
     * register StoreRegistry after Start the complete Server
     *
     * @see org.apache.catalina.LifecycleListener#lifecycleEvent(org.apache.catalina.LifecycleEvent)
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (Lifecycle.AFTER_START_EVENT.equals(event.getType())) {
            if (event.getSource() instanceof StandardServer) {
                createMBean((StandardServer) event.getSource());
            }
        }
    }

    /**
     * create StoreConfig MBean and load StoreRgistry MBeans name is
     * <i>Catalina:type=StoreConfig </i>
     */
    protected void createMBean(StandardServer server) {
        StoreLoader loader = new StoreLoader();
        try {
            Class<?> clazz = Class.forName(getStoreConfigClass(), true, this
                    .getClass().getClassLoader());
            storeConfig = (IStoreConfig) clazz.newInstance();
            if (null == getStoreRegistry())
                // default Loading
                loader.load();
            else
                // load a spezial file registry (url)
                loader.load(getStoreRegistry());
            // use the loader Registry
            storeConfig.setRegistry(loader.getRegistry());
            storeConfig.setServer(server);
        } catch (Exception e) {
            log.error("createMBean load", e);
            return;
        }
        MBeanServer mserver = MBeanUtils.createServer();
        try {
            ObjectName objectName = new ObjectName("Catalina:type=StoreConfig" );
            if (!mserver.isRegistered(objectName)) {
                registry.registerComponent(storeConfig, objectName, "StoreConfig");
            }
        } catch (Exception ex) {
            log.error("createMBean register MBean", ex);
        }
    }

    /**
     * Create a ManagedBean (StoreConfig)
     *
     * @param object
     * @return The bean
     * @throws Exception
     */
    protected DynamicMBean getManagedBean(Object object) throws Exception {
        ManagedBean managedBean = registry.findManagedBean("StoreConfig");
        return managedBean.createMBean(object);
    }

    /**
     * @return Returns the storeConfig.
     */
    public IStoreConfig getStoreConfig() {
        return storeConfig;
    }

    /**
     * @param storeConfig
     *            The storeConfig to set.
     */
    public void setStoreConfig(IStoreConfig storeConfig) {
        this.storeConfig = storeConfig;
    }

    /**
     * @return Returns the storeConfigClass.
     */
    public String getStoreConfigClass() {
        return storeConfigClass;
    }

    /**
     * @param storeConfigClass
     *            The storeConfigClass to set.
     */
    public void setStoreConfigClass(String storeConfigClass) {
        this.storeConfigClass = storeConfigClass;
    }

    /**
     * @return Returns the storeRegistry.
     */
    public String getStoreRegistry() {
        return storeRegistry;
    }

    /**
     * @param storeRegistry
     *            The storeRegistry to set.
     */
    public void setStoreRegistry(String storeRegistry) {
        this.storeRegistry = storeRegistry;
    }
}
