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
import java.util.ArrayList;
import java.util.Properties;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipProvider;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class StaticMembershipService extends MembershipServiceBase {
    private static final Log log = LogFactory.getLog(StaticMembershipService.class);
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    protected final ArrayList<StaticMember> staticMembers = new ArrayList<>();
    private StaticMember localMember;
    private StaticMembershipProvider provider;

    public StaticMembershipService() {
        //default values
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
        // TODO JMX register
    }

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
                // TODO JMX unregister
                provider = null;
                channel = null;;
            }
        } catch (Exception e) {
            log.error(sm.getString("staticMembershipService.stopFail", Integer.valueOf(level)), e);
        }
    }

    @Override
    public Member getLocalMember(boolean incAliveTime) {
        if ( incAliveTime && localMember != null) {
            localMember.setMemberAliveTime(System.currentTimeMillis()-localMember.getServiceStartTime());
        }
        return localMember;
    }

    @Override
    public void setLocalMemberProperties(String listenHost, int listenPort, 
            int securePort, int udpPort) {
        try {
            localMember.setHostname(listenHost);
            localMember.setPort(listenPort);
            localMember.setSecurePort(securePort);
            localMember.setUdpPort(udpPort);
            localMember.getData(true, true);
        } catch (IOException x) {
            throw new IllegalArgumentException(x);
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

    public ArrayList<StaticMember> getStaticMembers() {
        return staticMembers;
    }

    public void addStaticMember(StaticMember member) {
        staticMembers.add(member);
    }

    public void removeStaticMember(StaticMember member) {
        staticMembers.remove(member);
    }

    public void setLocalMember(StaticMember member) {
        this.localMember = member;
        localMember.setLocal(true);
    }

     public long getExpirationTime() {
         String expirationTime = properties.getProperty("expirationTime");
         return Long.parseLong(expirationTime);
     }

    public void setExpirationTime(long expirationTime) {
        properties.setProperty("expirationTime", String.valueOf(expirationTime));
    }

     public int getConnectTimeout() {
         String connectTimeout = properties.getProperty("connectTimeout");
         return Integer.parseInt(connectTimeout);
     }

    public void setConnectTimeout(int connectTimeout) {
        properties.setProperty("connectTimeout", String.valueOf(connectTimeout));
    }

    public long getRpcTimeout() {
        String rpcTimeout = properties.getProperty("rpcTimeout");
        return Long.parseLong(rpcTimeout);
    }

    public void setRpcTimeout(long rpcTimeout) {
        properties.setProperty("rpcTimeout", String.valueOf(rpcTimeout));
    }

    @Override
    public void setProperties(Properties properties) {
        setDefaults(properties);
        this.properties = properties;
    }

    protected void setDefaults(Properties properties) {
        // default values
        if (properties.getProperty("expirationTime") == null)
            properties.setProperty("expirationTime","5000");
        if (properties.getProperty("connectTimeout") == null)
            properties.setProperty("connectTimeout","500");
        if (properties.getProperty("rpcTimeout") == null)
            properties.setProperty("rpcTimeout","3000");
    }

    private String getMembershipName() {
        return channel.getName()+"-"+"StaticMembership";
    }
}