/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import java.util.HashMap;
import java.util.Hashtable;
import java.io.Serializable;


/**
 * Holds and manages the naming resources defined in the J2EE Enterprise 
 * Naming Context and their associated JNDI context.
 *
 * @author Remy Maucherat
 * @version $Revision: 303133 $ $Date: 2004-08-29 18:46:15 +0200 (dim., 29 août 2004) $
 */

public class NamingResources implements Serializable {


    // ----------------------------------------------------------- Constructors


    /**
     * Create a new NamingResources instance.
     */
    public NamingResources() {
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
    private Hashtable entries = new Hashtable();


    /**
     * The EJB resource references for this web application, keyed by name.
     */
    private HashMap ejbs = new HashMap();


    /**
     * The environment entries for this web application, keyed by name.
     */
    private HashMap envs = new HashMap();


    /**
     * The local  EJB resource references for this web application, keyed by
     * name.
     */
    private HashMap localEjbs = new HashMap();


    /**
     * The message destination referencess for this web application,
     * keyed by name.
     */
    private HashMap mdrs = new HashMap();


    /**
     * The resource environment references for this web application,
     * keyed by name.
     */
    private HashMap resourceEnvRefs = new HashMap();


    /**
     * The resource references for this web application, keyed by name.
     */
    private HashMap resources = new HashMap();


    /**
     * The resource links for this web application, keyed by name.
     */
    private HashMap resourceLinks = new HashMap();


    /**
     * The web service references for this web application, keyed by name.
     */
    private HashMap services = new HashMap();


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
            return;
        } else {
            entries.put(environment.getName(), environment.getType());
        }

        synchronized (envs) {
            environment.setNamingResources(this);
            envs.put(environment.getName(), environment);
        }
        support.firePropertyChange("environment", null, environment);

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

        synchronized (localEjbs) {
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
            Object value = resourceLink.getType();
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
            entries.put(service.getName(), service.getType());
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
            return ((ContextEjb) ejbs.get(name));
        }

    }


    /**
     * Return the defined EJB resource references for this application.
     * If there are none, a zero-length array is returned.
     */
    public ContextEjb[] findEjbs() {

        synchronized (ejbs) {
            ContextEjb results[] = new ContextEjb[ejbs.size()];
            return ((ContextEjb[]) ejbs.values().toArray(results));
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
            return ((ContextEnvironment) envs.get(name));
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
            return ((ContextEnvironment[]) envs.values().toArray(results));
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
            return ((ContextLocalEjb) localEjbs.get(name));
        }

    }


    /**
     * Return the defined local EJB resource references for this application.
     * If there are none, a zero-length array is returned.
     */
    public ContextLocalEjb[] findLocalEjbs() {

        synchronized (localEjbs) {
            ContextLocalEjb results[] = new ContextLocalEjb[localEjbs.size()];
            return ((ContextLocalEjb[]) localEjbs.values().toArray(results));
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
            return ((MessageDestinationRef) mdrs.get(name));
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
            return ((MessageDestinationRef[]) mdrs.values().toArray(results));
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
            return ((ContextResource) resources.get(name));
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
            return ((ContextResourceLink) resourceLinks.get(name));
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
            return ((ContextResourceLink[]) resourceLinks.values()
                    .toArray(results));
        }

    }


    /**
     * Return the defined resource references for this application.  If
     * none have been defined, a zero-length array is returned.
     */
    public ContextResource[] findResources() {

        synchronized (resources) {
            ContextResource results[] = new ContextResource[resources.size()];
            return ((ContextResource[]) resources.values().toArray(results));
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
            return ((ContextResourceEnvRef) resourceEnvRefs.get(name));
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
            return ((ContextResourceEnvRef[]) resourceEnvRefs.values().toArray(results));
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
            return ((ContextService) services.get(name));
        }

    }


    /**
     * Return the defined web service references for this application.  If
     * none have been defined, a zero-length array is returned.
     */
    public ContextService[] findServices() {
        
        synchronized (services) {
            ContextService results[] = new ContextService[services.size()];
            return ((ContextService[]) services.values().toArray(results));
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
            ejb = (ContextEjb) ejbs.remove(name);
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
            environment = (ContextEnvironment) envs.remove(name);
        }
        if (environment != null) {
            support.firePropertyChange("environment", environment, null);
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
            localEjb = (ContextLocalEjb) ejbs.remove(name);
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
            mdr = (MessageDestinationRef) mdrs.remove(name);
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
            resource = (ContextResource) resources.remove(name);
        }
        if (resource != null) {
            support.firePropertyChange("resource", resource, null);
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

        String type = null;
        synchronized (resourceEnvRefs) {
            type = (String) resourceEnvRefs.remove(name);
        }
        if (type != null) {
            support.firePropertyChange("resourceEnvRef",
                                       name + ":" + type, null);
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
            resourceLink = (ContextResourceLink) resourceLinks.remove(name);
        }
        if (resourceLink != null) {
            support.firePropertyChange("resourceLink", resourceLink, null);
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
            service = (ContextService) services.remove(name);
        }
        if (service != null) {
            support.firePropertyChange("service", service, null);
            service.setNamingResources(null);
        }
        
    }


}
