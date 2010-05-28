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
package org.apache.catalina.connector;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import org.apache.tomcat.util.http.mapper.Mapper;

import org.apache.tomcat.util.res.StringManager;


/**
 * Mapper listener.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class MapperListener implements ContainerListener, LifecycleListener {


    private static final Log log = LogFactory.getLog(MapperListener.class);


    // ----------------------------------------------------- Instance Variables
    /**
     * Associated mapper.
     */
    private Mapper mapper = null;
    
    /**
     * Associated connector
     */
    private Connector connector = null;


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * The domain (effectively the engine) this mapper is associated with
     */
    private String domain = null;

    // ----------------------------------------------------------- Constructors


    /**
     * Create mapper listener.
     */
    public MapperListener(Mapper mapper, Connector connector) {
        this.mapper = mapper;
        this.connector = connector;
    }


    // --------------------------------------------------------- Public Methods

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    /**
     * Initialize associated mapper.
     */
    public void init() {

        // Find any components that have already been initialized since the
        // MBean listener won't be notified as those components will have
        // already registered their MBeans
        findDefaultHost();
        
        Engine engine = (Engine) connector.getService().getContainer();
        engine.addContainerListener(this);
        
        Container[] conHosts = engine.findChildren();
        for (Container conHost : conHosts) {
            Host host = (Host) conHost;
            if (!LifecycleState.NEW.equals(host.getState())) {
                host.addLifecycleListener(this);
                // Registering the host will register the context and wrappers
                registerHost(host);
            }
        }
    }
        

    /**
     * Clean-up.
     */
    public void destroy() {
        // NO-OP?
    }


    // --------------------------------------------- Container Listener methods

    public void containerEvent(ContainerEvent event) {

        if (event.getType() == Container.ADD_CHILD_EVENT) {
            Container child = (Container) event.getData();
            child.addLifecycleListener(this);
            child.addContainerListener(this);
            if (child instanceof Host) {
                registerHost((Host) child);
            } else if (child instanceof Context) {
                registerContext((Context) child);
            } else if (child instanceof Wrapper) {
                registerWrapper((Wrapper) child);
            }
        } else if (event.getType() == Container.REMOVE_CHILD_EVENT) {
            Container child = (Container) event.getData();
            removeListeners(child);
            if (child instanceof Host) {
                unregisterHost((Host) child);
            } else if (child instanceof Context) {
                unregisterContext((Context) child);
            } else if (child instanceof Wrapper) {
                unregisterWrapper((Wrapper) child);
            }
        } else if (event.getType() == Host.ADD_ALIAS_EVENT) {
            // Handle dynamically adding host aliases
            mapper.addHostAlias(((Host) event.getSource()).getName(),
                    event.getData().toString());
        } else if (event.getType() == Host.REMOVE_ALIAS_EVENT) {
            // Handle dynamically removing host aliases
            mapper.removeHostAlias(event.getData().toString());
        } else if (event.getType() == Wrapper.ADD_MAPPING_EVENT) {
            // Handle dynamically adding wrappers
            Wrapper wrapper = (Wrapper) event.getSource();

            String contextName = wrapper.getParent().getName();
            if ("/".equals(contextName)) {
                contextName = "";
            }
            String hostName = wrapper.getParent().getParent().getName();

            String mapping = (String) event.getData();
            boolean jspWildCard = ("jsp".equals(wrapper.getName())
                    && mapping.endsWith("/*"));
            mapper.addWrapper(hostName, contextName, mapping, wrapper,
                    jspWildCard);
        } else if (event.getType() == Wrapper.REMOVE_MAPPING_EVENT) {
            // Handle dynamically removing wrappers
            Wrapper wrapper = (Wrapper) event.getSource();

            String contextName = wrapper.getParent().getName();
            if ("/".equals(contextName)) {
                contextName = "";
            }
            String hostName = wrapper.getParent().getParent().getName();

            String mapping = (String) event.getData();
            
            mapper.removeWrapper(hostName, contextName, mapping);
        } else if (event.getType() == Context.ADD_WELCOME_FILE_EVENT) {
            // Handle dynamically adding welcome files
            Context context = (Context) event.getSource();
            
            String hostName = context.getParent().getName();

            String contextName = context.getName();
            if ("/".equals(contextName)) {
                contextName = "";
            }
            
            String welcomeFile = (String) event.getData();
            
            mapper.addWelcomeFile(hostName, contextName, welcomeFile);
        } else if (event.getType() == Context.REMOVE_WELCOME_FILE_EVENT) {
            // Handle dynamically removing welcome files
            Context context = (Context) event.getSource();
            
            String hostName = context.getParent().getName();

            String contextName = context.getName();
            if ("/".equals(contextName)) {
                contextName = "";
            }
            
            String welcomeFile = (String) event.getData();
            
            mapper.removeWelcomeFile(hostName, contextName, welcomeFile);
        } else if (event.getType() == Context.CLEAR_WELCOME_FILES_EVENT) {
            // Handle dynamically clearing welcome files
            Context context = (Context) event.getSource();
            
            String hostName = context.getParent().getName();

            String contextName = context.getName();
            if ("/".equals(contextName)) {
                contextName = "";
            }
            
            mapper.clearWelcomeFiles(hostName, contextName);
        }
    }

    
    // ------------------------------------------------------ Protected Methods

    private void findDefaultHost() {

        Engine engine = (Engine) connector.getService().getContainer();
        String defaultHost = engine.getDefaultHost();

        boolean found = false;

        if (defaultHost != null && defaultHost.length() >0) {
            Container[] containers = engine.findChildren();
            
            for (Container container : containers) {
                Host host = (Host) container;
                if (defaultHost.equalsIgnoreCase(host.getName())) {
                    found = true;
                    break;
                }
                
                String[] aliases = host.findAliases();
                for (String alias : aliases) {
                    if (defaultHost.equalsIgnoreCase(alias)) {
                        found = true;
                        break;
                    }
                }
            }
        }

        if(found) {
            mapper.setDefaultHostName(defaultHost);
        } else {
            log.warn(sm.getString("mapperListener.unknownDefaultHost",
                    defaultHost));
        }
    }

    
    /**
     * Register host.
     */
    private void registerHost(Host host) {
        
        String[] aliases = host.findAliases();
        mapper.addHost(host.getName(), aliases, host.getObjectName());
        
        host.addContainerListener(this);
        
        for (Container container : host.findChildren()) {
            registerContext((Context) container);
        }
        if(log.isDebugEnabled()) {
            log.debug(sm.getString
                 ("mapperListener.registerHost", host.getName(), domain));
        }
    }


    /**
     * Unregister host.
     */
    private void unregisterHost(Host host) {

        String hostname = host.getName();
        
        mapper.removeHost(hostname);

        if(log.isDebugEnabled())
            log.debug(sm.getString("mapperListener.unregisterHost", hostname,
                    domain));
    }

    
    /**
     * Unregister wrapper.
     */
    private void unregisterWrapper(Wrapper wrapper) {

        String contextName = wrapper.getParent().getName();
        if ("/".equals(contextName)) {
            contextName = "";
        }
        String hostName = wrapper.getParent().getParent().getName();

        String[] mappings = wrapper.findMappings();
        
        for (String mapping : mappings) {
            mapper.removeWrapper(hostName, contextName, mapping);
        }
    }

    
    /**
     * Register context.
     */
    private void registerContext(Context context) {

        String contextName = context.getName();
        if ("/".equals(contextName)) {
            contextName = "";
        }
        String hostName = context.getParent().getName();
        
        javax.naming.Context resources = context.getResources();
        String[] welcomeFiles = context.findWelcomeFiles();

        mapper.addContext(hostName, contextName, context, welcomeFiles,
                resources);

        context.addContainerListener(this);
       
        for (Container container : context.findChildren()) {
            registerWrapper((Wrapper) container);
        }

        if(log.isDebugEnabled()) {
            log.debug(sm.getString
                 ("mapperListener.registerContext", contextName));
        }
    }


    /**
     * Unregister context.
     */
    private void unregisterContext(Context context) {

        // Don't un-map a context that is paused
        if (context.getPaused()){
            return;
        }

        String contextName = context.getName();
        if ("/".equals(contextName)) {
            contextName = "";
        }
        String hostName = context.getParent().getName();

        if(log.isDebugEnabled())
            log.debug(sm.getString
                  ("mapperListener.unregisterContext", contextName));

        mapper.removeContext(hostName, contextName);
    }


    /**
     * Register wrapper.
     */
    private void registerWrapper(Wrapper wrapper) {

        String wrapperName = wrapper.getName();
        String contextName = wrapper.getParent().getName();
        if ("/".equals(contextName)) {
            contextName = "";
        }
        String hostName = wrapper.getParent().getParent().getName();
        
        String[] mappings = wrapper.findMappings();

        for (String mapping : mappings) {
            boolean jspWildCard = (wrapperName.equals("jsp")
                                   && mapping.endsWith("/*"));
            mapper.addWrapper(hostName, contextName, mapping, wrapper,
                              jspWildCard);
        }

        wrapper.addContainerListener(this);

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("mapperListener.registerWrapper",
                    wrapperName, contextName));
        }
    }

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (event.getType() == Lifecycle.AFTER_START_EVENT) {
            Object obj = event.getSource();
            if (obj instanceof Wrapper) {
                registerWrapper((Wrapper) obj);
            } else if (obj instanceof Context) {
                registerContext((Context) obj);
            } else if (obj instanceof Host) {
                registerHost((Host) obj);
            }
        } else if (event.getType() == Lifecycle.BEFORE_STOP_EVENT) {
            Object obj = event.getSource();
            if (obj instanceof Wrapper) {
                unregisterWrapper((Wrapper) obj);
            } else if (obj instanceof Context) {
                unregisterContext((Context) obj);
            } else if (obj instanceof Host) {
                unregisterHost((Host) obj);
            }
        }
    }

    /**
     * Remove this mapper from the container and all child containers
     * 
     * @param container
     */
    private void removeListeners(Container container) {
        container.removeContainerListener(this);
        container.removeLifecycleListener(this);
        for (Container child : container.findChildren()) {
            removeListeners(child);
        }
    }
}
