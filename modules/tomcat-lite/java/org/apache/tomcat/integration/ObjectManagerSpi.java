/*
 */
package org.apache.tomcat.integration;

/**
 * Base class for framework-integration plugins.
 */
public abstract class ObjectManagerSpi {
    public abstract void bind(String name, Object o);
    
    public abstract void unbind(String name);

    public abstract Object get(String key);

    public void register(ObjectManager om) {
        om.providers.add(this);
    }
}