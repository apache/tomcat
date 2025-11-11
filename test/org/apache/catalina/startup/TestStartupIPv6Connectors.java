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
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface interf = interfaces.nextElement();
            Enumeration<InetAddress> addresses = interf.getInetAddresses();
            if (interf.isPointToPoint()) {
                continue;
            }
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet6Address inet6Address) {
                    if (inet6Address.isLinkLocalAddress()) {
                        linklocalAddress = inet6Address.getHostAddress();
                    }
                    if (!inet6Address.isAnyLocalAddress() && !inet6Address.isLoopbackAddress() && !inet6Address.isLinkLocalAddress() && !inet6Address.isMulticastAddress()) {
                        globalAddress = inet6Address.getHostAddress();
                    }
                    if (linklocalAddress != null && globalAddress != null) {
                        return;
                    }
                }
            }
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