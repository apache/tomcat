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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import javax.management.ObjectName;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipProvider;
import org.apache.catalina.tribes.jmx.JmxRegistry;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Service that manages static membership for a cluster channel.
 */
public class StaticMembershipService extends MembershipServiceBase implements StaticMembershipServiceMBean {

    private static final Log log = LogFactory.getLog(StaticMembershipService.class);
    /**
     * String manager for this class.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * List of static members configured for this service.
     */
    protected final ArrayList<StaticMember> staticMembers = new ArrayList<>();
    private StaticMember localMember;
    private StaticMembershipProvider provider;

    /**
     * the ObjectName of this MembershipService.
     */
    private ObjectName oname = null;

    /**
     * Default constructor.
     */
    public StaticMembershipService() {
        // default values
        setDefaults(this.properties);
    }

    @Override
    public void start(int level) throws Exception {
        if (provider != null) {
            provider.start(level);
            return;
        }
        localMember.setServiceStartTime(System.currentTimeMillis());
        localMember.setMemberAliveTime(100);
        // build membership provider
        if (provider == null) {
            provider = buildMembershipProvider();
        }
        provider.start(level);
        JmxRegistry jmxRegistry = JmxRegistry.getRegistry(channel);
        if (jmxRegistry != null) {
            this.oname = jmxRegistry.registerJmx(",component=Membership", this);
        }
    }

    /**
     * Builds and configures the membership provider.
     * @return the configured membership provider
     * @throws Exception if initialization fails
     */
    protected StaticMembershipProvider buildMembershipProvider() throws Exception {
        StaticMembershipProvider provider = new StaticMembershipProvider();
        provider.setChannel(channel);
        provider.setMembershipListener(this);
        provider.setMembershipService(this);
        provider.setStaticMembers(staticMembers);
        properties.setProperty("membershipName", getMembershipName());
        provider.init(properties);
        return provider;
    }

    @Override
    public void stop(int level) {
        try {
            if (provider != null && provider.stop(level)) {
                if (oname != null) {
                    JmxRegistry.getRegistry(channel).unregisterJmx(oname);
                    oname = null;
                }
                provider = null;
                channel = null;
            }
        } catch (Exception e) {
            log.error(sm.getString("staticMembershipService.stopFail", Integer.valueOf(level)), e);
        }
    }

    @Override
    public Member getLocalMember(boolean incAliveTime) {
        if (incAliveTime && localMember != null) {
            localMember.setMemberAliveTime(System.currentTimeMillis() - localMember.getServiceStartTime());
        }
        return localMember;
    }

    @Override
    public void setLocalMemberProperties(String listenHost, int listenPort, int securePort, int udpPort) {
        properties.setProperty("tcpListenHost", listenHost);
        properties.setProperty("tcpListenPort", String.valueOf(listenPort));
        try {
            findLocalMember();
            localMember.setHostname(listenHost);
            localMember.setPort(listenPort);
            localMember.setSecurePort(securePort);
            localMember.setUdpPort(udpPort);
            localMember.getData(true, true);
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
    }

    @Override
    public void setPayload(byte[] payload) {
        // no-op
    }

    @Override
    public void setDomain(byte[] domain) {
        // no-op
    }

    @Override
    public MembershipProvider getMembershipProvider() {
        return provider;
    }

    /**
     * Returns the list of static members.
     * @return the static members list
     */
    public ArrayList<StaticMember> getStaticMembers() {
        return staticMembers;
    }

    /**
     * Adds a static member to the cluster.
     * @param member the member to add
     */
    public void addStaticMember(StaticMember member) {
        staticMembers.add(member);
    }

    /**
     * Removes a static member from the cluster.
     * @param member the member to remove
     */
    public void removeStaticMember(StaticMember member) {
        staticMembers.remove(member);
    }

    /**
     * Sets the local member for this service.
     * @param member the local member
     */
    public void setLocalMember(StaticMember member) {
        this.localMember = member;
        localMember.setLocal(true);
    }

    @Override
    public long getExpirationTime() {
        String expirationTime = properties.getProperty("expirationTime");
        return Long.parseLong(expirationTime);
    }

    /**
     * Sets the member expiration time in milliseconds.
     * @param expirationTime the expiration time
     */
    public void setExpirationTime(long expirationTime) {
        properties.setProperty("expirationTime", String.valueOf(expirationTime));
    }

    @Override
    public int getConnectTimeout() {
        String connectTimeout = properties.getProperty("connectTimeout");
        return Integer.parseInt(connectTimeout);
    }

    /**
     * Sets the connection timeout in milliseconds.
     * @param connectTimeout the connection timeout
     */
    public void setConnectTimeout(int connectTimeout) {
        properties.setProperty("connectTimeout", String.valueOf(connectTimeout));
    }

    @Override
    public long getRpcTimeout() {
        String rpcTimeout = properties.getProperty("rpcTimeout");
        return Long.parseLong(rpcTimeout);
    }

    /**
     * Sets the RPC timeout in milliseconds.
     * @param rpcTimeout the RPC timeout
     */
    public void setRpcTimeout(long rpcTimeout) {
        properties.setProperty("rpcTimeout", String.valueOf(rpcTimeout));
    }

    @Override
    public boolean getUseThread() {
        String useThread = properties.getProperty("useThread");
        return Boolean.parseBoolean(useThread);
    }

    /**
     * Sets whether to use a background thread for pinging.
     * @param useThread true to use a background thread
     */
    public void setUseThread(boolean useThread) {
        properties.setProperty("useThread", String.valueOf(useThread));
    }

    @Override
    public long getPingInterval() {
        String pingInterval = properties.getProperty("pingInterval");
        return Long.parseLong(pingInterval);
    }

    /**
     * Sets the ping interval in milliseconds.
     * @param pingInterval the ping interval
     */
    public void setPingInterval(long pingInterval) {
        properties.setProperty("pingInterval", String.valueOf(pingInterval));
    }

    @Override
    public void setProperties(Properties properties) {
        setDefaults(properties);
        this.properties = properties;
    }

    /**
     * Sets default property values.
     * @param properties the properties to configure
     */
    protected void setDefaults(Properties properties) {
        // default values
        if (properties.getProperty("expirationTime") == null) {
            properties.setProperty("expirationTime", "5000");
        }
        if (properties.getProperty("connectTimeout") == null) {
            properties.setProperty("connectTimeout", "500");
        }
        if (properties.getProperty("rpcTimeout") == null) {
            properties.setProperty("rpcTimeout", "3000");
        }
        if (properties.getProperty("useThread") == null) {
            properties.setProperty("useThread", "false");
        }
        if (properties.getProperty("pingInterval") == null) {
            properties.setProperty("pingInterval", "1000");
        }
    }

    private String getMembershipName() {
        return channel.getName() + "-" + "StaticMembership";
    }

    private void findLocalMember() throws IOException {
        if (this.localMember != null) {
            return;
        }
        String listenHost = properties.getProperty("tcpListenHost");
        String listenPort = properties.getProperty("tcpListenPort");

        // find local member from static members
        for (StaticMember staticMember : this.staticMembers) {
            if (Arrays.equals(InetAddress.getByName(listenHost).getAddress(), staticMember.getHost()) &&
                    Integer.parseInt(listenPort) == staticMember.getPort()) {
                this.localMember = staticMember;
                break;
            }
        }
        if (this.localMember == null) {
            throw new IllegalStateException(sm.getString("staticMembershipService.noLocalMember"));
        }
        staticMembers.remove(this.localMember);
    }
}