/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.net;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.net.openssl.ciphers.MessageDigest;

/**
 * Represents a pre-shared key configuration for a virtual host.
 */
public class SSLHostConfigPreSharedKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SSLHostConfig sslHostConfig;

    private String identity;
    private byte[] key;
    private MessageDigest digest = MessageDigest.SHA256;

    /**
     * Creates a new pre-shared key configuration for the given host.
     *
     * @param sslHostConfig the parent SSL host configuration
     */
    public SSLHostConfigPreSharedKey(SSLHostConfig sslHostConfig) {
        this.sslHostConfig = sslHostConfig;
    }

    /**
     * Returns the SSLHostConfig that owns this pre-shared key configuration.
     *
     * @return the parent SSLHostConfig
     */
    public SSLHostConfig getSSLHostConfig() {
        return sslHostConfig;
    }

    /**
     * Returns the identity associated with the pre-shared key.
     *
     * @return the pre-shared key identity
     */
    public String getIdentity() {
        return identity;
    }

    /**
     * Sets the identity associated with the pre-shared key.
     *
     * @param identity the pre-shared key identity
     */
    public void setIdentity(String identity) {
        this.identity = identity;
    }

    /**
     * Returns the pre-shared key as a hexadecimal string.
     *
     * @return the hexadecimal pre-shared key
     */
    public String getKey() {
        return HexUtils.toHexString(key);
    }

    /**
     * Sets the pre-shared key from a hexadecimal string.
     *
     * @param key the hexadecimal pre-shared key
     *
     * @throws IllegalArgumentException if the key is not valid hexadecimal
     */
    public void setKey(String key) {
        this.key = HexUtils.fromHexString(key);
    }

    /**
     * Returns the pre-shared key.
     *
     * @return the pre-shared key
     */
    public byte[] getKeyInternal() {
        return key;
    }

    /**
     * Returns the message digest algorithm name.
     *
     * @return the message digest algorithm name
     */
    public String getDigest() {
        if (digest == null) {
            return null;
        }
        return digest.name();
    }

    /**
     * Sets the message digest algorithm.
     *
     * @param digest the message digest name
     *
     * @throws IllegalArgumentException if the message digest is not recognized
     */
    public void setDigest(String digest) {
        if (digest == null) {
            this.digest = null;
            return;
        }
        // Remove all "-". Handles SHA-256 as well as odd variations like Sh-A256 but there is no harm in that.
        this.digest = MessageDigest.valueOf(digest.replaceAll("-", "").toUpperCase(Locale.ENGLISH));
    }

    /**
     * Returns the message digest.
     *
     * @return the message digest
     */
    public MessageDigest getDigestInternal() {
        return digest;
    }
}
