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
package org.apache.tomcat.util.net.openssl;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.PreSharedKeySelector;
import org.apache.tomcat.util.net.SSLHostConfigPreSharedKey;
import org.apache.tomcat.util.res.StringManager;

/**
 * Callback implementation that Tomcat uses to select a pre-shared key for a given connection based on the identity
 * provided by the client.
 */
public class OpenSSLPreSharedKeySelector implements PreSharedKeySelector {

    private static final Log log = LogFactory.getLog(OpenSSLPreSharedKeySelector.class);
    private static final StringManager sm = StringManager.getManager(OpenSSLPreSharedKeySelector.class);

    private final Map<String,SSLHostConfigPreSharedKey> identityToKeyMap = new HashMap<>();

    public OpenSSLPreSharedKeySelector(Set<SSLHostConfigPreSharedKey> psks) {
        for (SSLHostConfigPreSharedKey psk : psks) {
            SSLHostConfigPreSharedKey old = identityToKeyMap.put(psk.getIdentity(), psk);
            if (old != null) {
                throw new IllegalArgumentException(sm.getString("opensslPreSharedKeySelector.identity.duplicate",
                        psk.getIdentity(), psk.getSSLHostConfig().getHostName()));
            }
        }
    }

    @Override
    public byte[] select(long ssl, String identity) {
        SSLHostConfigPreSharedKey psk = identityToKeyMap.get(identity);
        if (psk == null) {
            return null;
        }
        // Need to limit keys to 48 bytes for TLS 1.3
        return truncateToLength(identity, psk.getKeyInternal(), 512);
    }

    @Override
    public byte[] select(long ssl, byte[] identity, int[] cipherSuite) {
        String identityString = new String(identity, StandardCharsets.UTF_8);
        SSLHostConfigPreSharedKey psk = identityToKeyMap.get(identityString);
        if (psk == null) {
            return null;
        }
        switch (psk.getDigestInternal()) {
            case SHA256:
                // Any TLS ciphersuite that OpenSSL recognises that uses SHA256 works here
                cipherSuite[0] = 0x1301;
                break;
            case SHA384:
                // Any TLS ciphersuite that OpenSSL recognises that uses SHA384 works here
                cipherSuite[0] = 0x1302;
                break;
            case AEAD:
            case GOST89MAC:
            case GOST94:
            case MD5:
            case SHA1:
            default:
                // Unsupported digest
                return null;
        }
        // Need to limit keys to 48 bytes for TLS 1.3
        return truncateToLength(identityString, psk.getKeyInternal(), 48);
    }


    private byte[] truncateToLength(String identity, byte[] input, int length) {
        byte[] result;
        if (input.length > length) {
            result = new byte[length];
            System.arraycopy(input, 0, result, 0, length);
            log.warn(sm.getString("opensslPreSharedKeySelector.truncate", identity, Integer.toString(length)));
        } else {
            result = input;
        }
        return result;
    }
}
