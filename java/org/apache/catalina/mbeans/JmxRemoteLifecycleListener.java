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
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
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
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * This listener fixes the port used by JMX/RMI Server making things much
 * simpler if you need to connect jconsole or similar to a remote Tomcat
 * instance that is running behind a firewall. Only the ports are configured via
 * the listener. The remainder of the configuration is via the standard system
 * properties for configuring JMX.
 */
public class JmxRemoteLifecycleListener implements LifecycleListener {

    private static final Log log =
        LogFactory.getLog(JmxRemoteLifecycleListener.class);

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    protected String rmiBindAddress = null;
    protected int rmiRegistryPortPlatform = -1;
    protected int rmiServerPortPlatform = -1;
    protected boolean rmiSSL = true;
    protected String ciphers[] = null;
    protected String protocols[] = null;
    protected boolean clientAuth = true;
    protected boolean authenticate = true;
    protected String passwordFile = null;
    protected String loginModuleName = null;
    protected String accessFile = null;
    protected boolean useLocalPorts = false;

    protected JMXConnectorServer csPlatform = null;

    /**
     * Get the inet address on which the Platform RMI server is exported.
     * @return The textual representation of inet address
     */
    public String getRmiBindAddress() {
        return rmiBindAddress;
    }

    /**
     * Set the inet address on which the Platform RMI server is exported.
     * @param theRmiBindAddress The textual representation of inet address
     */
    public void setRmiBindAddress(String theRmiBindAddress) {
        rmiBindAddress = theRmiBindAddress;
    }

    /**
     * Get the port on which the Platform RMI server is exported. This is the
     * port that is normally chosen by the RMI stack.
     * @return The port number
     */
    public int getRmiServerPortPlatform() {
        return rmiServerPortPlatform;
    }

    /**
     * Set the port on which the Platform RMI server is exported. This is the
     * port that is normally chosen by the RMI stack.
     * @param theRmiServerPortPlatform The port number
     */
    public void setRmiServerPortPlatform(int theRmiServerPortPlatform) {
        rmiServerPortPlatform = theRmiServerPortPlatform;
    }

    /**
     * Get the port on which the Platform RMI registry is exported.
     * @return The port number
     */
    public int getRmiRegistryPortPlatform() {
        return rmiRegistryPortPlatform;
    }

    /**
     * Set the port on which the Platform RMI registry is exported.
     * @param theRmiRegistryPortPlatform The port number
     */
    public void setRmiRegistryPortPlatform(int theRmiRegistryPortPlatform) {
        rmiRegistryPortPlatform = theRmiRegistryPortPlatform;
    }

    /**
     * Get the flag that indicates that local ports should be used for all
     * connections. If using SSH tunnels, or similar, this should be set to
     * true to ensure the RMI client uses the tunnel.
     * @return <code>true</code> if local ports should be used
     */
    public boolean getUseLocalPorts() {
        return useLocalPorts;
    }

    /**
     * Set the flag that indicates that local ports should be used for all
     * connections. If using SSH tunnels, or similar, this should be set to
     * true to ensure the RMI client uses the tunnel.
     * @param useLocalPorts Set to <code>true</code> if local ports should be
     *                      used
     */
    public void setUseLocalPorts(boolean useLocalPorts) {
        this.useLocalPorts = useLocalPorts;
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

        loginModuleName = System.getProperty(
                "com.sun.management.jmxremote.login.config");
    }


    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        // When the server starts, configure JMX/RMI
        if (Lifecycle.START_EVENT == event.getType()) {
            // Configure using standard jmx system properties
            init();

            // Prevent an attacker guessing the RMI object ID
            System.setProperty("java.rmi.server.randomIDs", "true");

            // Create the environment
            HashMap<String,Object> env = new HashMap<>();

            RMIClientSocketFactory csf = null;
            RMIServerSocketFactory ssf = null;

            // Configure SSL for RMI connection if required
            if (rmiSSL) {
                if (rmiBindAddress != null) {
                    throw new IllegalStateException(sm.getString(
                            "jmxRemoteLifecycleListener.sslRmiBindAddress"));
                }

                csf = new SslRMIClientSocketFactory();
                ssf = new SslRMIServerSocketFactory(ciphers, protocols,
                            clientAuth);
            }

            // Force server bind address if required
            if (rmiBindAddress != null) {
                try {
                    ssf = new RmiServerBindSocketFactory(
                            InetAddress.getByName(rmiBindAddress));
                } catch (UnknownHostException e) {
                    log.error(sm.getString(
                            "jmxRemoteLifecycleListener.invalidRmiBindAddress",
                            rmiBindAddress), e);
                }
            }

            // Force the use of local ports if required
            if (useLocalPorts) {
                csf = new RmiClientLocalhostSocketFactory(csf);
            }

            // Populate the env properties used to create the server
            if (csf != null) {
                env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE,
                        csf);
                env.put("com.sun.jndi.rmi.factory.socket", csf);
            }
            if (ssf != null) {
                env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE,
                        ssf);
            }

            // Configure authentication
            if (authenticate) {
                env.put("jmx.remote.x.password.file", passwordFile);
                env.put("jmx.remote.x.access.file", accessFile);
                env.put("jmx.remote.x.login.config", loginModuleName);
            }


            // Create the Platform server
            csPlatform = createServer("Platform", rmiBindAddress, rmiRegistryPortPlatform,
                    rmiServerPortPlatform, env, csf, ssf,
                    ManagementFactory.getPlatformMBeanServer());

        } else if (Lifecycle.STOP_EVENT == event.getType()) {
            destroyServer("Platform", csPlatform);
        }
    }

    private JMXConnectorServer createServer(String serverName,
            String bindAddress, int theRmiRegistryPort, int theRmiServerPort,
            HashMap<String,Object> theEnv, RMIClientSocketFactory csf,
            RMIServerSocketFactory ssf, MBeanServer theMBeanServer) {

        // Create the RMI registry
        try {
            LocateRegistry.createRegistry(theRmiRegistryPort, csf, ssf);
        } catch (RemoteException e) {
            log.error(sm.getString(
                    "jmxRemoteLifecycleListener.createRegistryFailed",
                    serverName, Integer.toString(theRmiRegistryPort)), e);
            return null;
        }

        if (bindAddress == null) {
            bindAddress = "localhost";
        }

        // Build the connection string with fixed ports
        StringBuilder url = new StringBuilder();
        url.append("service:jmx:rmi://");
        url.append(bindAddress);
        url.append(":");
        url.append(theRmiServerPort);
        url.append("/jndi/rmi://");
        url.append(bindAddress);
        url.append(":");
        url.append(theRmiRegistryPort);
        url.append("/jmxrmi");
        JMXServiceURL serviceUrl;
        try {
            serviceUrl = new JMXServiceURL(url.toString());
        } catch (MalformedURLException e) {
            log.error(sm.getString(
                    "jmxRemoteLifecycleListener.invalidURL",
                    serverName, url.toString()), e);
            return null;
        }

        // Start the JMX server with the connection string
        JMXConnectorServer cs = null;
        try {
            cs = JMXConnectorServerFactory.newJMXConnectorServer(
                    serviceUrl, theEnv, theMBeanServer);
            cs.start();
            log.info(sm.getString("jmxRemoteLifecycleListener.start",
                    Integer.toString(theRmiRegistryPort),
                    Integer.toString(theRmiServerPort), serverName));
        } catch (IOException e) {
            log.error(sm.getString(
                    "jmxRemoteLifecycleListener.createServerFailed",
                    serverName), e);
        }
        return cs;
    }

    private void destroyServer(String serverName,
            JMXConnectorServer theConnectorServer) {
        if (theConnectorServer != null) {
            try {
                theConnectorServer.stop();
            } catch (IOException e) {
                log.error(sm.getString(
                        "jmxRemoteLifecycleListener.destroyServerFailed",
                        serverName),e);
            }
        }
    }

    public static class RmiClientLocalhostSocketFactory
            implements RMIClientSocketFactory, Serializable {

        private static final long serialVersionUID = 1L;

        private static final String FORCED_HOST = "localhost";

        private final RMIClientSocketFactory factory;

        public RmiClientLocalhostSocketFactory(RMIClientSocketFactory theFactory) {
            factory = theFactory;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            if (factory == null) {
                return new Socket(FORCED_HOST, port);
            } else {
                return factory.createSocket(FORCED_HOST, port);
            }
        }
    }

    public static class RmiServerBindSocketFactory
            implements RMIServerSocketFactory {

        private final InetAddress bindAddress;

        public RmiServerBindSocketFactory(InetAddress address) {
            bindAddress = address;
        }

        @Override
        public ServerSocket createServerSocket(int port) throws IOException  {
            return new ServerSocket(port, 0, bindAddress);
        }
    }
}
