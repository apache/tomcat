/*
 */
package org.apache.tomcat.integration;

import java.util.ArrayList;
import java.util.List;

/**
 * Tomcat is using JMX heavily for monitoring and config - but other 
 * apps embedding tomcat may have different preferences. There
 * is interest to use and better integrate with dependency injection
 * frameworks, OSGI. 
 * 
 * Tomcat will make call to this class when it creates contexts,
 * servlets, connectors - giving a chance to DI frameworks to inject,
 * and to JMX to expose the objects. 
 * 
 * Tomcat will also call this class when it needs a plugin, allowing
 * DI or frameworks to locate the dependency.
 * 
 * @author Costin Manolache
 */
public class ObjectManager {
    
    /** 
     * Attribute used to keep a reference to the object manager 
     * in the context, for the use of servlets. 
     */
    public static final String ATTRIBUTE = "ObjectManager";

    /**
     * Register a named object with the framework. 
     * 
     * For example JMX will expose the object as an MBean.
     * 
     * The framework may inject properties - if it supports that.
     */
    public void bind(String name, Object o) {
        for (ObjectManagerSpi p : providers) {
            p.bind(name, o);
        }
    }

    /** 
     * When an object is no longer in use.
     */
    public void unbind(String name) {
        for (ObjectManagerSpi p : providers) {
            p.unbind(name);
        }        
    }

    /**
     * Create or get a new object with the given name.
     */
    public Object get(String key) {
        for (ObjectManagerSpi p : providers) {
            Object o = p.get(key);
            if (o != null) {
                return o;
            }
        }        
        return null;
    }

    /**
     * Helper for typed get.
     */
    public Object get(Class c) {
        return get(c.getName());
    }

    /**
     * ObjectManager delegates to providers. You can have multiple
     * providers - for example JMX, DI and OSGI at the same time.
     */
    protected List<ObjectManagerSpi> providers = 
        new ArrayList<ObjectManagerSpi>(); 
}
