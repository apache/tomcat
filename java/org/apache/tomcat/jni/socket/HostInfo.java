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
package org.apache.tomcat.jni.socket;

import java.io.Serializable;

/**
 * Information about the remote host. Persisting this in memcache or similar
 * storage can improve performance on future TLS connections by skipping roundtrips
 * and reducing CPU use in handshake.
 *
 * This class is used in both server and client mode.
 *
 * AprSocketContextLitener.getPeer(name) can be used to read from an external storage.
 *
 * TODO: also save the SPDY persistent settings here.
 * TODO: fix tickets, don't seem to work anymore.
 */
public class HostInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    public String host;

    public int port;

    public boolean secure;

    /**
     * Raw cert data (x.509 format).
     * This is retrieved when a full handshake happens - if session reuse or tickets
     * are used you'll not receive the certs again.
     */
    public byte[][] certs;

    public byte[] ticket;
    public int ticketLen;

    public String sessionId;

    /**
     * DER-encoded session data.
     */
    public byte[] sessDer;

    /**
     * Negotiated NPN.
     */
    byte[] npn;
    int npnLen;

    public HostInfo() {
    }

    public HostInfo(String host, int port, boolean secure) {
        this.host = host;
        this.port = port;
        this.secure = secure;
    }

    public String getNpn() {
        return new String(npn, 0, npnLen);
    }

    public void setNpn(String npn) {
        if (npn == null) {
            npnLen = 0;
        }
    }
}