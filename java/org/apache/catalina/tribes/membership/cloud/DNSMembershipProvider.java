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
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class DNSMembershipProvider extends CloudMembershipProvider {
    private static final Log log = LogFactory.getLog(DNSMembershipProvider.class);

    private static final String CUSTOM_ENV_PREFIX = "OPENSHIFT_KUBE_PING_";
    private String namespace;

    @Override
    public void start(int level) throws Exception {
        if ((level & MembershipService.MBR_RX) == 0) {
            return;
        }

        super.start(level);

        // Set up Kubernetes API parameters
        namespace = getEnv("KUBERNETES_NAMESPACE", CUSTOM_ENV_PREFIX + "NAMESPACE");
        if (namespace == null || namespace.length() == 0) {
            throw new IllegalArgumentException(sm.getString("kubernetesMembershipProvider.noNamespace"));
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("Namespace [%s] set; clustering enabled", namespace));
        }
        namespace = URLEncoder.encode(namespace, "UTF-8");

        // Fetch initial members
        heartbeat();
    }

    @Override
    public boolean stop(int level) throws Exception {
        return super.stop(level);
    }

    @Override
    protected Member[] fetchMembers() {
        List<MemberImpl> members = new ArrayList<>();

        InetAddress[] inetAddresses = null;
        try {
            inetAddresses = InetAddress.getAllByName(namespace);
        } catch (UnknownHostException exception) {
            log.warn(sm.getString("dnsMembershipProvider.dnsError", namespace), exception);
        }

        if (inetAddresses != null) {
            for (InetAddress inetAddress : inetAddresses) {
                String ip = inetAddress.getHostAddress();
                byte[] id = md5.digest(ip.getBytes());
                // We found ourselves, ignore
                if (ip.equals(localIp)) {
                    // Update the UID on initial lookup
                    Member localMember = service.getLocalMember(false);
                    if (localMember.getUniqueId() == CloudMembershipService.INITIAL_ID && localMember instanceof MemberImpl) {
                        ((MemberImpl) localMember).setUniqueId(id);
                    }
                    continue;
                }
                long aliveTime = -1;
                MemberImpl member = null;
                try {
                    member = new MemberImpl(ip, port, aliveTime);
                } catch (IOException e) {
                    log.error(sm.getString("kubernetesMembershipProvider.memberError"), e);
                    continue;
                }
                member.setUniqueId(id);
                members.add(member);
            }
        }

        return members.toArray(new Member[0]);
    }
}