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

import java.io.PrintWriter;
import java.net.URL;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Store Server/Service/Host/Context at file or PrintWriter. Default server.xml
 * is at $catalina.base/conf/server.xml
 */
public class StoreConfig implements IStoreConfig {
    private static Log log = LogFactory.getLog(StoreConfig.class);
    protected static final StringManager sm = StringManager
            .getManager(Constants.Package);

    private String serverFilename = "conf/server.xml";

    private StoreRegistry registry;

    private Server server;

    /**
     * Get server.xml location
     *
     * @return The server file name
     */
    public String getServerFilename() {
        return serverFilename;
    }

    /**
     * Set new server.xml location.
     *
     * @param string The server.xml location
     */
    public void setServerFilename(String string) {
        serverFilename = string;
    }

    /**
     * Get the StoreRegistry with all factory to generate the
     * server.xml/context.xml files.
     *
     * @see org.apache.catalina.storeconfig.IStoreConfig#getRegistry()
     */
    @Override
    public StoreRegistry getRegistry() {
        return registry;
    }

    @Override
    public void setServer(Server aServer) {
        server = aServer;
    }

    @Override
    public Server getServer() {
        return server;
    }

    /**
     * set StoreRegistry
     *
     * @see org.apache.catalina.storeconfig.IStoreConfig#setRegistry(org.apache.catalina.storeconfig.StoreRegistry)
     */
    @Override
    public void setRegistry(StoreRegistry aRegistry) {
        registry = aRegistry;
    }

    /**
     * Store current Server.
     */
    @Override
    public void storeConfig() {
        store(server);
    }

    /**
     * Store Server from Object Name (Catalina:type=Server).
     *
     * @param aServerName Server ObjectName
     * @param backup <code>true</code> to backup existing configuration files
     *  before rewriting them
     * @param externalAllowed <code>true</code> to allow saving webapp
     *  configuration for webapps that are not inside the host's app
     *  directory
     * @throws MalformedObjectNameException Bad MBean name
     */
    public synchronized void storeServer(String aServerName, boolean backup,
            boolean externalAllowed) throws MalformedObjectNameException {
        if (aServerName == null || aServerName.length() == 0) {
            if (log.isErrorEnabled())
                log.error("Please, call with a correct server ObjectName!");
            return;
        }
        MBeanServer mserver = MBeanUtils.createServer();
        ObjectName objectName = new ObjectName(aServerName);
        if (mserver.isRegistered(objectName)) {
            try {
                Server aServer = (Server) mserver.getAttribute(objectName,
                        "managedResource");
                StoreDescription desc = null;
                desc = getRegistry().findDescription(StandardContext.class);
                if (desc != null) {
                    boolean oldSeparate = desc.isStoreSeparate();
                    boolean oldBackup = desc.isBackup();
                    boolean oldExternalAllowed = desc.isExternalAllowed();
                    try {
                        desc.setStoreSeparate(true);
                        desc.setBackup(backup);
                        desc.setExternalAllowed(externalAllowed);
                        store(aServer);
                    } finally {
                        desc.setStoreSeparate(oldSeparate);
                        desc.setBackup(oldBackup);
                        desc.setExternalAllowed(oldExternalAllowed);
                    }
                } else {
                    store(aServer);
                }
            } catch (Exception e) {
                if (log.isInfoEnabled())
                    log.info("Object " + aServerName
                            + " is no a Server instance or store exception", e);
            }
        } else if (log.isInfoEnabled())
            log.info("Server " + aServerName + " not found!");
    }

    /**
     * Store a Context from ObjectName.
     *
     * @param aContextName MBean ObjectName
     * @param backup <code>true</code> to backup existing configuration files
     *  before rewriting them
     * @param externalAllowed <code>true</code> to allow saving webapp
     *  configuration for webapps that are not inside the host's app
     *  directory
     * @throws MalformedObjectNameException Bad MBean name
     */
    public synchronized void storeContext(String aContextName, boolean backup,
            boolean externalAllowed) throws MalformedObjectNameException {
        if (aContextName == null || aContextName.length() == 0) {
            if (log.isErrorEnabled())
                log.error("Please, call with a correct context ObjectName!");
            return;
        }
        MBeanServer mserver = MBeanUtils.createServer();
        ObjectName objectName = new ObjectName(aContextName);
        if (mserver.isRegistered(objectName)) {
            try {
                Context aContext = (Context) mserver.getAttribute(objectName,
                        "managedResource");
                URL configFile = aContext.getConfigFile();
                if (configFile != null) {
                    try {
                        StoreDescription desc = null;
                        desc = getRegistry().findDescription(
                                aContext.getClass());
                        if (desc != null) {
                            boolean oldSeparate = desc.isStoreSeparate();
                            boolean oldBackup = desc.isBackup();
                            boolean oldExternalAllowed = desc
                                    .isExternalAllowed();
                            try {
                                desc.setStoreSeparate(true);
                                desc.setBackup(backup);
                                desc.setExternalAllowed(externalAllowed);
                                desc.getStoreFactory()
                                        .store(null, -2, aContext);
                            } finally {
                                desc.setStoreSeparate(oldSeparate);
                                desc.setBackup(oldBackup);
                                desc.setBackup(oldExternalAllowed);
                            }
                        }
                    } catch (Exception e) {
                        log.error(e);
                    }
                } else
                    log.error("Missing configFile at Context "
                            + aContext.getPath() + " to store!");
            } catch (Exception e) {
                if (log.isInfoEnabled())
                    log
                            .info(
                                    "Object "
                                            + aContextName
                                            + " is no a context instance or store exception",
                                    e);
            }
        } else if (log.isInfoEnabled())
            log.info("Context " + aContextName + " not found!");
    }

    /**
     * Write the configuration information for this entire <code>Server</code>
     * out to the server.xml configuration file.
     *
     * @param aServer Server instance
     * @return <code>true</code> if the store operation was successful
     */
    @Override
    public synchronized boolean store(Server aServer) {
        StoreFileMover mover = new StoreFileMover(System
                .getProperty("catalina.base"), getServerFilename(),
                getRegistry().getEncoding());
        // Open an output writer for the new configuration file
        try {
            try (PrintWriter writer = mover.getWriter()) {
                store(writer, -2, aServer);
            }
            mover.move();
            return true;
        } catch (Exception e) {
            log.error(sm.getString("config.storeServerError"), e);
        }
        return false;
    }

    /**
     * @see org.apache.catalina.storeconfig.IStoreConfig#store(org.apache.catalina.Context)
     */
    @Override
    public synchronized boolean store(Context aContext) {
        URL configFile = aContext.getConfigFile();
        if (configFile != null) {
            try {
                StoreDescription desc = null;
                desc = getRegistry().findDescription(aContext.getClass());
                if (desc != null) {
                    boolean old = desc.isStoreSeparate();
                    try {
                        desc.setStoreSeparate(true);
                        desc.getStoreFactory().store(null, -2, aContext);
                    } finally {
                        desc.setStoreSeparate(old);
                    }
                }
                return true;
            } catch (Exception e) {
                log.error(sm.getString("config.storeContextError", aContext.getName()), e);
            }
        } else {
            log.error("Missing configFile at Context " + aContext.getPath());
        }
        return false;
    }

    /**
     * @see org.apache.catalina.storeconfig.IStoreConfig#store(java.io.PrintWriter,
     *      int, org.apache.catalina.Context)
     */
    @Override
    public void store(PrintWriter aWriter, int indent,
            Context aContext) throws Exception {
        boolean oldSeparate = true;
        StoreDescription desc = null;
        try {
            desc = getRegistry().findDescription(aContext.getClass());
            oldSeparate = desc.isStoreSeparate();
            desc.setStoreSeparate(false);
            desc.getStoreFactory().store(aWriter, indent, aContext);
        } finally {
            if (desc != null)
                desc.setStoreSeparate(oldSeparate);
        }
    }

    /**
     * @see org.apache.catalina.storeconfig.IStoreConfig#store(java.io.PrintWriter,
     *      int, org.apache.catalina.Host)
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Host aHost)
            throws Exception {
        StoreDescription desc = getRegistry().findDescription(
                aHost.getClass());
        desc.getStoreFactory().store(aWriter, indent, aHost);
    }

    /**
     * @see org.apache.catalina.storeconfig.IStoreConfig#store(java.io.PrintWriter,
     *      int, org.apache.catalina.Service)
     */
    @Override
    public void store(PrintWriter aWriter, int indent,
            Service aService) throws Exception {
        StoreDescription desc = getRegistry().findDescription(
                aService.getClass());
        desc.getStoreFactory().store(aWriter, indent, aService);
    }

    /**
     * @see org.apache.catalina.storeconfig.IStoreConfig#store(java.io.PrintWriter,
     *      int, org.apache.catalina.Server)
     */
    @Override
    public void store(PrintWriter writer, int indent,
            Server aServer) throws Exception {
        StoreDescription desc = getRegistry().findDescription(
                aServer.getClass());
        desc.getStoreFactory().store(writer, indent, aServer);
    }

}
