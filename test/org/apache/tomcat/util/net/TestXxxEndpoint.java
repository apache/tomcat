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
package org.apache.tomcat.util.net;

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.jni.Error;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.Pool;

/**
 * Test case for the Endpoint implementations. The testing framework will ensure
 * that each implementation is tested.
 */
public class TestXxxEndpoint extends TomcatBaseTest {

    private long createAprPool() {

        // Create the pool for the server socket
        try {
            return Pool.create(0);
        } catch (UnsatisfiedLinkError e) {
            log.error("Could not create socket pool", e);
            return 0;
        }
    }

    /*
     * @deprecated  The scope of the APR/Native Library will be reduced in Tomcat
     *              10.1.x onwards to only those components required to provide
     *              OpenSSL integration with the NIO and NIO2 connectors.
     */
    @Deprecated
    private long createAprSocket(int port, long pool)
                 throws Exception {
        /**
         * Server socket "pointer".
         */
        long serverSock = 0;

        String address = InetAddress.getByName("localhost").getHostAddress();

        // Create the APR address that will be bound
        int family = org.apache.tomcat.jni.Socket.APR_INET;
        if (Library.APR_HAVE_IPV6) {
            if (!org.apache.tomcat.jni.OS.IS_BSD && !org.apache.tomcat.jni.OS.IS_WIN32 &&
                    !org.apache.tomcat.jni.OS.IS_WIN64) {
                family = org.apache.tomcat.jni.Socket.APR_UNSPEC;
            }
         }

        long inetAddress = 0;
        try {
            inetAddress = org.apache.tomcat.jni.Address.info(address, family,
                                       port, 0, pool);
            // Create the APR server socket
            serverSock = org.apache.tomcat.jni.Socket.create(org.apache.tomcat.jni.Address.getInfo(inetAddress).family,
                    org.apache.tomcat.jni.Socket.SOCK_STREAM,
                    org.apache.tomcat.jni.Socket.APR_PROTO_TCP, pool);
        } catch (Exception ex) {
            log.error("Could not create socket for address '" + address + "'");
            return 0;
        }

        if (org.apache.tomcat.jni.OS.IS_UNIX) {
            org.apache.tomcat.jni.Socket.optSet(serverSock, org.apache.tomcat.jni.Socket.APR_SO_REUSEADDR, 1);
        }
        // Deal with the firewalls that tend to drop the inactive sockets
        org.apache.tomcat.jni.Socket.optSet(serverSock, org.apache.tomcat.jni.Socket.APR_SO_KEEPALIVE, 1);
        // Bind the server socket
        int ret = org.apache.tomcat.jni.Socket.bind(serverSock, inetAddress);
        if (ret != 0) {
            log.error("Could not bind: " + Error.strerror(ret));
            throw (new Exception(Error.strerror(ret)));
        }
        return serverSock;
    }

    /*
     * @deprecated  The scope of the APR/Native Library will be reduced in Tomcat
     *              10.1.x onwards to only those components required to provide
     *              OpenSSL integration with the NIO and NIO2 connectors.
     */
    @Deprecated
    private void destroyAprSocket(long serverSock, long pool) {
        if (serverSock != 0) {
            org.apache.tomcat.jni.Socket.shutdown(serverSock, org.apache.tomcat.jni.Socket.APR_SHUTDOWN_READWRITE);
            org.apache.tomcat.jni.Socket.close(serverSock);
            org.apache.tomcat.jni.Socket.destroy(serverSock);
        }

        if (pool != 0) {
            Pool.destroy(pool);
            pool = 0;
        }
    }

    @Test
    public void testStartStopBindOnInit() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File(getBuildDirectory(), "webapps/examples");
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        tomcat.start();

        int port = getPort();

        tomcat.getConnector().stop();
        Exception e = null;
        ServerSocket s = null;
        long pool = 0;
        long nativeSocket = 0;
        boolean isApr = tomcat.getConnector().getProtocolHandlerClassName().contains("Apr");
        try {
            // This should throw an Exception
            if (isApr) {
                pool = createAprPool();
                Assert.assertTrue(pool != 0);
                nativeSocket = createAprSocket(port, pool);
                Assert.assertTrue(nativeSocket != 0);
            } else {
                s = new ServerSocket(port, 100,
                        InetAddress.getByName("localhost"));
            }
        } catch (Exception e1) {
            e = e1;
        } finally {
            try {
                if (isApr) {
                    destroyAprSocket(nativeSocket, pool);
                } else if (s != null) {
                    s.close();
                }
            } catch (Exception e2) { /* Ignore */ }
        }
        if (e != null) {
            log.info("Exception was", e);
        }
        Assert.assertNotNull(e);
        tomcat.getConnector().start();
    }

    @Test
    public void testStartStopBindOnStart() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Connector c = tomcat.getConnector();
        Assert.assertTrue(c.setProperty("bindOnInit", "false"));

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());


        tomcat.start();
        int port = getPort();

        tomcat.getConnector().stop();
        Exception e = null;
        ServerSocket s = null;
        long pool = 0;
        long nativeSocket = 0;
        boolean isApr = tomcat.getConnector().getProtocolHandlerClassName().contains("Apr");
        try {
            // This should not throw an Exception
            if (isApr) {
                pool = createAprPool();
                Assert.assertTrue(pool != 0);
                nativeSocket = createAprSocket(port, pool);
                Assert.assertTrue(nativeSocket != 0);
            } else {
                s = new ServerSocket(port, 100,
                        InetAddress.getByName("localhost"));
            }
        } catch (Exception e1) {
            e = e1;
        } finally {
            try {
                if (isApr) {
                    destroyAprSocket(nativeSocket, pool);
                } else if (s != null) {
                    s.close();
                }
            } catch (Exception e2) { /* Ignore */ }
        }
        Assert.assertNull(e);
        tomcat.getConnector().start();
    }
}
