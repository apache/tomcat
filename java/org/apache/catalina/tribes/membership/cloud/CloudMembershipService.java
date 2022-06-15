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
package org.apache.catalina.tribes.membership.cloud;

import java.io.IOException;

import javax.management.ObjectName;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipProvider;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.jmx.JmxRegistry;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.membership.MembershipServiceBase;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * A {@link org.apache.catalina.tribes.MembershipService} that uses Kubernetes API(default) or DNS to retrieve
 * the members of a cluster.<br>
 * <p>
 * The default implementation of the MembershipProvider component is the {@link KubernetesMembershipProvider}.
 * The MembershipProvider can be configured by the <code>membershipProviderClassName</code> property.
 * Possible shortcuts are {@code kubernetes} and {@code dns}. For dns look at the {@link DNSMembershipProvider}.
 * </p>
 * <p>
 * <strong>Configuration example</strong>
 * </p>
 *
 * {@code server.xml }
 *
 * <pre>
 * {@code
 * <Server ...
 *
 *   <Service ...
 *
 *     <Engine ...
 *
 *       <Host ...
 *
 *         <Cluster className="org.apache.catalina.ha.tcp.SimpleTcpCluster">
 *           <Channel className="org.apache.catalina.tribes.group.GroupChannel">
 *             <Membership className="org.apache.catalina.tribes.membership.cloud.CloudMembershipService"/>
 *           </Channel>
 *         </Cluster>
 *         ...
 *  }
 *  </pre>
 *
 */

public class CloudMembershipService extends MembershipServiceBase
        implements CloudMembershipServiceMBean {

    private static final Log log = LogFactory.getLog(CloudMembershipService.class);
    protected static final StringManager sm = StringManager.getManager(CloudMembershipService.class);

    public static final String MEMBERSHIP_PROVIDER_CLASS_NAME = "membershipProviderClassName";
    private static final String KUBE = "kubernetes";
    private static final String DNS = "dns";
    private static final String KUBE_PROVIDER_CLASS = "org.apache.catalina.tribes.membership.cloud.KubernetesMembershipProvider";
    private static final String DNS_PROVIDER_CLASS = "org.apache.catalina.tribes.membership.cloud.DNSMembershipProvider";
    protected static final byte[] INITIAL_ID = new byte[16];

    private MembershipProvider membershipProvider;
    private MemberImpl localMember;

    private byte[] payload;
    private byte[] domain;

    private ObjectName oname = null;

    /**
     * Return a property.
     * @param name the property name
     * @return the property value
     */
    public Object getProperty(String name) {
        return properties.getProperty(name);
    }

    /**
     * Set a property.
     * @param name the property name
     * @param value the property value
     * @return <code>true</code> if the property was successfully set
     */
    public boolean setProperty(String name, String value) {
        return (properties.setProperty(name, value) == null);
    }

    /**
     * Return the membership provider class.
     * @return the classname
     */
    public String getMembershipProviderClassName() {
        return properties.getProperty(MEMBERSHIP_PROVIDER_CLASS_NAME);
    }

    /**
     * Set the membership provider class.
     * @param membershipProviderClassName the class name
     */
    public void setMembershipProviderClassName(String membershipProviderClassName) {
        properties.setProperty(MEMBERSHIP_PROVIDER_CLASS_NAME, membershipProviderClassName);
    }

    @Override
    public void start(int level) throws Exception {
        if ((level & MembershipService.MBR_RX) == 0) {
            return;
        }

        createOrUpdateLocalMember();
        localMember.setServiceStartTime(System.currentTimeMillis());
        localMember.setMemberAliveTime(100);
        localMember.setPayload(payload);
        localMember.setDomain(domain);

        if (membershipProvider == null) {
            String provider = getMembershipProviderClassName();
            if (provider == null || KUBE.equals(provider)) {
                provider = KUBE_PROVIDER_CLASS;
            } else if (DNS.equals(provider)) {
                provider = DNS_PROVIDER_CLASS;
            }
            if (log.isDebugEnabled()) {
                log.debug("Using membershipProvider: " + provider);
            }
            membershipProvider =
                    (MembershipProvider) Class.forName(provider).getConstructor().newInstance();
            membershipProvider.setMembershipListener(this);
            membershipProvider.setMembershipService(this);
            membershipProvider.init(properties);
        }
        membershipProvider.start(level);

        JmxRegistry jmxRegistry = JmxRegistry.getRegistry(channel);
        if (jmxRegistry != null) {
            oname = jmxRegistry.registerJmx(",component=Membership", this);
        }
    }

    @Override
    public void stop(int level) {
        try {
            if (membershipProvider != null && membershipProvider.stop(level)) {
                if (oname != null) {
                    JmxRegistry.getRegistry(channel).unregisterJmx(oname);
                    oname = null;
                }
                membershipProvider = null;
                channel = null;
            }
        } catch (Exception e) {
            log.error(sm.getString("cloudMembershipService.stopFail", Integer.valueOf(level)), e);
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
        if (log.isDebugEnabled()) {
            log.debug(String.format("setLocalMemberProperties(%s, %d, %d, %d)", listenHost,
                    Integer.valueOf(listenPort), Integer.valueOf(securePort), Integer.valueOf(udpPort)));
        }
        properties.setProperty("tcpListenHost", listenHost);
        properties.setProperty("tcpListenPort", String.valueOf(listenPort));
        properties.setProperty("udpListenPort", String.valueOf(udpPort));
        properties.setProperty("tcpSecurePort", String.valueOf(securePort));

        try {
            createOrUpdateLocalMember();
            localMember.setPayload(payload);
            localMember.setDomain(domain);
            localMember.getData(true, true);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void createOrUpdateLocalMember() throws IOException {
        String host = properties.getProperty("tcpListenHost");
        int port = Integer.parseInt(properties.getProperty("tcpListenPort"));
        int securePort = Integer.parseInt(properties.getProperty("tcpSecurePort"));
        int udpPort = Integer.parseInt(properties.getProperty("udpListenPort"));

        if (localMember == null) {
            localMember = new MemberImpl();
            localMember.setUniqueId(INITIAL_ID);
            localMember.setLocal(true);
        }
        localMember.setHostname(host);
        localMember.setPort(port);
        localMember.setSecurePort(securePort);
        localMember.setUdpPort(udpPort);
        localMember.getData(true, true);
    }

    @Override
    public void setPayload(byte[] payload) {
        this.payload = payload;
        if (localMember != null) {
            localMember.setPayload(payload);
        }
    }

    @Override
    public void setDomain(byte[] domain) {
        this.domain = domain;
        if (localMember != null) {
            localMember.setDomain(domain);
        }
    }

    @Override
    public MembershipProvider getMembershipProvider() {
        return membershipProvider;
    }

    public void setMembershipProvider(MembershipProvider memberProvider) {
        this.membershipProvider = memberProvider;
    }

    @Override
    public int getConnectTimeout() {
        return Integer.parseInt(properties.getProperty("connectTimeout", "1000"));
    }

    public void setConnectTimeout(int connectTimeout) {
        properties.setProperty("connectTimeout", String.valueOf(connectTimeout));
    }

    @Override
    public int getReadTimeout() {
        return Integer.parseInt(properties.getProperty("readTimeout", "1000"));
    }

    public void setReadTimeout(int readTimeout) {
        properties.setProperty("readTimeout", String.valueOf(readTimeout));
    }

    @Override
    public long getExpirationTime() {
        return Long.parseLong(properties.getProperty("expirationTime", "5000"));
    }

    public void setExpirationTime(long expirationTime) {
        properties.setProperty("expirationTime", String.valueOf(expirationTime));
    }
}
