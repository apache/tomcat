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
import java.io.Serializable;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.Heartbeat;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.membership.Membership;
import org.apache.catalina.tribes.membership.MembershipProviderBase;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Abstract base class for cloud-based membership providers.
 */
public abstract class CloudMembershipProvider extends MembershipProviderBase implements Heartbeat, ChannelListener {
    private static final Log log = LogFactory.getLog(CloudMembershipProvider.class);
    /**
     * String manager for this class.
     */
    protected static final StringManager sm = StringManager.getManager(CloudMembershipProvider.class);

    /**
     * Prefix for custom environment variables.
     */
    protected static final String CUSTOM_ENV_PREFIX = "OPENSHIFT_KUBE_PING_";

    /**
     * The URL for the cloud membership service.
     */
    protected String url;
    /**
     * The provider for cloud API streams.
     */
    protected StreamProvider streamProvider;
    /**
     * Connection timeout in milliseconds.
     */
    protected int connectionTimeout;
    /**
     * Read timeout in milliseconds.
     */
    protected int readTimeout;

    /**
     * The time when this provider started.
     */
    protected Instant startTime;
    /**
     * MD5 message digest for hashing operations.
     */
    protected MessageDigest md5;

    /**
     * HTTP headers for cloud API requests.
     */
    protected Map<String,String> headers = new HashMap<>();

    /**
     * The local IP address.
     */
    protected String localIp;
    /**
     * The local port number.
     */
    protected int port;

    /**
     * Member expiration time in milliseconds.
     */
    protected long expirationTime = 5000;

    /**
     * Creates a new CloudMembershipProvider and initializes the MD5 digest.
     */
    public CloudMembershipProvider() {
        try {
            md5 = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            // Ignore
        }
    }

    /**
     * Get value of environment variable.
     *
     * @param keys the environment variables
     *
     * @return the env variables values, or null if not found
     */
    protected static String getEnv(String... keys) {
        String val = null;
        for (String key : keys) {
            val = System.getenv(key);
            if (val != null) {
                break;
            }
        }
        return val;
    }

    /**
     * Get the Kubernetes namespace, or "tomcat" if the Kubernetes environment variable cannot be found (with a warning
     * log about the missing namespace).
     *
     * @return the namespace
     */
    protected String getNamespace() {
        String namespace = getEnv(CUSTOM_ENV_PREFIX + "NAMESPACE", "KUBERNETES_NAMESPACE");
        if (namespace == null || namespace.isEmpty()) {
            log.warn(sm.getString("kubernetesMembershipProvider.noNamespace"));
            namespace = "tomcat";
        }
        return namespace;
    }

    @Override
    public void init(Properties properties) throws IOException {
        startTime = Instant.now();

        CloudMembershipService service = (CloudMembershipService) this.service;
        connectionTimeout = service.getConnectTimeout();
        readTimeout = service.getReadTimeout();
        expirationTime = service.getExpirationTime();

        localIp = InetAddress.getLocalHost().getHostAddress();
        port = Integer.parseInt(properties.getProperty("tcpListenPort"));
    }

    @Override
    public void start(int level) throws Exception {
        if (membership == null) {
            membership = new Membership(service.getLocalMember(true));
        }
        service.getChannel().addChannelListener(this);
    }

    @Override
    public boolean stop(int level) throws Exception {
        return true;
    }

    @Override
    public void heartbeat() {
        Member[] announcedMembers = fetchMembers();
        // Add new members or refresh the members in the membership
        for (Member member : announcedMembers) {
            updateMember(member, true);
        }
        // Remove non refreshed members from the membership
        Member[] expired = membership.expire(expirationTime);
        for (Member member : expired) {
            updateMember(member, false);
        }
    }

    /**
     * Fetch current cluster members from the cloud orchestration.
     *
     * @return the member array
     */
    protected abstract Member[] fetchMembers();

    /**
     * Add or remove specified member.
     *
     * @param member the member to add
     * @param add    true if the member is added, false otherwise
     */
    protected void updateMember(Member member, boolean add) {
        if (add && !membership.memberAlive(member)) {
            return;
        }
        if (log.isDebugEnabled()) {
            String message = add ? sm.getString("cloudMembershipProvider.add", member) :
                    sm.getString("cloudMembershipProvider.remove", member);
            log.debug(message);
        }
        Runnable r = () -> {
            Thread currentThread = Thread.currentThread();
            String name = currentThread.getName();
            try {
                String threadName = add ? "CloudMembership-memberAdded" : "CloudMembership-memberDisappeared";
                currentThread.setName(threadName);
                if (add) {
                    membershipListener.memberAdded(member);
                } else {
                    membershipListener.memberDisappeared(member);
                }
            } finally {
                currentThread.setName(name);
            }
        };
        executor.execute(r);
    }

    @Override
    public void messageReceived(Serializable msg, Member sender) {
    }

    @Override
    public boolean accept(Serializable msg, Member sender) {
        return false;
    }

}
