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
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * A {@link org.apache.catalina.tribes.MembershipProvider} that uses DNS to retrieve the members of a cluster.<br>
 *
 * <p>
 * <strong>Configuration example for Kubernetes</strong>
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
 *             <Membership className="org.apache.catalina.tribes.membership.cloud.CloudMembershipService"
 *                 membershipProviderClassName="org.apache.catalina.tribes.membership.cloud.DNSMembershipProvider"/>
 *           </Channel>
 *         </Cluster>
 *         ...
 *  }
 *  </pre>
 *
 * {@code dns-membership-service.yml }
 *
 * <pre>
 * {@code
 * apiVersion: v1
 * kind: Service
 * metadata:
 *   annotations:
 *     service.alpha.kubernetes.io/tolerate-unready-endpoints: "true"
 *     description: "The service for tomcat cluster membership."
 *   name: my-tomcat-app-membership
 * spec:
 *   clusterIP: None
 *   ports:
 *   - name: membership
 *     port: 8888
 *   selector:
 *     app: my-tomcat-app
 * }
 * </pre>
 *
 * Environment variable configuration<br>
 *
 * {@code DNS_MEMBERSHIP_SERVICE_NAME=my-tomcat-app-membership }
 */

public class DNSMembershipProvider extends CloudMembershipProvider {
    private static final Log log = LogFactory.getLog(DNSMembershipProvider.class);

    private String dnsServiceName;

    @Override
    public void start(int level) throws Exception {
        if ((level & MembershipService.MBR_RX) == 0) {
            return;
        }

        super.start(level);

        // Set up Kubernetes API parameters
        dnsServiceName = getEnv("DNS_MEMBERSHIP_SERVICE_NAME");
        if (dnsServiceName == null) {
            dnsServiceName = getNamespace();
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("Namespace [%s] set; clustering enabled", dnsServiceName));
        }
        dnsServiceName = URLEncoder.encode(dnsServiceName, "UTF-8");

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
            inetAddresses = InetAddress.getAllByName(dnsServiceName);
        } catch (UnknownHostException exception) {
            log.warn(sm.getString("dnsMembershipProvider.dnsError", dnsServiceName), exception);
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

    @Override
    public boolean accept(Serializable msg, Member sender) {
        // Check if the sender is in the member list.
        boolean found = false;
        Member[] members = membership.getMembers();
        if (members != null) {
            for (Member member : members) {
                if (Arrays.equals(sender.getHost(), member.getHost())
                        && sender.getPort() == member.getPort()) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            MemberImpl member = new MemberImpl();
            member.setHost(sender.getHost());
            member.setPort(sender.getPort());
            byte[] host = sender.getHost();
            int i = 0;
            StringBuilder buf = new StringBuilder();
            buf.append(host[i++] & 0xff);
            for (; i < host.length; i++) {
                buf.append(".").append(host[i] & 0xff);
            }

            byte[] id = md5.digest(buf.toString().getBytes());
            member.setUniqueId(id);
            member.setMemberAliveTime(-1);
            updateMember(member, true);
        }
        return false;
    }
}
