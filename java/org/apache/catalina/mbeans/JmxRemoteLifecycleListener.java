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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.HashMap;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * This listener fixes the port used by JMX/RMI Server making things much
 * simpler if you need to connect jconsole or similar to a remote Tomcat
 * instance that is running behind a firewall. Only this port is configured via
 * the listener. The remainder of the configuration is via the standard system
 * properties for configuring JMX.
 */
public class JmxRemoteLifecycleListener implements LifecycleListener {
    
    private static Log log =
        LogFactory.getLog(JmxRemoteLifecycleListener.class);
    
    /**
     * The string resources for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    protected int rmiRegistryPort = -1;
    protected int rmiServerPort = -1;
    protected boolean rmiSSL = true;
    protected String ciphers[] = null;
    protected String protocols[] = null;
    protected boolean clientAuth = true;
    protected boolean authenticate = true;
    protected String passwordFile = null;
    protected String accessFile = null;

    /**
     * Get the port on which the RMI server is exported. This is the port that
     * is normally chosen by the RMI stack.
     * @returns The port number
     */
    public int getRmiServerPort() {
        return rmiServerPort;
    }
    
    /**
     * Set the port on which the RMI server is exported. This is the port that
     * is normally chosen by the RMI stack.
     * @param theRmiServerPort The port number
     */
    public void setRmiServerPort(int theRmiServerPort) {
        rmiServerPort = theRmiServerPort;
    }
    
    /**
     * Get the port on which the RMI registry is exported.
     * @returns The port number
     */
    public int getRmiRegistryPort() {
        return rmiRegistryPort;
    }
    
    /**
     * Set the port on which the RMI registryis exported.
     * @param theRmiServerPort The port number
     */
    public void setRmiRegistryPort(int theRmiRegistryPort) {
        rmiRegistryPort = theRmiRegistryPort;
    }
    
    private void init() {
        // Get all the other parameters required from the standard system
        // properties. Only need to get the parameters that affect the creation
        // of the server port.
        String rmiSSLValue = System.getProperty(
                "com.sun.management.jmxremote.ssl", "true");
        rmiSSL = Boolean.parseBoolean(rmiSSLValue);

        String protocolsValue = System.getProperty(
                "com.sun.management.jmxremote.ssl.enabled.protocols");
        if (protocolsValue != null) {
            protocols = protocolsValue.split(",");
        }

        String ciphersValue = System.getProperty(
                "com.sun.management.jmxremote.ssl.enabled.cipher.suites");
        if (ciphersValue != null) {
            ciphers = ciphersValue.split(",");
        }

        String clientAuthValue = System.getProperty(
            "com.sun.management.jmxremote.ssl.need.client.auth", "true");
        clientAuth = Boolean.parseBoolean(clientAuthValue);

        String authenticateValue = System.getProperty(
                "com.sun.management.jmxremote.authenticate", "true");
        authenticate = Boolean.parseBoolean(authenticateValue);

        passwordFile = System.getProperty(
                "com.sun.management.jmxremote.password.file",
                "jmxremote.password");

        accessFile = System.getProperty(
                "com.sun.management.jmxremote.access.file",
                "jmxremote.access");
    }
    

    public void lifecycleEvent(LifecycleEvent event) {
        // When the server starts, configure JMX/RMI
        if (Lifecycle.START_EVENT == event.getType()) {
            // Configure using standard jmx system properties 
            init();

            // Prevent an attacker guessing the RMI object ID
            System.setProperty("java.rmi.server.randomIDs", "true");

            try {
                LocateRegistry.createRegistry(rmiRegistryPort);
            } catch (RemoteException e) {
                log.error(sm.getString(
                        "jmxRemoteLifecycleListener.createRegistryFailed",
                        Integer.toString(rmiRegistryPort)), e);
                return;
            }
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            HashMap<String,Object> env = new HashMap<String,Object>();
            
            if (rmiSSL) {
                // Use SSL for RMI connection
                SslRMIClientSocketFactory csf =
                    new SslRMIClientSocketFactory();
                SslRMIServerSocketFactory ssf =
                    new SslRMIServerSocketFactory(ciphers, protocols,
                            clientAuth);
                env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE,
                        csf);
                env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE,
                        ssf);
            }
            if (authenticate) {
                env.put("jmx.remote.x.password.file", passwordFile);
                env.put("jmx.remote.x.access.file", accessFile);
            }
            StringBuffer url = new StringBuffer();
            url.append("service:jmx:rmi://localhost:");
            url.append(rmiServerPort);
            url.append("/jndi/rmi://localhost:");
            url.append(rmiRegistryPort);
            url.append("/jmxrmi");
            JMXServiceURL serviceUrl;
            try {
                serviceUrl = new JMXServiceURL(url.toString());
            } catch (MalformedURLException e) {
                log.error(sm.getString(
                        "jmxRemoteLifecycleListener.invalidURL",
                        url.toString()), e);
                return;
            }
            JMXConnectorServer cs;
            try {
                cs = JMXConnectorServerFactory.newJMXConnectorServer(
                        serviceUrl, env, mbs);
                cs.start();
                log.info(sm.getString("jmxRemoteLifecycleListener.start",
                        new Integer(rmiRegistryPort),
                        new Integer(rmiServerPort)));
            } catch (IOException e) {
                log.error(sm.getString(""), e);
            }
        }
    }

}
