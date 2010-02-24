/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.integration.jmx;

import java.lang.management.ManagementFactory;
import java.util.logging.Logger;

import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Plugin for integration with JMX.
 * 
 * All objects of interest are registered automatically.
 */
public class JmxObjectManagerSpi extends ObjectManager implements Runnable {
    Registry registry;
    Logger log = Logger.getLogger("JmxObjectManager");
    
    public JmxObjectManagerSpi() {
        registry = Registry.getRegistry(null, null);
        registry.setMBeanServer(ManagementFactory.getPlatformMBeanServer());
    }
    
    public void bind(String name, Object o) {
        try {
            registry.registerComponent(o, 
                    ":name=\"" + name + "\"", null);
        } catch (Exception e) {
            log.severe("Error registering" + e);
        }
    }

    public void unbind(String name) {
        registry.unregisterComponent(":name=\"" + name + "\"");
    }

    @Override
    public Object get(String key) {
        return null;
    }
    
    ObjectManager om;
    
    public void setObjectManager(ObjectManager om) {
        this.om = om;
    }

    public void run() {
        om.register(this);
        // TODO: register existing objects in JMX
    }
}
