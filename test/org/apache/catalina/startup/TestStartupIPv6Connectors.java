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

package org.apache.catalina.startup;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.util.Enumeration;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.catalina.Context;

public class TestStartupIPv6Connectors extends TomcatBaseTest {

    private static String linklocalAddress;
    private static String globalAddress;

    @Test
    public void testIPv6Localhost() throws Exception {
        assertHttpOkOnAddress("::1");
    }

    @Test
    public void testIPv6LinkLocal() throws Exception {
        Assume.assumeTrue("No IPv6 link-local present", linklocalAddress != null);
        assertHttpOkOnAddress(linklocalAddress);
    }

    @Test
    public void testIPv6Global() throws Exception {
        Assume.assumeTrue("No IPv6 global-unicast present", globalAddress != null);
        assertHttpOkOnAddress(globalAddress);
    }

    @Test
    public void testIPv6MappedIPv4() throws Exception {
        assertHttpOkOnAddress("::ffff:127.0.0.1");
    }

    @BeforeClass
    public static void initializeTestIpv6Addresses() throws Exception {
        Inet6Address possibleLinkLocalLoopback = null;
        Inet6Address possibleLinkLocalOnGlobal = null;
        Inet6Address possibleLinkLocalAny = null;

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface interf = interfaces.nextElement();
            if (!interf.isUp() || interf.isVirtual() || interf.isPointToPoint() || !interf.supportsMulticast()) {
                continue;
            }
            Enumeration<InetAddress> addresses = interf.getInetAddresses();
            boolean globalOnInterface = false;
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet6Address) {
                    Inet6Address inet6Address = (Inet6Address) address;
                    if (!inet6Address.isAnyLocalAddress() && !inet6Address.isLoopbackAddress() && !inet6Address.isLinkLocalAddress() && !inet6Address.isMulticastAddress()) {
                        globalOnInterface = true;
                        if (!interf.isLoopback()) {
                            globalAddress = inet6Address.getHostAddress();
                            break;
                        }
                    }
                }
            }

            // Second pass to get link-local results with specific order
            addresses = interf.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet6Address) {
                    Inet6Address inet6Address = (Inet6Address) address;
                    if (inet6Address.isLinkLocalAddress()) {
                        if (interf.isLoopback()) {
                            // Best option for mac
                            possibleLinkLocalLoopback = inet6Address;
                        } else if (globalOnInterface && possibleLinkLocalOnGlobal == null) {
                            // link-local on an interface that also has a global IPv6 (e.g. en0)
                            possibleLinkLocalOnGlobal = inet6Address;
                        } else if (possibleLinkLocalAny == null) {
                            possibleLinkLocalAny = inet6Address;
                        }
                    }
                }
            }
        }

        if (possibleLinkLocalLoopback != null) {
            linklocalAddress = possibleLinkLocalLoopback.getHostAddress();
        } else if (possibleLinkLocalOnGlobal != null) {
            linklocalAddress = possibleLinkLocalOnGlobal.getHostAddress();
        } else if (possibleLinkLocalAny != null) {
            linklocalAddress = possibleLinkLocalAny.getHostAddress();
        }

    }

    private void assertHttpOkOnAddress(String address) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setProperty("address", address);
        File baseDir = new File(getTemporaryDirectory(), "ipv6");
        addDeleteOnTearDown(baseDir);
        Assert.assertTrue(baseDir.mkdirs() && baseDir.isDirectory());
        tomcat.setBaseDir(baseDir.getAbsolutePath());
        Context context = tomcat.addContext("", null);
        Tomcat.addServlet(context, "simple", new HelloWorldServlet());
        context.addServletMappingDecoded("/", "simple");

        try {
            tomcat.start();
        } catch (Exception e) {
            Assume.assumeNoException("Can't bind to " + address, e);
        }
        if (address.contains(":")) {
            address = "[" + address + "]";
        }
        URL url = new URI("http://" + address + ":" + getPort() + "/").toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();
        Assert.assertEquals(HttpServletResponse.SC_OK, connection.getResponseCode());
        tomcat.stop();
    }
}