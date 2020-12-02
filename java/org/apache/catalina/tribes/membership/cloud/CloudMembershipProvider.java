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
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
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

public abstract class CloudMembershipProvider extends MembershipProviderBase implements Heartbeat, ChannelListener {
    private static final Log log = LogFactory.getLog(CloudMembershipProvider.class);
    protected static final StringManager sm = StringManager.getManager(CloudMembershipProvider.class);

    protected static final String CUSTOM_ENV_PREFIX = "OPENSHIFT_KUBE_PING_";

    protected String url;
    protected StreamProvider streamProvider;
    protected int connectionTimeout;
    protected int readTimeout;

    protected Instant startTime;
    protected MessageDigest md5;

    protected Map<String, String> headers = new HashMap<>();

    protected String localIp;
    protected int port;

    protected long expirationTime = 5000;

    public CloudMembershipProvider() {
        try {
            md5 = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            // Ignore
        }
    }

    /**
     * Get value of environment variable.
     * @param keys the environment variables
     * @return the env variables values, or null if not found
     */
    protected static String getEnv(String... keys) {
        String val = null;
        for (String key : keys) {
            val = AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getenv(key));
            if (val != null)
                break;
        }
        return val;
    }

    /**
     * Get the Kubernetes namespace, or "tomcat" if the Kubernetes environment variable
     * cannot be found (with a warning log about the missing namespace).
     * @return the namespace
     */
    protected String getNamespace() {
        String namespace = getEnv(CUSTOM_ENV_PREFIX + "NAMESPACE", "KUBERNETES_NAMESPACE");
        if (namespace == null || namespace.length() == 0) {
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
            if (membership.memberAlive(member)) {
                if (log.isDebugEnabled()) {
                    log.debug("Member added: " + member);
                }
                Runnable r = () -> {
                    String name = Thread.currentThread().getName();
                    try {
                        Thread.currentThread().setName("CloudMembership-memberAdded");
                        membershipListener.memberAdded(member);
                    } finally {
                        Thread.currentThread().setName(name);
                    }
                };
                executor.execute(r);
            }
        }
        // Remove non refreshed members from the membership
        Member[] expired = membership.expire(expirationTime);
        for (Member member : expired) {
            if (log.isDebugEnabled()) {
                log.debug("Member disappeared: " + member);
            }
            Runnable r = () -> {
                String name = Thread.currentThread().getName();
                try {
                    Thread.currentThread().setName("CloudMembership-memberDisappeared");
                    membershipListener.memberDisappeared(member);
                } finally {
                    Thread.currentThread().setName(name);
                }
            };
            executor.execute(r);
        }
    }

    /**
     * Fetch current cluster members from the cloud orchestration.
     * @return the member array
     */
    protected abstract Member[] fetchMembers();

    @Override
    public void messageReceived(Serializable msg, Member sender) {
    }

    @Override
    public boolean accept(Serializable msg, Member sender) {
        return false;
    }

}
