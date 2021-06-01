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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.json.JSONParser;

/**
 * A {@link org.apache.catalina.tribes.MembershipProvider} that uses Kubernetes API to retrieve the members of a cluster.<br>
 *
 */

public class KubernetesMembershipProvider extends CloudMembershipProvider {
    private static final Log log = LogFactory.getLog(KubernetesMembershipProvider.class);

    @Override
    public void start(int level) throws Exception {
        if ((level & MembershipService.MBR_RX) == 0) {
            return;
        }

        super.start(level);

        // Set up Kubernetes API parameters
        String namespace = getNamespace();

        if (log.isDebugEnabled()) {
            log.debug(String.format("Namespace [%s] set; clustering enabled", namespace));
        }

        String protocol = getEnv(CUSTOM_ENV_PREFIX + "MASTER_PROTOCOL", "KUBERNETES_MASTER_PROTOCOL");
        String masterHost = getEnv(CUSTOM_ENV_PREFIX + "MASTER_HOST", "KUBERNETES_SERVICE_HOST");
        String masterPort = getEnv(CUSTOM_ENV_PREFIX + "MASTER_PORT", "KUBERNETES_SERVICE_PORT");

        String clientCertificateFile = getEnv(CUSTOM_ENV_PREFIX + "CLIENT_CERT_FILE", "KUBERNETES_CLIENT_CERTIFICATE_FILE");
        String caCertFile = getEnv(CUSTOM_ENV_PREFIX + "CA_CERT_FILE", "KUBERNETES_CA_CERTIFICATE_FILE");
        if (caCertFile == null) {
            caCertFile = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
        }

        if (clientCertificateFile == null) {
            if (protocol == null) {
                protocol = "https";
            }
            String saTokenFile = getEnv(CUSTOM_ENV_PREFIX + "SA_TOKEN_FILE", "SA_TOKEN_FILE");
            if (saTokenFile == null) {
                saTokenFile = "/var/run/secrets/kubernetes.io/serviceaccount/token";
            }
            try {
                byte[] bytes = Files.readAllBytes(FileSystems.getDefault().getPath(saTokenFile));
                streamProvider = new TokenStreamProvider(new String(bytes, StandardCharsets.US_ASCII), caCertFile);
            } catch (IOException e) {
                log.error(sm.getString("kubernetesMembershipProvider.streamError"), e);
            }
        } else {
            if (protocol == null) {
                protocol = "http";
            }
            String clientKeyFile = getEnv("KUBERNETES_CLIENT_KEY_FILE");
            String clientKeyPassword = getEnv("KUBERNETES_CLIENT_KEY_PASSWORD");
            String clientKeyAlgo = getEnv("KUBERNETES_CLIENT_KEY_ALGO");
            if (clientKeyAlgo == null) {
                clientKeyAlgo = "RSA";
            }
            streamProvider = new CertificateStreamProvider(clientCertificateFile, clientKeyFile, clientKeyPassword, clientKeyAlgo, caCertFile);
        }

        String ver = getEnv(CUSTOM_ENV_PREFIX + "API_VERSION", "KUBERNETES_API_VERSION");
        if (ver == null) {
            ver = "v1";
        }

        String labels = getEnv(CUSTOM_ENV_PREFIX + "LABELS", "KUBERNETES_LABELS");

        namespace = URLEncoder.encode(namespace, "UTF-8");
        labels = labels == null ? null : URLEncoder.encode(labels, "UTF-8");

        url = String.format("%s://%s:%s/api/%s/namespaces/%s/pods", protocol, masterHost, masterPort, ver, namespace);
        if (labels != null && labels.length() > 0) {
            url = url + "?labelSelector=" + labels;
        }

        // Fetch initial members
        heartbeat();
    }

    @Override
    public boolean stop(int level) throws Exception {
        try {
            return super.stop(level);
        } finally {
            streamProvider = null;
        }
    }

    @Override
    protected Member[] fetchMembers() {
        if (streamProvider == null) {
            return new Member[0];
        }

        List<MemberImpl> members = new ArrayList<>();

        try (InputStream stream = streamProvider.openStream(url, headers, connectionTimeout, readTimeout);
                InputStreamReader reader = new InputStreamReader(stream, "UTF-8")) {
            parsePods(reader, members);
        } catch (IOException e) {
            log.error(sm.getString("kubernetesMembershipProvider.streamError"), e);
        }

        return members.toArray(new Member[0]);
    }

    @SuppressWarnings("unchecked")
    protected void parsePods(Reader reader, List<MemberImpl> members) {
        JSONParser parser = new JSONParser(reader);
        try {
            LinkedHashMap<String, Object> json = parser.object();
            Object itemsObject = json.get("items");
            if (!(itemsObject instanceof List<?>)) {
                log.error(sm.getString("kubernetesMembershipProvider.invalidPodsList", "no items"));
                return;
            }
            List<Object> items = (List<Object>) itemsObject;
            for (Object podObject : items) {
                if (!(podObject instanceof LinkedHashMap<?, ?>)) {
                    log.warn(sm.getString("kubernetesMembershipProvider.invalidPod"));
                    continue;
                }
                LinkedHashMap<String, Object> pod = (LinkedHashMap<String, Object>) podObject;
                // If there is a "kind", check it is "Pod"
                Object podKindObject = pod.get("kind");
                if (podKindObject != null && !"Pod".equals(podKindObject)) {
                    continue;
                }
                // "metadata" contains "name", "uid" and "creationTimestamp"
                Object metadataObject = pod.get("metadata");
                if (!(metadataObject instanceof LinkedHashMap<?, ?>)) {
                    log.warn(sm.getString("kubernetesMembershipProvider.invalidPod"));
                    continue;
                }
                LinkedHashMap<String, Object> metadata = (LinkedHashMap<String, Object>) metadataObject;
                Object nameObject = metadata.get("name");
                if (nameObject == null) {
                    log.warn(sm.getString("kubernetesMembershipProvider.invalidPod"));
                    continue;
                }
                Object objectUid = metadata.get("uid");
                Object creationTimestampObject = metadata.get("creationTimestamp");
                if (creationTimestampObject == null) {
                    log.warn(sm.getString("kubernetesMembershipProvider.invalidPod"));
                    continue;
                }
                String creationTimestamp = creationTimestampObject.toString();
                // "status" contains "phase" (which must be "Running") and "podIP"
                Object statusObject = pod.get("status");
                if (!(statusObject instanceof LinkedHashMap<?, ?>)) {
                    log.warn(sm.getString("kubernetesMembershipProvider.invalidPod"));
                    continue;
                }
                LinkedHashMap<String, Object> status = (LinkedHashMap<String, Object>) statusObject;
                if (!"Running".equals(status.get("phase"))) {
                    continue;
                }
                Object podIPObject = status.get("podIP");
                if (podIPObject == null) {
                    log.warn(sm.getString("kubernetesMembershipProvider.invalidPod"));
                    continue;
                }
                String podIP = podIPObject.toString();
                String uid = (objectUid == null) ? podIP : objectUid.toString();

                // We found ourselves, ignore
                if (podIP.equals(localIp)) {
                    // Update the UID on initial lookup
                    Member localMember = service.getLocalMember(false);
                    if (localMember.getUniqueId() == CloudMembershipService.INITIAL_ID && localMember instanceof MemberImpl) {
                        byte[] id = md5.digest(uid.getBytes(StandardCharsets.US_ASCII));
                        ((MemberImpl) localMember).setUniqueId(id);
                    }
                    continue;
                }

                long aliveTime = Duration.between(Instant.parse(creationTimestamp), startTime).toMillis();

                MemberImpl member = null;
                try {
                    member = new MemberImpl(podIP, port, aliveTime);
                } catch (IOException e) {
                    // Shouldn't happen:
                    // an exception is thrown if hostname can't be resolved to IP, but we already provide an IP
                    log.error(sm.getString("kubernetesMembershipProvider.memberError"), e);
                    continue;
                }
                byte[] id = md5.digest(uid.getBytes(StandardCharsets.US_ASCII));
                member.setUniqueId(id);
                members.add(member);
            }
        } catch (Exception e) {
            log.error(sm.getString("kubernetesMembershipProvider.jsonError"), e);
        }
    }

}
