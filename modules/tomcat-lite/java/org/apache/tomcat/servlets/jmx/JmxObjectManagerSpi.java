/*
 */
package org.apache.tomcat.servlets.jmx;

import java.util.logging.Logger;

import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Plugin for integration with JMX.
 * 
 * All objects of interest are registered automatically.
 */
public class JmxObjectManagerSpi extends ObjectManager {
    Registry registry;
    Logger log = Logger.getLogger("JmxObjectManager");
    
    public JmxObjectManagerSpi() {
        registry = Registry.getRegistry(null, null);
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

}
