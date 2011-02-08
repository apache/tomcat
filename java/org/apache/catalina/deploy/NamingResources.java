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


package org.apache.catalina.deploy;


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Hashtable;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * Holds and manages the naming resources defined in the J2EE Enterprise 
 * Naming Context and their associated JNDI context.
 *
 * @author Remy Maucherat
 * @version $Id$
 */

public class NamingResources extends LifecycleMBeanBase implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private static final Log log = LogFactory.getLog(NamingResources.class);
    
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    private volatile boolean resourceRequireExplicitRegistration = false;

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new NamingResources instance.
     */
    public NamingResources() {
        // NOOP
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Associated container object.
     */
    private Object container = null;


    /**
     * List of naming entries, keyed by name. The value is the entry type, as
     * declared by the user.
     */
    private Hashtable<String, String> entries =
        new Hashtable<String, String>();


    /**
     * The EJB resource references for this web application, keyed by name.
     */
    private HashMap<String, ContextEjb> ejbs =
        new HashMap<String, ContextEjb>();


    /**
     * The environment entries for this web application, keyed by name.
     */
    private HashMap<String, ContextEnvironment> envs =
        new HashMap<String, ContextEnvironment>();


    /**
     * The local  EJB resource references for this web application, keyed by
     * name.
     */
    private HashMap<String, ContextLocalEjb> localEjbs =
        new HashMap<String, ContextLocalEjb>();


    /**
     * The message destination referencess for this web application,
     * keyed by name.
     */
    private HashMap<String, MessageDestinationRef> mdrs =
        new HashMap<String, MessageDestinationRef>();


    /**
     * The resource environment references for this web application,
     * keyed by name.
     */
    private HashMap<String, ContextResourceEnvRef> resourceEnvRefs =
        new HashMap<String, ContextResourceEnvRef>();


    /**
     * The resource references for this web application, keyed by name.
     */
    private HashMap<String, ContextResource> resources =
        new HashMap<String, ContextResource>();


    /**
     * The resource links for this web application, keyed by name.
     */
    private HashMap<String, ContextResourceLink> resourceLinks =
        new HashMap<String, ContextResourceLink>();


    /**
     * The web service references for this web application, keyed by name.
     */
    private HashMap<String, ContextService> services =
        new HashMap<String, ContextService>();


    /**
     * The transaction for this webapp.
     */
    private ContextTransaction transaction = null;


    /**
     * The property change support for this component.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);


    // ------------------------------------------------------------- Properties


    /**
     * Get the container with which the naming resources are associated.
     */
    public Object getContainer() {
        return container;
    }


    /**
     * Set the container with which the naming resources are associated.
     */
    public void setContainer(Object container) {
        this.container = container;
    }

    
    /**
     * Set the transaction object.
     */
    public void setTransaction(ContextTransaction transaction) {
        this.transaction = transaction;
    }
    

    /**
     * Get the transaction object.
     */
    public ContextTransaction getTransaction() {
        return transaction;
    }
    

    /**
     * Add an EJB resource reference for this web application.
     *
     * @param ejb New EJB resource reference
     */
    public void addEjb(ContextEjb ejb) {

        if (entries.containsKey(ejb.getName())) {
            return;
        } else {
            entries.put(ejb.getName(), ejb.getType());
        }

        synchronized (ejbs) {
            ejb.setNamingResources(this);
            ejbs.put(ejb.getName(), ejb);
        }
        support.firePropertyChange("ejb", null, ejb);

    }


    /**
     * Add an environment entry for this web application.
     *
     * @param environment New environment entry
     */
    public void addEnvironment(ContextEnvironment environment) {

        if (entries.containsKey(environment.getName())) {
            ContextEnvironment ce = findEnvironment(environment.getName());
            ContextResourceLink rl = findResourceLink(environment.getName());
            if (ce != null) {
                if (ce.getOverride()) {
                    removeEnvironment(environment.getName());
                } else {
                    return;
                }
            } else if (rl != null) {
                // Link. Need to look at the global resources
                NamingResources global = getServer().getGlobalNamingResources();
                if (global.findEnvironment(rl.getGlobal()) != null) {
                    if (global.findEnvironment(rl.getGlobal()).getOverride()) {
                        removeResourceLink(environment.getName());
                    } else {
                        return;
                    }
                }
            } else {
                // It exists but it isn't an env or a res link...
                return;
            }
        }
        
        entries.put(environment.getName(), environment.getType());

        synchronized (envs) {
            environment.setNamingResources(this);
            envs.put(environment.getName(), environment);
        }
        support.firePropertyChange("environment", null, environment);

        // Register with JMX
        if (resourceRequireExplicitRegistration) {
            try {
                MBeanUtils.createMBean(environment);
            } catch (Exception e) {
                log.warn(sm.getString("namingResources.mbeanCreateFail",
                        environment.getName()), e);
            }
        }
    }

    // Container should be an instance of Server or Context. If it is anything
    // else, return null which will trigger a NPE.
    private Server getServer() {
        if (container instanceof Server) {
            return (Server) container;
        }
        if (container instanceof Context) {
            // Could do this in one go. Lots of casts so split out for clarity
            Engine engine =
                (Engine) ((Context) container).getParent().getParent();
            return engine.getService().getServer();
        }
        return null;
    }

    /**
     * Add a local EJB resource reference for this web application.
     *
     * @param ejb New EJB resource reference
     */
    public void addLocalEjb(ContextLocalEjb ejb) {

        if (entries.containsKey(ejb.getName())) {
            return;
        } else {
            entries.put(ejb.getName(), ejb.getType());
        }

        synchronized (localEjbs) {
            ejb.setNamingResources(this);
            localEjbs.put(ejb.getName(), ejb);
        }
        support.firePropertyChange("localEjb", null, ejb);

    }


    /**
     * Add a message destination reference for this web application.
     *
     * @param mdr New message destination reference
     */
    public void addMessageDestinationRef(MessageDestinationRef mdr) {

        if (entries.containsKey(mdr.getName())) {
            return;
        } else {
            entries.put(mdr.getName(), mdr.getType());
        }

        synchronized (mdrs) {
            mdr.setNamingResources(this);
            mdrs.put(mdr.getName(), mdr);
        }
        support.firePropertyChange("messageDestinationRef", null, mdr);

    }


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

    }


    /**
     * Add a resource reference for this web application.
     *
     * @param resource New resource reference
     */
    public void addResource(ContextResource resource) {

        if (entries.containsKey(resource.getName())) {
            return;
        } else {
            entries.put(resource.getName(), resource.getType());
        }

        synchronized (resources) {
            resource.setNamingResources(this);
            resources.put(resource.getName(), resource);
        }
        support.firePropertyChange("resource", null, resource);

        // Register with JMX
        if (resourceRequireExplicitRegistration) {
            try {
                MBeanUtils.createMBean(resource);
            } catch (Exception e) {
                log.warn(sm.getString("namingResources.mbeanCreateFail",
                        resource.getName()), e);
            }
        }
    }


    /**
     * Add a resource environment reference for this web application.
     *
     * @param resource The resource
     */
    public void addResourceEnvRef(ContextResourceEnvRef resource) {

        if (entries.containsKey(resource.getName())) {
            return;
        } else {
            entries.put(resource.getName(), resource.getType());
        }

        synchronized (resourceEnvRefs) {
            resource.setNamingResources(this);
            resourceEnvRefs.put(resource.getName(), resource);
        }
        support.firePropertyChange("resourceEnvRef", null, resource);

    }


    /**
     * Add a resource link for this web application.
     *
     * @param resourceLink New resource link
     */
    public void addResourceLink(ContextResourceLink resourceLink) {

        if (entries.containsKey(resourceLink.getName())) {
            return;
        } else {
            String value = resourceLink.getType();
            if (value == null) {
                value = "";
            }
            entries.put(resourceLink.getName(), value);
        }

        synchronized (resourceLinks) {
            resourceLink.setNamingResources(this);
            resourceLinks.put(resourceLink.getName(), resourceLink);
        }
        support.firePropertyChange("resourceLink", null, resourceLink);

        // Register with JMX
        if (resourceRequireExplicitRegistration) {
            try {
                MBeanUtils.createMBean(resourceLink);
            } catch (Exception e) {
                log.warn(sm.getString("namingResources.mbeanCreateFail",
                        resourceLink.getName()), e);
            }
        }
    }


    /**
     * Add a web service reference for this web application.
     *
     * @param service New web service reference
     */
    public void addService(ContextService service) {

        if (entries.containsKey(service.getName())) {
            return;
        } else {
            String value = service.getType();
            if (value == null) {
                value = "";
            }
            entries.put(service.getName(), value);
        }
        
        synchronized (services) {
            service.setNamingResources(this);
            services.put(service.getName(), service);
        }
        support.firePropertyChange("service", null, service);
        
    }


    /**
     * Return the EJB resource reference with the specified name, if any;
     * otherwise, return <code>null</code>.
     *
     * @param name Name of the desired EJB resource reference
     */
    public ContextEjb findEjb(String name) {

        synchronized (ejbs) {
            return ejbs.get(name);
        }

    }


    /**
     * Return the defined EJB resource references for this application.
     * If there are none, a zero-length array is returned.
     */
    public ContextEjb[] findEjbs() {

        synchronized (ejbs) {
            ContextEjb results[] = new ContextEjb[ejbs.size()];
            return ejbs.values().toArray(results);
        }

    }


    /**
     * Return the environment entry with the specified name, if any;
     * otherwise, return <code>null</code>.
     *
     * @param name Name of the desired environment entry
     */
    public ContextEnvironment findEnvironment(String name) {

        synchronized (envs) {
            return envs.get(name);
        }

    }


    /**
     * Return the set of defined environment entries for this web
     * application.  If none have been defined, a zero-length array
     * is returned.
     */
    public ContextEnvironment[] findEnvironments() {

        synchronized (envs) {
            ContextEnvironment results[] = new ContextEnvironment[envs.size()];
            return envs.values().toArray(results);
        }

    }


    /**
     * Return the local EJB resource reference with the specified name, if any;
     * otherwise, return <code>null</code>.
     *
     * @param name Name of the desired EJB resource reference
     */
    public ContextLocalEjb findLocalEjb(String name) {

        synchronized (localEjbs) {
            return localEjbs.get(name);
        }

    }


    /**
     * Return the defined local EJB resource references for this application.
     * If there are none, a zero-length array is returned.
     */
    public ContextLocalEjb[] findLocalEjbs() {

        synchronized (localEjbs) {
            ContextLocalEjb results[] = new ContextLocalEjb[localEjbs.size()];
            return localEjbs.values().toArray(results);
        }

    }


    /**
     * Return the message destination reference with the specified name,
     * if any; otherwise, return <code>null</code>.
     *
     * @param name Name of the desired message destination reference
     */
    public MessageDestinationRef findMessageDestinationRef(String name) {

        synchronized (mdrs) {
            return mdrs.get(name);
        }

    }


    /**
     * Return the defined message destination references for this application.
     * If there are none, a zero-length array is returned.
     */
    public MessageDestinationRef[] findMessageDestinationRefs() {

        synchronized (mdrs) {
            MessageDestinationRef results[] =
                new MessageDestinationRef[mdrs.size()];
            return mdrs.values().toArray(results);
        }

    }


    /**
     * Return the resource reference with the specified name, if any;
     * otherwise return <code>null</code>.
     *
     * @param name Name of the desired resource reference
     */
    public ContextResource findResource(String name) {

        synchronized (resources) {
            return resources.get(name);
        }

    }


    /**
     * Return the resource link with the specified name, if any;
     * otherwise return <code>null</code>.
     *
     * @param name Name of the desired resource link
     */
    public ContextResourceLink findResourceLink(String name) {

        synchronized (resourceLinks) {
            return resourceLinks.get(name);
        }

    }


    /**
     * Return the defined resource links for this application.  If
     * none have been defined, a zero-length array is returned.
     */
    public ContextResourceLink[] findResourceLinks() {

        synchronized (resourceLinks) {
            ContextResourceLink results[] = 
                new ContextResourceLink[resourceLinks.size()];
            return resourceLinks.values().toArray(results);
        }

    }


    /**
     * Return the defined resource references for this application.  If
     * none have been defined, a zero-length array is returned.
     */
    public ContextResource[] findResources() {

        synchronized (resources) {
            ContextResource results[] = new ContextResource[resources.size()];
            return resources.values().toArray(results);
        }

    }


    /**
     * Return the resource environment reference type for the specified
     * name, if any; otherwise return <code>null</code>.
     *
     * @param name Name of the desired resource environment reference
     */
    public ContextResourceEnvRef findResourceEnvRef(String name) {

        synchronized (resourceEnvRefs) {
            return resourceEnvRefs.get(name);
        }

    }


    /**
     * Return the set of resource environment reference names for this
     * web application.  If none have been specified, a zero-length
     * array is returned.
     */
    public ContextResourceEnvRef[] findResourceEnvRefs() {

        synchronized (resourceEnvRefs) {
            ContextResourceEnvRef results[] = new ContextResourceEnvRef[resourceEnvRefs.size()];
            return resourceEnvRefs.values().toArray(results);
        }

    }


    /**
     * Return the web service reference for the specified
     * name, if any; otherwise return <code>null</code>.
     *
     * @param name Name of the desired web service
     */
    public ContextService findService(String name) {

        synchronized (services) {
            return services.get(name);
        }

    }


    /**
     * Return the defined web service references for this application.  If
     * none have been defined, a zero-length array is returned.
     */
    public ContextService[] findServices() {
        
        synchronized (services) {
            ContextService results[] = new ContextService[services.size()];
            return services.values().toArray(results);
        }
        
    }


    /**
     * Return true if the name specified already exists.
     */
    public boolean exists(String name) {

        return (entries.containsKey(name));

    }


    /**
     * Remove any EJB resource reference with the specified name.
     *
     * @param name Name of the EJB resource reference to remove
     */
    public void removeEjb(String name) {

        entries.remove(name);

        ContextEjb ejb = null;
        synchronized (ejbs) {
            ejb = ejbs.remove(name);
        }
        if (ejb != null) {
            support.firePropertyChange("ejb", ejb, null);
            ejb.setNamingResources(null);
        }

    }


    /**
     * Remove any environment entry with the specified name.
     *
     * @param name Name of the environment entry to remove
     */
    public void removeEnvironment(String name) {

        entries.remove(name);

        ContextEnvironment environment = null;
        synchronized (envs) {
            environment = envs.remove(name);
        }
        if (environment != null) {
            support.firePropertyChange("environment", environment, null);
            // De-register with JMX
            if (resourceRequireExplicitRegistration) {
                try {
                    MBeanUtils.destroyMBean(environment);
                } catch (Exception e) {
                    log.warn(sm.getString("namingResources.mbeanDestroyFail",
                            environment.getName()), e);
                }
            }
            environment.setNamingResources(null);
        }
    }


    /**
     * Remove any local EJB resource reference with the specified name.
     *
     * @param name Name of the EJB resource reference to remove
     */
    public void removeLocalEjb(String name) {

        entries.remove(name);

        ContextLocalEjb localEjb = null;
        synchronized (localEjbs) {
            localEjb = localEjbs.remove(name);
        }
        if (localEjb != null) {
            support.firePropertyChange("localEjb", localEjb, null);
            localEjb.setNamingResources(null);
        }

    }


    /**
     * Remove any message destination reference with the specified name.
     *
     * @param name Name of the message destination resource reference to remove
     */
    public void removeMessageDestinationRef(String name) {

        entries.remove(name);

        MessageDestinationRef mdr = null;
        synchronized (mdrs) {
            mdr = mdrs.remove(name);
        }
        if (mdr != null) {
            support.firePropertyChange("messageDestinationRef",
                                       mdr, null);
            mdr.setNamingResources(null);
        }

    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }


    /**
     * Remove any resource reference with the specified name.
     *
     * @param name Name of the resource reference to remove
     */
    public void removeResource(String name) {

        entries.remove(name);

        ContextResource resource = null;
        synchronized (resources) {
            resource = resources.remove(name);
        }
        if (resource != null) {
            support.firePropertyChange("resource", resource, null);
            // De-register with JMX
            if (resourceRequireExplicitRegistration) {
                try {
                    MBeanUtils.destroyMBean(resource);
                } catch (Exception e) {
                    log.warn(sm.getString("namingResources.mbeanDestroyFail",
                            resource.getName()), e);
                }
            }
            resource.setNamingResources(null);
        }
    }


    /**
     * Remove any resource environment reference with the specified name.
     *
     * @param name Name of the resource environment reference to remove
     */
    public void removeResourceEnvRef(String name) {

        entries.remove(name);

        ContextResourceEnvRef resourceEnvRef = null;
        synchronized (resourceEnvRefs) {
            resourceEnvRef =
                resourceEnvRefs.remove(name);
        }
        if (resourceEnvRef != null) {
            support.firePropertyChange("resourceEnvRef", resourceEnvRef, null);
            resourceEnvRef.setNamingResources(null);
        }

    }


    /**
     * Remove any resource link with the specified name.
     *
     * @param name Name of the resource link to remove
     */
    public void removeResourceLink(String name) {

        entries.remove(name);

        ContextResourceLink resourceLink = null;
        synchronized (resourceLinks) {
            resourceLink = resourceLinks.remove(name);
        }
        if (resourceLink != null) {
            support.firePropertyChange("resourceLink", resourceLink, null);
            // De-register with JMX
            if (resourceRequireExplicitRegistration) {
                try {
                    MBeanUtils.destroyMBean(resourceLink);
                } catch (Exception e) {
                    log.warn(sm.getString("namingResources.mbeanDestroyFail",
                            resourceLink.getName()), e);
                }
            }
            resourceLink.setNamingResources(null);
        }
    }


    /**
     * Remove any web service reference with the specified name.
     *
     * @param name Name of the web service reference to remove
     */
    public void removeService(String name) {
        
        entries.remove(name);
        
        ContextService service = null;
        synchronized (services) {
            service = services.remove(name);
        }
        if (service != null) {
            support.firePropertyChange("service", service, null);
            service.setNamingResources(null);
        }
        
    }


    // ------------------------------------------------------- Lifecycle methods
    
    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        
        // Set this before we register currently known naming resources to avoid
        // timing issues. Duplication registration is not an issue.
        resourceRequireExplicitRegistration = true;
        
        for (ContextResource cr : resources.values()) {
            try {
                MBeanUtils.createMBean(cr);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanCreateFail", cr.getName()), e);
            }
        }
        
        for (ContextEnvironment ce : envs.values()) {
            try {
                MBeanUtils.createMBean(ce);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanCreateFail", ce.getName()), e);
            }
        }
        
        for (ContextResourceLink crl : resourceLinks.values()) {
            try {
                MBeanUtils.createMBean(crl);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanCreateFail", crl.getName()), e);
            }
        }
    }


    @Override
    protected void startInternal() throws LifecycleException {
        fireLifecycleEvent(CONFIGURE_START_EVENT, null);
        setState(LifecycleState.STARTING);
    }


    @Override
    protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
        fireLifecycleEvent(CONFIGURE_STOP_EVENT, null);
    }

    
    @Override
    protected void destroyInternal() throws LifecycleException {

        // Set this before we de-register currently known naming resources to
        // avoid timing issues. Duplication de-registration is not an issue.
        resourceRequireExplicitRegistration = false;

        // Destroy in reverse order to create, although it should not matter
        for (ContextResourceLink crl : resourceLinks.values()) {
            try {
                MBeanUtils.destroyMBean(crl);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanDestroyFail", crl.getName()), e);
            }
        }
        
        for (ContextEnvironment ce : envs.values()) {
            try {
                MBeanUtils.destroyMBean(ce);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanDestroyFail", ce.getName()), e);
            }
        }
        
        for (ContextResource cr : resources.values()) {
            try {
                MBeanUtils.destroyMBean(cr);
            } catch (Exception e) {
                log.warn(sm.getString(
                        "namingResources.mbeanDestroyFail", cr.getName()), e);
            }
        }
        
        super.destroyInternal();
    }


    @Override
    protected String getDomainInternal() {
        // Use the same domain as our associated container if we have one
        Object c = getContainer();
        
        if (c instanceof LifecycleMBeanBase) {
            return ((LifecycleMBeanBase) c).getDomain();
        }

        return null;
    }


    @Override
    protected String getObjectNameKeyProperties() {
        Object c = getContainer();
        if (c instanceof Container) {
            return "type=NamingResources" +
                    MBeanUtils.getContainerKeyProperties((Container) c);
        }
        // Server or just unknown
        return "type=NamingResources";
    }
}
