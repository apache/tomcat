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
package org.apache.catalina.tribes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import org.apache.catalina.tribes.group.interceptors.DomainFilterInterceptor;
import org.apache.catalina.tribes.util.UUIDGenerator;

/**
 * Utility methods for use by multiple tests.
 */
public class TesterUtil {

    private TesterUtil() {
        // Hide default constructor
    }


    /*
     * Determines whether IP multicast is actually usable in the current
     * environment. The tribes membership tests rely on the default
     * {@link org.apache.catalina.tribes.membership.McastService} which
     * discovers members via IP multicast. Some environments (containers,
     * minimal CI images, hosts with multicast blocked by a firewall or with no
     * multicast-capable network interface) cannot deliver multicast traffic. In
     * those environments the affected tests would fail on a membership timeout
     * through no fault of the code under test, so they should be skipped instead.
     *
     * This performs a functional check - it sends a multicast datagram to the
     * default group over loopback and confirms it is received - rather than
     * simply inspecting interface flags, so that it also detects environments
     * where multicast sockets can be created but traffic is silently dropped.
     * Any failure is treated as "multicast not available" so a hostile
     * environment results in a skipped test rather than a spurious failure.
     */
    public static boolean isMulticastAvailable() {
        InetAddress group;
        try {
            // Same default group as McastService
            group = InetAddress.getByName("228.0.0.4");
        } catch (UnknownHostException e) {
            return false;
        }

        // Bind to an ephemeral port (not the McastService default) so this probe
        // never clashes with a running membership service on the same host.
        try (MulticastSocket socket = new MulticastSocket(0)) {
            // Loop-back must be enabled for the probe to receive its own packet (the parameter is "disable").
            socket.setLoopbackMode(false);
            socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, Boolean.TRUE);
            socket.setSoTimeout(1000);

            int port = socket.getLocalPort();
            InetSocketAddress groupAddress = new InetSocketAddress(group, port);
            // null network interface -> let the OS pick the default, mirroring
            // McastServiceImpl when no bind address is configured.
            socket.joinGroup(groupAddress, null);
            try {
                byte[] probe = "tomcat-tribes-multicast-probe".getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(probe, probe.length, group, port));

                DatagramPacket received = new DatagramPacket(new byte[probe.length], probe.length);
                // Throws SocketTimeoutException if the packet is not delivered.
                socket.receive(received);
                return received.getLength() == probe.length;
            } finally {
                socket.leaveGroup(groupAddress, null);
            }
        } catch (IOException e) {
            return false;
        }
    }


    /*
     * Configures a set of channels to use a random domain. Use to ensure that
     * multiple instance of the test suite do not interfere when running on the
     * same machine. This may happen in a CI system or when a developer is
     * running tests for multiple branches in parallel.
     */
    public static void addRandomDomain(ManagedChannel[] channels) {
        if (channels == null) {
            return;
        }

        byte[] domain = UUIDGenerator.randomUUID(false);

        for (ManagedChannel channel : channels) {
            channel.getMembershipService().setDomain(domain);
            DomainFilterInterceptor filter = new DomainFilterInterceptor();
            filter.setDomain(domain);
            channel.addInterceptor(filter);
        }
    }
}
