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

package org.apache.catalina.mbeans;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.management.MBeanException;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.deploy.ContextEnvironment;
import org.apache.catalina.deploy.ContextResource;
import org.apache.catalina.deploy.ContextResourceLink;
import org.apache.catalina.deploy.NamingResources;
import org.apache.jasper.util.ExceptionUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * Implementation of <code>LifecycleListener</code> that
 * instantiates the set of MBeans associated with the components of a
 * running instance of Catalina.
 *
 * @author Craig R. McClanahan
 * @author Amy Roh
 * @version $Id$
 * 
 * TODO: Is this still required? Possibly for resources. Need to check.
 */
public class ServerLifecycleListener
    implements ContainerListener, LifecycleListener, PropertyChangeListener {

    private static final Log log = LogFactory.getLog(ServerLifecycleListener.class);


    // ------------------------------------------------------------- Properties


    /**
     * Semicolon separated list of paths containing MBean descriptor resources.
     */
    protected String descriptors = null;

    public String getDescriptors() {
        return (this.descriptors);
    }

    public void setDescriptors(String descriptors) {
        this.descriptors = descriptors;
    }


    // ---------------------------------------------- ContainerListener Methods


    /**
     * Handle a <code>ContainerEvent</code> from one of the Containers we are
     * interested in.
     *
     * @param event The event that has occurred
     */
    public void containerEvent(ContainerEvent event) {

        try {
            String type = event.getType();
            if (Container.ADD_CHILD_EVENT.equals(type)) {
                processContainerAddChild(event.getContainer(),
                                         (Container) event.getData());
            } else if (Container.REMOVE_CHILD_EVENT.equals(type)) {
                processContainerRemoveChild(event.getContainer(),
                                            (Container) event.getData());
            }
        } catch (Exception e) {
            log.error("Exception processing event " + event, e);
        }

    }


    // ---------------------------------------------- LifecycleListener Methods


    /**
     * Primary entry point for startup and shutdown events.
     *
     * @param event The event that has occurred
     */
    public void lifecycleEvent(LifecycleEvent event) {

        Lifecycle lifecycle = event.getLifecycle();
        if (Lifecycle.START_EVENT.equals(event.getType())) {

            try {

                if (lifecycle instanceof Server) {
                    MBeanFactory factory = new MBeanFactory();
                    factory.setContainer(lifecycle);
                    createMBeans(factory);
                    createMBeans((Server) lifecycle);
                }

                if( lifecycle instanceof Service ) {
                    MBeanFactory factory = new MBeanFactory();
                    factory.setContainer(lifecycle);
                    createMBeans(factory);
                    createMBeans((Service)lifecycle);
                }

            } catch (MBeanException t) {

                Exception e = t.getTargetException();
                if (e == null)
                    e = t;
                log.error("createMBeans: MBeanException", e);

            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error("createMBeans: Throwable", t);
            }

             /*
            // Ignore events from StandardContext objects to avoid
            // reregistering the context
            if (lifecycle instanceof StandardContext)
                return;
            createMBeans();
            */

        } else if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType())) {
            try {
                if (lifecycle instanceof Server) {
                    destroyMBeans((Server)lifecycle);
                }
                if (lifecycle instanceof Service) {
                    destroyMBeans((Service)lifecycle);
                }
            } catch (MBeanException t) {

                Exception e = t.getTargetException();
                if (e == null) {
                    e = t;
                }
                log.error("destroyMBeans: MBeanException", e);

            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error("destroyMBeans: Throwable", t);
            }
            // FIXME: RMI adaptor should be stopped; however, this is
            // undocumented in MX4J, and reports exist in the MX4J bug DB that
            // this doesn't work

        }

        if ((Context.RELOAD_EVENT.equals(event.getType()))
            || (Lifecycle.START_EVENT.equals(event.getType()))) {

            // Give context a new handle to the MBean server if the
            // context has been reloaded since reloading causes the
            // context to lose its previous handle to the server
            if (lifecycle instanceof StandardContext) {
                // If the context is privileged, give a reference to it
                // in a servlet context attribute
                StandardContext context = (StandardContext)lifecycle;
                if (context.getPrivileged()) {
                    context.getServletContext().setAttribute
                        (Globals.MBEAN_REGISTRY_ATTR,
                         MBeanUtils.createRegistry());
                    context.getServletContext().setAttribute
                        (Globals.MBEAN_SERVER_ATTR,
                         MBeanUtils.createServer());
                }
            }

        }

    }


    // ----------------------------------------- PropertyChangeListener Methods


    /**
     * Handle a <code>PropertyChangeEvent</code> from one of the Containers
     * we are interested in.
     *
     * @param event The event that has occurred
     */
    public void propertyChange(PropertyChangeEvent event) {

        if (event.getSource() instanceof Container) {
            try {
                processContainerPropertyChange((Container) event.getSource(),
                                               event.getPropertyName(),
                                               event.getOldValue(),
                                               event.getNewValue());
            } catch (Exception e) {
                log.error("Exception handling Container property change", e);
            }
        } else if (event.getSource() instanceof NamingResources) {
            try {
                processNamingResourcesPropertyChange
                    ((NamingResources) event.getSource(),
                     event.getPropertyName(),
                     event.getOldValue(),
                     event.getNewValue());
            } catch (Exception e) {
                log.error("Exception handling NamingResources property change", e);
            }
        } else if (event.getSource() instanceof Server) {
            try {
                processServerPropertyChange((Server) event.getSource(),
                                            event.getPropertyName(),
                                            event.getOldValue(),
                                            event.getNewValue());
            } catch (Exception e) {
                log.error("Exception handing Server property change", e);
            }
        } else if (event.getSource() instanceof Service) {
            try {
                processServicePropertyChange((Service) event.getSource(),
                                             event.getPropertyName(),
                                             event.getOldValue(),
                                             event.getNewValue());
            } catch (Exception e) {
                log.error("Exception handing Service property change", e);
            }
        }

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Create the MBeans for the specified Connector and its nested components.
     *
     * @param connector Connector for which to create MBeans
     *
     * @exception Exception if an exception is thrown during MBean creation
     */
    protected void createMBeans(Connector connector) throws Exception {

        // Create the MBean for the Connector itself
//        if (log.isDebugEnabled())
//            log.debug("Creating MBean for Connector " + connector);
//        MBeanUtils.createMBean(connector);

    }


    /**
     * Create the MBeans for the specified Context and its nested components.
     *
     * @param context Context for which to create MBeans
     *
     * @exception Exception if an exception is thrown during MBean creation
     */
    protected void createMBeans(Context context) throws Exception {

        // Create the MBean for the Context itself
//        if (log.isDebugEnabled())
//            log.debug("Creating MBean for Context " + context);
//        MBeanUtils.createMBean(context);
        context.addContainerListener(this);
        if (context instanceof StandardContext) {
            ((StandardContext) context).addPropertyChangeListener(this);
            context.addLifecycleListener(this);
        }

        // If the context is privileged, give a reference to it
        // in a servlet context attribute
        if (context.getPrivileged()) {
            context.getServletContext().setAttribute
                (Globals.MBEAN_REGISTRY_ATTR,
                 MBeanUtils.createRegistry());
            context.getServletContext().setAttribute
                (Globals.MBEAN_SERVER_ATTR,
                 MBeanUtils.createServer());
        }

        // Create the MBeans for the associated nested components
        Loader cLoader = context.getLoader();
        if (cLoader != null) {
            if (log.isDebugEnabled())
                log.debug("Creating MBean for Loader " + cLoader);
            //MBeanUtils.createMBean(cLoader);
        }
        Manager cManager = context.getManager();
        if (cManager != null) {
            if (log.isDebugEnabled())
                log.debug("Creating MBean for Manager " + cManager);
            //MBeanUtils.createMBean(cManager);
        }
        Realm hRealm = context.getParent().getRealm();
        Realm cRealm = context.getRealm();
        if ((cRealm != null) && (cRealm != hRealm)) {
            if (log.isDebugEnabled())
                log.debug("Creating MBean for Realm " + cRealm);
            //MBeanUtils.createMBean(cRealm);
        }

        // Create the MBeans for the NamingResources (if any)
        NamingResources resources = context.getNamingResources();
        createMBeans(resources);

    }


    /**
     * Create the MBeans for the specified ContextEnvironment entry.
     *
     * @param environment ContextEnvironment for which to create MBeans
     *
     * @exception Exception if an exception is thrown during MBean creation
     */
    protected void createMBeans(ContextEnvironment environment)
        throws Exception {

        // Create the MBean for the ContextEnvironment itself
        if (log.isDebugEnabled()) {
            log.debug("Creating MBean for ContextEnvironment " + environment);
        }
        MBeanUtils.createMBean(environment);

    }


    /**
     * Create the MBeans for the specified ContextResource entry.
     *
     * @param resource ContextResource for which to create MBeans
     *
     * @exception Exception if an exception is thrown during MBean creation
     */
    protected void createMBeans(ContextResource resource)
        throws Exception {

        // Create the MBean for the ContextResource itself
        if (log.isDebugEnabled()) {
            log.debug("Creating MBean for ContextResource " + resource);
        }
        MBeanUtils.createMBean(resource);

    }


    /**
     * Create the MBeans for the specified ContextResourceLink entry.
     *
     * @param resourceLink ContextResourceLink for which to create MBeans
     *
     * @exception Exception if an exception is thrown during MBean creation
     */
    protected void createMBeans(ContextResourceLink resourceLink)
        throws Exception {

        // Create the MBean for the ContextResourceLink itself
        if (log.isDebugEnabled()) {
            log.debug("Creating MBean for ContextResourceLink " + resourceLink);
        }
        MBeanUtils.createMBean(resourceLink);

    }


    /**
     * Create the MBeans for the specified Engine and its nested components.
     *
     * @param engine Engine for which to create MBeans
     *
     * @exception Exception if an exception is thrown during MBean creation
     */
    protected void createMBeans(Engine engine) throws Exception {

        // Create the MBean for the Engine itself
        if (log.isDebugEnabled()) {
            log.debug("Creating MBean for Engine " + engine);
        }
        //MBeanUtils.createMBean(engine);
        engine.addContainerListener(this);
        if (engine instanceof StandardEngine) {
            ((StandardEngine) engine).addPropertyChangeListener(this);
        }

        // Create the MBeans for the associated nested components
        Realm eRealm = engine.getRealm();
        if (eRealm != null) {
            if (log.isDebugEnabled())
                log.debug("Creating MBean for Realm " + eRealm);
            //MBeanUtils.createMBean(eRealm);
        }

        // Create the MBeans for each child Host
        Container hosts[] = engine.findChildren();
        for (int j = 0; j < hosts.length; j++) {
            createMBeans((Host) hosts[j]);
        }

    }


    /**
     * Create the MBeans for the specified Host and its nested components.
     *
     * @param host Host for which to create MBeans
     *
     * @exception Exception if an exception is thrown during MBean creation
     */
    protected void createMBeans(Host host) throws Exception {

        // Create the MBean for the Host itself
        if (log.isDebugEnabled()) {
            log.debug("Creating MBean for Host " + host);
        }
        //MBeanUtils.createMBean(host);
        host.addContainerListener(this);
        if (host instanceof StandardHost) {
            ((StandardHost) host).addPropertyChangeListener(this);
        }

        // Create the MBeans for the associated nested components
        Realm eRealm = host.getParent().getRealm();
        Realm hRealm = host.getRealm();
        if ((hRealm != null) && (hRealm != eRealm)) {
            if (log.isDebugEnabled())
                log.debug("Creating MBean for Realm " + hRealm);
            //MBeanUtils.createMBean(hRealm);
        }

        // Create the MBeans for each child Context
        Container contexts[] = host.findChildren();
        for (int k = 0; k < contexts.length; k++) {
            createMBeans((Context) contexts[k]);
        }

    }


    /**
     * Create the MBeans for MBeanFactory.
     *
     * @param factory MBeanFactory for which to create MBean
     *
     * @exception Exception if an exception is thrown during MBean creation
     */
    protected void createMBeans(MBeanFactory factory) throws Exception {

        // Create the MBean for the MBeanFactory
        if (log.isDebugEnabled())
            log.debug("Creating MBean for MBeanFactory " + factory);
        MBeanUtils.createMBean(factory);

    }


    /**
     * Create the MBeans for the specified NamingResources and its
     * nested components.
     *
     * @param resources NamingResources for which to create MBeans
     */
    protected void createMBeans(NamingResources resources) throws Exception {

        // Create the MBean for the NamingResources itself
        if (log.isDebugEnabled()) {
            log.debug("Creating MBean for NamingResources " + resources);
        }
        MBeanUtils.createMBean(resources);
        resources.addPropertyChangeListener(this);

        // Create the MBeans for each child environment entry
        ContextEnvironment environments[] = resources.findEnvironments();
        for (int i = 0; i < environments.length; i++) {
            createMBeans(environments[i]);
        }

        // Create the MBeans for each child resource entry
        ContextResource cresources[] = resources.findResources();
        for (int i = 0; i < cresources.length; i++) {
            createMBeans(cresources[i]);
        }

        // Create the MBeans for each child resource link entry
        ContextResourceLink cresourcelinks[] = resources.findResourceLinks();
        for (int i = 0; i < cresourcelinks.length; i++) {
            createMBeans(cresourcelinks[i]);
        }

    }


    /**
     * Create the MBeans for the specified Server and its nested components.
     *
     * @param server Server for which to create MBeans
     *
     * @exception Exception if an exception is thrown during MBean creation
     */
    protected void createMBeans(Server server) throws Exception {

        // Create the MBean for the Server itself
        if (log.isDebugEnabled())
            log.debug("Creating MBean for Server " + server);
        //MBeanUtils.createMBean(server);
        if (server instanceof StandardServer) {
            ((StandardServer) server).addPropertyChangeListener(this);
        }

        // Create the MBeans for the global NamingResources (if any)
        NamingResources resources = server.getGlobalNamingResources();
        if (resources != null) {
            createMBeans(resources);
        }

        // Create the MBeans for each child Service
        Service services[] = server.findServices();
        for (int i = 0; i < services.length; i++) {
            // FIXME - Warp object hierarchy not currently supported
            if (services[i].getContainer().getClass().getName().equals
                ("org.apache.catalina.connector.warp.WarpEngine")) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping MBean for Service " + services[i]);
                }
                continue;
            }
            createMBeans(services[i]);
        }

    }


    /**
     * Create the MBeans for the specified Service and its nested components.
     *
     * @param service Service for which to create MBeans
     *
     * @exception Exception if an exception is thrown during MBean creation
     */
    protected void createMBeans(Service service) throws Exception {

        if (service instanceof StandardService) {
            ((StandardService) service).addPropertyChangeListener(this);
        }

        // Create the MBeans for the corresponding Connectors
        Connector connectors[] = service.findConnectors();
        for (int j = 0; j < connectors.length; j++) {
            createMBeans(connectors[j]);
        }

        // Create the MBean for the associated Engine and friends
        Engine engine = (Engine) service.getContainer();
        if (engine != null) {
            createMBeans(engine);
        }

    }


    /**
     * Deregister the MBeans for the specified Connector and its nested
     * components.
     *
     * @param connector Connector for which to deregister MBeans
     *
     * @exception Exception if an exception is thrown during MBean destruction
     */
    protected void destroyMBeans(Connector connector, Service service)
        throws Exception {

        // deregister the MBean for the Connector itself
        if (log.isDebugEnabled())
            log.debug("Destroying MBean for Connector " + connector);
        MBeanUtils.destroyMBean(connector, service);

    }


    /**
     * Deregister the MBeans for the specified Context and its nested
     * components.
     *
     * @param context Context for which to deregister MBeans
     *
     * @exception Exception if an exception is thrown during MBean destruction
     */
    protected void destroyMBeans(Context context) throws Exception {

        // Deregister ourselves as a ContainerListener
        context.removeContainerListener(this);

        // Destroy the MBeans for the associated nested components
        Realm hRealm = context.getParent().getRealm();
        Realm cRealm = context.getRealm();
        if ((cRealm != null) && (cRealm != hRealm)) {
            if (log.isDebugEnabled())
                log.debug("Destroying MBean for Realm " + cRealm);
            //MBeanUtils.destroyMBean(cRealm);
        }
        Manager cManager = context.getManager();
        if (cManager != null) {
            if (log.isDebugEnabled())
                log.debug("Destroying MBean for Manager " + cManager);
            //MBeanUtils.destroyMBean(cManager);
        }
        Loader cLoader = context.getLoader();
        if (cLoader != null) {
            if (log.isDebugEnabled())
                log.debug("Destroying MBean for Loader " + cLoader);
            //MBeanUtils.destroyMBean(cLoader);
        }

        // Destroy the MBeans for the NamingResources (if any)
        NamingResources resources = context.getNamingResources();
        if (resources != null) {
            destroyMBeans(resources);
        }

        // deregister the MBean for the Context itself
        if (log.isDebugEnabled())
            log.debug("Destroying MBean for Context " + context);
        MBeanUtils.destroyMBean(context);
        if (context instanceof StandardContext) {
            ((StandardContext) context).
                removePropertyChangeListener(this);
        }

    }


    /**
     * Deregister the MBeans for the specified ContextEnvironment entry.
     *
     * @param environment ContextEnvironment for which to destroy MBeans
     *
     * @exception Exception if an exception is thrown during MBean destruction
     */
    protected void destroyMBeans(ContextEnvironment environment)
        throws Exception {

        // Destroy the MBean for the ContextEnvironment itself
        if (log.isDebugEnabled()) {
            log.debug("Destroying MBean for ContextEnvironment " + environment);
        }
        MBeanUtils.destroyMBean(environment);

    }


    /**
     * Deregister the MBeans for the specified ContextResource entry.
     *
     * @param resource ContextResource for which to destroy MBeans
     *
     * @exception Exception if an exception is thrown during MBean destruction
     */
    protected void destroyMBeans(ContextResource resource)
        throws Exception {

        // Destroy the MBean for the ContextResource itself
        if (log.isDebugEnabled()) {
            log.debug("Destroying MBean for ContextResource " + resource);
        }
        MBeanUtils.destroyMBean(resource);

    }


    /**
     * Deregister the MBeans for the specified ContextResourceLink entry.
     *
     * @param resourceLink ContextResourceLink for which to destroy MBeans
     *
     * @exception Exception if an exception is thrown during MBean destruction
     */
    protected void destroyMBeans(ContextResourceLink resourceLink)
        throws Exception {

        // Destroy the MBean for the ContextResourceLink itself
        if (log.isDebugEnabled()) {
            log.debug("Destroying MBean for ContextResourceLink " + resourceLink);
        }
        MBeanUtils.destroyMBean(resourceLink);

    }


    /**
     * Deregister the MBeans for the specified Engine and its nested
     * components.
     *
     * @param engine Engine for which to destroy MBeans
     *
     * @exception Exception if an exception is thrown during MBean destruction
     */
    protected void destroyMBeans(Engine engine) throws Exception {

        // Deregister ourselves as a ContainerListener
        engine.removeContainerListener(this);

        // Deregister the MBeans for each child Host
        Container hosts[] = engine.findChildren();
        for (int k = 0; k < hosts.length; k++) {
            destroyMBeans((Host) hosts[k]);
        }

        // Deregister the MBeans for the associated nested components
        Realm eRealm = engine.getRealm();
        if (eRealm != null) {
            if (log.isDebugEnabled())
                log.debug("Destroying MBean for Realm " + eRealm);
            //MBeanUtils.destroyMBean(eRealm);
        }

        // Deregister the MBean for the Engine itself
        if (log.isDebugEnabled()) {
            log.debug("Destroying MBean for Engine " + engine);
        }
        MBeanUtils.destroyMBean(engine);

    }


    /**
     * Deregister the MBeans for the specified Host and its nested components.
     *
     * @param host Host for which to destroy MBeans
     *
     * @exception Exception if an exception is thrown during MBean destruction
     */
    protected void destroyMBeans(Host host) throws Exception {

        // Deregister ourselves as a ContainerListener
        host.removeContainerListener(this);

        // Deregister the MBeans for each child Context
        Container contexts[] = host.findChildren();
        for (int k = 0; k < contexts.length; k++) {
            destroyMBeans((Context) contexts[k]);
        }


        // Deregister the MBeans for the associated nested components
        Realm eRealm = host.getParent().getRealm();
        Realm hRealm = host.getRealm();
        if ((hRealm != null) && (hRealm != eRealm)) {
            if (log.isDebugEnabled())
                log.debug("Destroying MBean for Realm " + hRealm);
            //MBeanUtils.destroyMBean(hRealm);
        }

        // Deregister the MBean for the Host itself
        if (log.isDebugEnabled()) {
            log.debug("Destroying MBean for Host " + host);
        }
        MBeanUtils.destroyMBean(host);

    }


    /**
     * Deregister the MBeans for the specified NamingResources and its
     * nested components.
     *
     * @param resources NamingResources for which to destroy MBeans
     *
     * @exception Exception if an exception is thrown during MBean destruction
     */
    protected void destroyMBeans(NamingResources resources) throws Exception {

        // Destroy the MBeans for each child resource entry
        ContextResource cresources[] = resources.findResources();
        for (int i = 0; i < cresources.length; i++) {
            destroyMBeans(cresources[i]);
        }

        // Destroy the MBeans for each child resource link entry
        ContextResourceLink cresourcelinks[] = resources.findResourceLinks();
        for (int i = 0; i < cresourcelinks.length; i++) {
            destroyMBeans(cresourcelinks[i]);
        }

        // Destroy the MBeans for each child environment entry
        ContextEnvironment environments[] = resources.findEnvironments();
        for (int i = 0; i < environments.length; i++) {
            destroyMBeans(environments[i]);
        }

        // Destroy the MBean for the NamingResources itself
        if (log.isDebugEnabled()) {
            log.debug("Destroying MBean for NamingResources " + resources);
        }
        MBeanUtils.destroyMBean(resources);
        resources.removePropertyChangeListener(this);

    }


    /**
     * Deregister the MBeans for the specified Server and its related
     * components.
     *
     * @param server Server for which to destroy MBeans
     *
     * @exception Exception if an exception is thrown during MBean destruction
     */
    protected void destroyMBeans(Server server) throws Exception {

        // Destroy the MBeans for the global NamingResources (if any)
        NamingResources resources = server.getGlobalNamingResources();
        if (resources != null) {
            destroyMBeans(resources);
        }

        // Destroy the MBeans for each child Service
        Service services[] = server.findServices();
        for (int i = 0; i < services.length; i++) {
            destroyMBeans(services[i]);
        }

        // Destroy the MBean for the Server itself
        if (log.isDebugEnabled()) {
            log.debug("Destroying MBean for Server " + server);
        }
        MBeanUtils.destroyMBean(server);
        if (server instanceof StandardServer) {
            ((StandardServer) server).removePropertyChangeListener(this);
        }

    }


    /**
     * Deregister the MBeans for the specified Service and its nested
     * components.
     *
     * @param service Service for which to destroy MBeans
     *
     * @exception Exception if an exception is thrown during MBean destruction
     */
    protected void destroyMBeans(Service service) throws Exception {

        // Deregister the MBeans for the associated Engine
        Engine engine = (Engine) service.getContainer();
        if (engine != null) {
            destroyMBeans(engine);
        }

        // Deregister the MBeans for the corresponding Connectors
        Connector connectors[] = service.findConnectors();
        for (int j = 0; j < connectors.length; j++) {
            destroyMBeans(connectors[j], service);
        }

        if (service instanceof StandardService) {
            ((StandardService) service).removePropertyChangeListener(this);
        }

    }


    /**
     * Process the addition of a new child Container to a parent Container.
     *
     * @param parent Parent container
     * @param child Child container
     */
    protected void processContainerAddChild(Container parent,
                                            Container child) {

        if (log.isDebugEnabled())
            log.debug("Process addChild[parent=" + parent + ",child=" + child + "]");

        try {
            if (child instanceof Context) {
                createMBeans((Context) child);
            } else if (child instanceof Engine) {
                createMBeans((Engine) child);
            } else if (child instanceof Host) {
                createMBeans((Host) child);
            }
        } catch (MBeanException t) {
            Exception e = t.getTargetException();
            if (e == null)
                e = t;
            log.error("processContainerAddChild: MBeanException", e);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error("processContainerAddChild: Throwable", t);
        }

    }


    /**
     * Process a property change event on a Container.
     *
     * @param container The container on which this event occurred
     * @param propertyName The name of the property that changed
     * @param oldValue The previous value (may be <code>null</code>)
     * @param newValue The new value (may be <code>null</code>)
     *
     * @exception Exception if an exception is thrown
     */
    protected void processContainerPropertyChange(Container container,
                                                  String propertyName,
                                                  Object oldValue,
                                                  Object newValue)
        throws Exception {

        if (log.isTraceEnabled()) {
            log.trace("propertyChange[container=" + container +
                ",propertyName=" + propertyName +
                ",oldValue=" + oldValue +
                ",newValue=" + newValue + "]");
        }
        if ("loader".equals(propertyName)) {
            if (oldValue != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Removing MBean for Loader " + oldValue);
                }
                MBeanUtils.destroyMBean((Loader) oldValue);
            }
            if (newValue != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Creating MBean for Loader " + newValue);
                }
                MBeanUtils.createMBean((Loader) newValue);
            }
        } else if ("logger".equals(propertyName)) {
            if (oldValue != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Removing MBean for Logger " + oldValue);
                }
               // MBeanUtils.destroyMBean((Logger) oldValue);
            }
            if (newValue != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Creating MBean for Logger " + newValue);
                }
                //MBeanUtils.createMBean((Logger) newValue);
            }
        } else if ("manager".equals(propertyName)) {
            if (oldValue != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Removing MBean for Manager " + oldValue);
                }
                //MBeanUtils.destroyMBean((Manager) oldValue);
            }
            if (newValue != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Creating MBean for Manager " + newValue);
                }
                //MBeanUtils.createMBean((Manager) newValue);
            }
        } else if ("realm".equals(propertyName)) {
            if (oldValue != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Removing MBean for Realm " + oldValue);
                }
                MBeanUtils.destroyMBean((Realm) oldValue);
            }
            if (newValue != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Creating MBean for Realm " + newValue);
                }
                //MBeanUtils.createMBean((Realm) newValue);
            }
        } else if ("service".equals(propertyName)) {
            if (oldValue != null) {
                destroyMBeans((Service) oldValue);
            }
            if (newValue != null) {
                createMBeans((Service) newValue);
            }
        }

    }


    /**
     * Process the removal of a child Container from a parent Container.
     *
     * @param parent Parent container
     * @param child Child container
     */
    protected void processContainerRemoveChild(Container parent,
                                               Container child) {

        if (log.isDebugEnabled())
            log.debug("Process removeChild[parent=" + parent + ",child=" +
                child + "]");

        try {
            if (child instanceof Context) {
                Context context = (Context) child;
                if (context.getPrivileged()) {
                    context.getServletContext().removeAttribute
                        (Globals.MBEAN_REGISTRY_ATTR);
                    context.getServletContext().removeAttribute
                        (Globals.MBEAN_SERVER_ATTR);
                }
                if (log.isDebugEnabled())
                    log.debug("  Removing MBean for Context " + context);
                destroyMBeans(context);
                if (context instanceof StandardContext) {
                    ((StandardContext) context).
                        removePropertyChangeListener(this);
                }
            } else if (child instanceof Host) {
                Host host = (Host) child;
                destroyMBeans(host);
                if (host instanceof StandardHost) {
                    ((StandardHost) host).
                        removePropertyChangeListener(this);
                }
            }
        } catch (MBeanException t) {
            Exception e = t.getTargetException();
            if (e == null)
                e = t;
            log.error("processContainerRemoveChild: MBeanException", e);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error("processContainerRemoveChild: Throwable", t);
        }

    }


    /**
     * Process a property change event on a NamingResources.
     *
     * @param resources The global naming resources on which this
     *  event occurred
     * @param propertyName The name of the property that changed
     * @param oldValue The previous value (may be <code>null</code>)
     * @param newValue The new value (may be <code>null</code>)
     *
     * @exception Exception if an exception is thrown
     */
    protected void processNamingResourcesPropertyChange
        (NamingResources resources, String propertyName,
         Object oldValue, Object newValue)
        throws Exception {

        if (log.isTraceEnabled()) {
            log.trace("propertyChange[namingResources=" + resources +
                ",propertyName=" + propertyName +
                ",oldValue=" + oldValue +
                ",newValue=" + newValue + "]");
        }

        // FIXME - Add other resource types when supported by admin tool
        if ("environment".equals(propertyName)) {
            if (oldValue != null) {
                destroyMBeans((ContextEnvironment) oldValue);
            }
            if (newValue != null) {
                createMBeans((ContextEnvironment) newValue);
            }
        } else if ("resource".equals(propertyName)) {
            if (oldValue != null) {
                destroyMBeans((ContextResource) oldValue);
            }
            if (newValue != null) {
                createMBeans((ContextResource) newValue);
            }
        } else if ("resourceLink".equals(propertyName)) {
            if (oldValue != null) {
                destroyMBeans((ContextResourceLink) oldValue);
            }
            if (newValue != null) {
                createMBeans((ContextResourceLink) newValue);
            }
        }

    }


    /**
     * Process a property change event on a Server.
     *
     * @param server The server on which this event occurred
     * @param propertyName The name of the property that changed
     * @param oldValue The previous value (may be <code>null</code>)
     * @param newValue The new value (may be <code>null</code>)
     *
     * @exception Exception if an exception is thrown
     */
    protected void processServerPropertyChange(Server server,
                                               String propertyName,
                                               Object oldValue,
                                               Object newValue)
        throws Exception {

        if (log.isTraceEnabled()) {
            log.trace("propertyChange[server=" + server +
                ",propertyName=" + propertyName +
                ",oldValue=" + oldValue +
                ",newValue=" + newValue + "]");
        }
        if ("globalNamingResources".equals(propertyName)) {
            if (oldValue != null) {
                destroyMBeans((NamingResources) oldValue);
            }
            if (newValue != null) {
                createMBeans((NamingResources) newValue);
            }
        } else if ("service".equals(propertyName)) {
            if (oldValue != null) {
                destroyMBeans((Service) oldValue);
            }
            if (newValue != null) {
                createMBeans((Service) newValue);
            }
        }

    }


    /**
     * Process a property change event on a Service.
     *
     * @param service The service on which this event occurred
     * @param propertyName The name of the property that changed
     * @param oldValue The previous value (may be <code>null</code>)
     * @param newValue The new value (may be <code>null</code>)
     *
     * @exception Exception if an exception is thrown
     */
    protected void processServicePropertyChange(Service service,
                                                String propertyName,
                                                Object oldValue,
                                                Object newValue)
        throws Exception {

        if (log.isTraceEnabled()) {
            log.trace("propertyChange[service=" + service +
                ",propertyName=" + propertyName +
                ",oldValue=" + oldValue +
                ",newValue=" + newValue + "]");
        }
        if ("connector".equals(propertyName)) {
            if (oldValue != null) {
                destroyMBeans((Connector) oldValue, service);
            }
            if (newValue != null) {
                createMBeans((Connector) newValue);
            }
        } else if ("container".equals(propertyName)) {
            if (oldValue != null) {
                destroyMBeans((Engine) oldValue);
            }
            if (newValue != null) {
                createMBeans((Engine) newValue);
            }
        }

    }


}
