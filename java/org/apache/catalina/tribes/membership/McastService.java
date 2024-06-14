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
package org.apache.catalina.tribes.membership;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Properties;

import javax.management.ObjectName;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipProvider;
import org.apache.catalina.tribes.MessageListener;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.jmx.JmxRegistry;
import org.apache.catalina.tribes.util.Arrays;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.catalina.tribes.util.UUIDGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * A <b>membership</b> implementation using simple multicast. This is the representation of a multicast membership
 * service. This class is responsible for maintaining a list of active cluster nodes in the cluster. If a node fails to
 * send out a heartbeat, the node will be dismissed.
 */
public class McastService extends MembershipServiceBase implements MessageListener, McastServiceMBean {

    private static final Log log = LogFactory.getLog(McastService.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * A handle to the actual low level implementation
     */
    protected McastServiceImpl impl;

    /**
     * A message listener delegate for broadcasts
     */
    protected MessageListener msglistener;
    /**
     * The local member
     */
    protected MemberImpl localMember;
    private int mcastSoTimeout;
    private int mcastTTL;

    protected byte[] payload;

    protected byte[] domain;

    /**
     * the ObjectName of this McastService.
     */
    private ObjectName oname = null;

    /**
     * Create a membership service.
     */
    public McastService() {
        // default values
        setDefaults(this.properties);
    }

    /**
     * Sets the properties for the membership service.
     *
     * @param properties <br>
     *                       All are required<br>
     *                       1. mcastPort - the port to listen to<BR>
     *                       2. mcastAddress - the mcast group address<BR>
     *                       4. bindAddress - the bind address if any - only one that can be null<BR>
     *                       5. memberDropTime - the time a member is gone before it is considered gone.<BR>
     *                       6. mcastFrequency - the frequency of sending messages<BR>
     *                       7. tcpListenPort - the port this member listens to<BR>
     *                       8. tcpListenHost - the bind address of this member<BR>
     *
     * @exception java.lang.IllegalArgumentException if a property is missing.
     */
    @Override
    public void setProperties(Properties properties) {
        hasProperty(properties, "mcastPort");
        hasProperty(properties, "mcastAddress");
        hasProperty(properties, "memberDropTime");
        hasProperty(properties, "mcastFrequency");
        hasProperty(properties, "tcpListenPort");
        hasProperty(properties, "tcpListenHost");
        setDefaults(properties);
        this.properties = properties;
    }

    /**
     * @return the local member name
     */
    @Override
    public String getLocalMemberName() {
        return localMember.toString();
    }

    @Override
    public Member getLocalMember(boolean alive) {
        if (alive && localMember != null && impl != null) {
            localMember.setMemberAliveTime(System.currentTimeMillis() - impl.getServiceStartTime());
        }
        return localMember;
    }

    @Override
    public void setLocalMemberProperties(String listenHost, int listenPort, int securePort, int udpPort) {
        properties.setProperty("tcpListenHost", listenHost);
        properties.setProperty("tcpListenPort", String.valueOf(listenPort));
        properties.setProperty("udpListenPort", String.valueOf(udpPort));
        properties.setProperty("tcpSecurePort", String.valueOf(securePort));
        try {
            if (localMember != null) {
                localMember.setHostname(listenHost);
                localMember.setPort(listenPort);
            } else {
                localMember = new MemberImpl(listenHost, listenPort, 0);
                localMember.setUniqueId(UUIDGenerator.randomUUID(true));
                localMember.setPayload(getPayload());
                localMember.setDomain(getDomain());
                localMember.setLocal(true);
            }
            localMember.setSecurePort(securePort);
            localMember.setUdpPort(udpPort);
            localMember.getData(true, true);
        } catch (IOException x) {
            throw new IllegalArgumentException(x);
        }
    }

    public void setAddress(String addr) {
        properties.setProperty("mcastAddress", addr);
    }

    @Override
    public String getAddress() {
        return properties.getProperty("mcastAddress");
    }

    public void setMcastBindAddress(String bindaddr) {
        setBind(bindaddr);
    }

    public void setBind(String bindaddr) {
        properties.setProperty("mcastBindAddress", bindaddr);
    }

    @Override
    public String getBind() {
        return properties.getProperty("mcastBindAddress");
    }

    public void setPort(int port) {
        properties.setProperty("mcastPort", String.valueOf(port));
    }

    public void setRecoveryCounter(int recoveryCounter) {
        properties.setProperty("recoveryCounter", String.valueOf(recoveryCounter));
    }

    @Override
    public int getRecoveryCounter() {
        String p = properties.getProperty("recoveryCounter");
        if (p != null) {
            return Integer.parseInt(p);
        }
        return -1;
    }

    public void setRecoveryEnabled(boolean recoveryEnabled) {
        properties.setProperty("recoveryEnabled", String.valueOf(recoveryEnabled));
    }

    @Override
    public boolean getRecoveryEnabled() {
        String p = properties.getProperty("recoveryEnabled");
        if (p != null) {
            return Boolean.parseBoolean(p);
        }
        return false;
    }

    public void setRecoverySleepTime(long recoverySleepTime) {
        properties.setProperty("recoverySleepTime", String.valueOf(recoverySleepTime));
    }

    @Override
    public long getRecoverySleepTime() {
        String p = properties.getProperty("recoverySleepTime");
        if (p != null) {
            return Long.parseLong(p);
        }
        return -1;
    }

    public void setLocalLoopbackDisabled(boolean localLoopbackDisabled) {
        properties.setProperty("localLoopbackDisabled", String.valueOf(localLoopbackDisabled));
    }

    @Override
    public boolean getLocalLoopbackDisabled() {
        String p = properties.getProperty("localLoopbackDisabled");
        if (p != null) {
            return Boolean.parseBoolean(p);
        }
        return false;
    }

    @Override
    public int getPort() {
        String p = properties.getProperty("mcastPort");
        return Integer.parseInt(p);
    }

    public void setFrequency(long time) {
        properties.setProperty("mcastFrequency", String.valueOf(time));
    }

    @Override
    public long getFrequency() {
        String p = properties.getProperty("mcastFrequency");
        return Long.parseLong(p);
    }

    public void setMcastDropTime(long time) {
        setDropTime(time);
    }

    public void setDropTime(long time) {
        properties.setProperty("memberDropTime", String.valueOf(time));
    }

    @Override
    public long getDropTime() {
        String p = properties.getProperty("memberDropTime");
        return Long.parseLong(p);
    }

    /**
     * Check if a required property is available.
     *
     * @param properties The set of properties
     * @param name       The property to check for
     */
    protected void hasProperty(Properties properties, String name) {
        if (properties.getProperty(name) == null) {
            throw new IllegalArgumentException(sm.getString("mcastService.missing.property", name));
        }
    }

    @Override
    public void start(int level) throws Exception {
        hasProperty(properties, "mcastPort");
        hasProperty(properties, "mcastAddress");
        hasProperty(properties, "memberDropTime");
        hasProperty(properties, "mcastFrequency");
        hasProperty(properties, "tcpListenPort");
        hasProperty(properties, "tcpListenHost");
        hasProperty(properties, "tcpSecurePort");
        hasProperty(properties, "udpListenPort");


        if (impl != null) {
            impl.start(level);
            return;
        }
        String host = getProperties().getProperty("tcpListenHost");
        int port = Integer.parseInt(getProperties().getProperty("tcpListenPort"));
        int securePort = Integer.parseInt(getProperties().getProperty("tcpSecurePort"));
        int udpPort = Integer.parseInt(getProperties().getProperty("udpListenPort"));

        if (localMember == null) {
            localMember = new MemberImpl(host, port, 100);
            localMember.setUniqueId(UUIDGenerator.randomUUID(true));
            localMember.setLocal(true);
        } else {
            localMember.setHostname(host);
            localMember.setPort(port);
            localMember.setMemberAliveTime(100);
        }
        localMember.setSecurePort(securePort);
        localMember.setUdpPort(udpPort);
        if (this.payload != null) {
            localMember.setPayload(payload);
        }
        if (this.domain != null) {
            localMember.setDomain(domain);
        }
        localMember.setServiceStartTime(System.currentTimeMillis());
        java.net.InetAddress bind = null;
        if (properties.getProperty("mcastBindAddress") != null) {
            bind = java.net.InetAddress.getByName(properties.getProperty("mcastBindAddress"));
        }
        int ttl = -1;
        int soTimeout = -1;
        if (properties.getProperty("mcastTTL") != null) {
            try {
                ttl = Integer.parseInt(properties.getProperty("mcastTTL"));
            } catch (Exception x) {
                log.error(sm.getString("McastService.parseTTL", properties.getProperty("mcastTTL")), x);
            }
        }
        if (properties.getProperty("mcastSoTimeout") != null) {
            try {
                soTimeout = Integer.parseInt(properties.getProperty("mcastSoTimeout"));
            } catch (Exception x) {
                log.error(sm.getString("McastService.parseSoTimeout", properties.getProperty("mcastSoTimeout")), x);
            }
        }

        impl = new McastServiceImpl(localMember, Long.parseLong(properties.getProperty("mcastFrequency")),
                Long.parseLong(properties.getProperty("memberDropTime")),
                Integer.parseInt(properties.getProperty("mcastPort")), bind,
                java.net.InetAddress.getByName(properties.getProperty("mcastAddress")), ttl, soTimeout, this, this,
                Boolean.parseBoolean(properties.getProperty("localLoopbackDisabled")));
        impl.setMembershipService(this);
        String value = properties.getProperty("recoveryEnabled");
        boolean recEnabled = Boolean.parseBoolean(value);
        impl.setRecoveryEnabled(recEnabled);
        int recCnt = Integer.parseInt(properties.getProperty("recoveryCounter"));
        impl.setRecoveryCounter(recCnt);
        long recSlpTime = Long.parseLong(properties.getProperty("recoverySleepTime"));
        impl.setRecoverySleepTime(recSlpTime);
        impl.setChannel(channel);

        impl.start(level);
        // register jmx
        JmxRegistry jmxRegistry = JmxRegistry.getRegistry(channel);
        if (jmxRegistry != null) {
            this.oname = jmxRegistry.registerJmx(",component=Membership", this);
        }

    }


    /**
     * Stop broadcasting and listening to membership pings
     */
    @Override
    public void stop(int svc) {
        try {
            if (impl != null && impl.stop(svc)) {
                if (oname != null) {
                    JmxRegistry.getRegistry(channel).unregisterJmx(oname);
                    oname = null;
                }
                impl.setChannel(null);
                impl = null;
                channel = null;
            }
        } catch (Exception x) {
            log.error(sm.getString("McastService.stopFail", Integer.valueOf(svc)), x);
        }
    }

    public void setMessageListener(MessageListener listener) {
        this.msglistener = listener;
    }

    public void removeMessageListener() {
        this.msglistener = null;
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        if (msglistener != null && msglistener.accept(msg)) {
            msglistener.messageReceived(msg);
        }
    }

    @Override
    public boolean accept(ChannelMessage msg) {
        return true;
    }

    @Override
    public void broadcast(ChannelMessage message) throws ChannelException {
        if (impl == null || (impl.startLevel & Channel.MBR_TX_SEQ) != Channel.MBR_TX_SEQ) {
            throw new ChannelException(sm.getString("mcastService.noStart"));
        }

        byte[] data = XByteBuffer.createDataPackage((ChannelData) message);
        if (data.length > McastServiceImpl.MAX_PACKET_SIZE) {
            throw new ChannelException(sm.getString("mcastService.exceed.maxPacketSize", Integer.toString(data.length),
                    Integer.toString(McastServiceImpl.MAX_PACKET_SIZE)));
        }
        DatagramPacket packet = new DatagramPacket(data, 0, data.length);
        try {
            impl.send(false, packet);
        } catch (Exception x) {
            throw new ChannelException(x);
        }
    }

    @Override
    public int getSoTimeout() {
        return mcastSoTimeout;
    }

    public void setSoTimeout(int mcastSoTimeout) {
        this.mcastSoTimeout = mcastSoTimeout;
        properties.setProperty("mcastSoTimeout", String.valueOf(mcastSoTimeout));
    }

    @Override
    public int getTtl() {
        return mcastTTL;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public byte[] getDomain() {
        return domain;
    }

    public void setTtl(int mcastTTL) {
        this.mcastTTL = mcastTTL;
        properties.setProperty("mcastTTL", String.valueOf(mcastTTL));
    }

    @Override
    public void setPayload(byte[] payload) {
        this.payload = payload;
        if (localMember != null) {
            localMember.setPayload(payload);
            try {
                if (impl != null) {
                    impl.send(false);
                }
            } catch (Exception x) {
                log.error(sm.getString("McastService.payload"), x);
            }
        }
    }

    @Override
    public void setDomain(byte[] domain) {
        this.domain = domain;
        if (localMember != null) {
            localMember.setDomain(domain);
            try {
                if (impl != null) {
                    impl.send(false);
                }
            } catch (Exception x) {
                log.error(sm.getString("McastService.domain"), x);
            }
        }
    }

    public void setDomain(String domain) {
        if (domain == null) {
            return;
        }
        if (domain.startsWith("{")) {
            setDomain(Arrays.fromString(domain));
        } else {
            setDomain(Arrays.convert(domain));
        }
    }

    @Override
    public MembershipProvider getMembershipProvider() {
        return impl;
    }

    protected void setDefaults(Properties properties) {
        // default values
        if (properties.getProperty("mcastPort") == null) {
            properties.setProperty("mcastPort", "45564");
        }
        if (properties.getProperty("mcastAddress") == null) {
            properties.setProperty("mcastAddress", "228.0.0.4");
        }
        if (properties.getProperty("memberDropTime") == null) {
            properties.setProperty("memberDropTime", "3000");
        }
        if (properties.getProperty("mcastFrequency") == null) {
            properties.setProperty("mcastFrequency", "500");
        }
        if (properties.getProperty("recoveryCounter") == null) {
            properties.setProperty("recoveryCounter", "10");
        }
        if (properties.getProperty("recoveryEnabled") == null) {
            properties.setProperty("recoveryEnabled", "true");
        }
        if (properties.getProperty("recoverySleepTime") == null) {
            properties.setProperty("recoverySleepTime", "5000");
        }
        if (properties.getProperty("localLoopbackDisabled") == null) {
            properties.setProperty("localLoopbackDisabled", "false");
        }
    }

    /**
     * Simple test program
     *
     * @param args Command-line arguments
     *
     * @throws Exception If an error occurs
     */
    public static void main(String args[]) throws Exception {
        McastService service = new McastService();
        Properties p = new Properties();
        p.setProperty("mcastPort", "5555");
        p.setProperty("mcastAddress", "224.10.10.10");
        p.setProperty("mcastClusterDomain", "catalina");
        p.setProperty("bindAddress", "localhost");
        p.setProperty("memberDropTime", "3000");
        p.setProperty("mcastFrequency", "500");
        p.setProperty("tcpListenPort", "4000");
        p.setProperty("tcpListenHost", "127.0.0.1");
        p.setProperty("tcpSecurePort", "4100");
        p.setProperty("udpListenPort", "4200");
        service.setProperties(p);
        service.start();
        Thread.sleep(60 * 1000 * 60);
    }
}
