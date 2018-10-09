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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.codec.binary.StringUtils;
import org.noggit.JSONParser;


public class KubernetesMembershipProvider extends CloudMembershipProvider {
    private static final Log log = LogFactory.getLog(KubernetesMembershipProvider.class);

    private static final String CUSTOM_ENV_PREFIX = "OPENSHIFT_KUBE_PING_";

    @Override
    public void start(int level) throws Exception {
        if ((level & MembershipService.MBR_RX) == 0) {
            return;
        }

        super.start(level);

        // Set up Kubernetes API parameters
        String namespace = getEnv("KUBERNETES_NAMESPACE", CUSTOM_ENV_PREFIX + "NAMESPACE");
        if (namespace == null || namespace.length() == 0) {
            throw new IllegalArgumentException(sm.getString("kubernetesMembershipProvider.noNamespace"));
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("Namespace [%s] set; clustering enabled", namespace));
        }

        String protocol = getEnv("KUBERNETES_MASTER_PROTOCOL", CUSTOM_ENV_PREFIX + "MASTER_PROTOCOL");
        String masterHost = getEnv("KUBERNETES_SERVICE_HOST", CUSTOM_ENV_PREFIX + "MASTER_HOST");
        String masterPort = getEnv("KUBERNETES_SERVICE_PORT", CUSTOM_ENV_PREFIX + "MASTER_PORT");

        String clientCertificateFile = getEnv("KUBERNETES_CLIENT_CERTIFICATE_FILE", CUSTOM_ENV_PREFIX + "CLIENT_CERT_FILE");
        String caCertFile = getEnv("KUBERNETES_CA_CERTIFICATE_FILE", CUSTOM_ENV_PREFIX + "CA_CERT_FILE");
        if (caCertFile == null) {
            caCertFile = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
        }

        if (clientCertificateFile == null) {
            if (protocol == null) {
                protocol = "https";
            }
            String saTokenFile = getEnv("SA_TOKEN_FILE", CUSTOM_ENV_PREFIX + "SA_TOKEN_FILE");
            if (saTokenFile == null) {
                saTokenFile = "/var/run/secrets/kubernetes.io/serviceaccount/token";
            }
            byte[] bytes = Files.readAllBytes(FileSystems.getDefault().getPath(saTokenFile));
            streamProvider = new TokenStreamProvider(StringUtils.newStringUsAscii(bytes), caCertFile);
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

        String ver = getEnv("KUBERNETES_API_VERSION", CUSTOM_ENV_PREFIX + "API_VERSION");
        if (ver == null)
            ver = "v1";

        String labels = getEnv("KUBERNETES_LABELS", CUSTOM_ENV_PREFIX + "LABELS");

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

    protected void parsePods(Reader reader, List<MemberImpl> members)
            throws IOException{

        int event = 0;
        JSONParser parser = new JSONParser(reader);
        boolean parseItems = false;
        for (;;) {
            event = parser.nextEvent();
            switch (event) {
            case JSONParser.STRING:
                String value = parser.getString();
                if (parser.wasKey()) {
                    if ("items".equals(value) && parser.nextEvent() == JSONParser.ARRAY_START) {
                        parseItems = true;
                    }
                }
                break;
            case JSONParser.ARRAY_END:
                parseItems = false;
                break;
            case JSONParser.OBJECT_START:
                if (parseItems) {
                    parseItem(parser, members);
                }
                break;
            default:
                break;
            }

            if (event == JSONParser.EOF)
                break;
        }

    }

    private void parseItem(JSONParser parser, List<MemberImpl> members)
            throws IOException {
        int event = 0;
        String podIP = null;
        // Name in first position, creation in second
        String[] nameAndCreationTimestamp = null;
        for (;;) {
            event = parser.nextEvent();
            switch (event) {
            case JSONParser.STRING:
                String value = parser.getString();
                if (parser.wasKey()) {
                    if ("kind".equals(value)) {
                        // Verify the item is a pod
                        if (parser.nextEvent() != JSONParser.STRING && (!"Pod".equals(parser.getString()))) {
                            throw new IllegalStateException();
                        }
                    } else if ("status".equals(value)) {
                        // Verify the status is Running and return the podIP
                        podIP = parseStatus(parser);
                    } else if ("metadata".equals(value)) {
                        nameAndCreationTimestamp = parseMetadata(parser);
                    }
                }
                break;
            case JSONParser.OBJECT_END:
                // Done
                if (podIP == null || nameAndCreationTimestamp == null
                    || nameAndCreationTimestamp[0] == null
                    || nameAndCreationTimestamp[1] == null) {
                    throw new IOException(sm.getString("kubernetesMembershipProvider.jsonError"));
                } else {
                    // We found ourselves, ignore
                    if (nameAndCreationTimestamp[0].equals(hostName))
                        return;

                    // id = md5(hostname)
                    byte[] id = md5.digest(nameAndCreationTimestamp[0].getBytes());
                    long aliveTime = Duration.between(Instant.parse(nameAndCreationTimestamp[1]), startTime).getSeconds() * 1000; // aliveTime is in ms

                    MemberImpl member = null;
                    try {
                        member = new MemberImpl(podIP, port, aliveTime);
                    } catch (IOException e) {
                        // Shouldn't happen:
                        // an exception is thrown if hostname can't be resolved to IP, but we already provide an IP
                        log.error(sm.getString("kubernetesMembershipProvider.memberError"), e);
                        continue;
                    }

                    member.setUniqueId(id);
                    members.add(member);
                }
                return;
            default:
                break;
            }

            if (event == JSONParser.EOF)
                break;
        }

    }

    private String parseStatus(JSONParser parser)
            throws IOException {
        String result = null;
        int event = 0;
        for (;;) {
            event = parser.nextEvent();
            switch (event) {
            case JSONParser.STRING:
                String value = parser.getString();
                if (parser.wasKey()) {
                    if ("phase".equals(value)) {
                        // Verify the item is a pod
                        if (parser.nextEvent() != JSONParser.STRING && (!"Running".equals(parser.getString()))) {
                            return null;
                        }
                    } else if ("podIP".equals(value)) {
                        if (parser.nextEvent() == JSONParser.STRING) {
                            result = parser.getString();
                        }
                    }
                }
                break;
            case JSONParser.OBJECT_END:
                // Done
                return result;
            default:
                break;
            }

            if (event == JSONParser.EOF)
                break;
        }
        return result;
    }

    private String[] parseMetadata(JSONParser parser)
            throws IOException {
        String[] result = new String[2];
        int event = 0;
        for (;;) {
            event = parser.nextEvent();
            switch (event) {
            case JSONParser.STRING:
                String value = parser.getString();
                if (parser.wasKey()) {
                    if ("name".equals(value)) {
                        if (parser.nextEvent() == JSONParser.STRING) {
                            result[0] = parser.getString();
                        }
                    } else if ("creationTimestamp".equals(value)) {
                        if (parser.nextEvent() == JSONParser.STRING) {
                            result[1] = parser.getString();
                        }
                    }
                }
                break;
            case JSONParser.OBJECT_END:
                // Done
                return result;
            default:
                break;
            }

            if (event == JSONParser.EOF)
                break;
        }
        return result;
    }

}
