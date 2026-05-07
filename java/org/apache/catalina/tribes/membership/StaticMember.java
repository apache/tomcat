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
package org.apache.catalina.tribes.membership;

import java.io.IOException;

import org.apache.catalina.tribes.util.Arrays;

/**
 * Static member representation for cluster membership.
 */
public class StaticMember extends MemberImpl {
    /**
     * Default constructor.
     */
    public StaticMember() {
        super();
    }

    /**
     * Creates a static member with the given host, port, and alive time.
     * @param host the host address
     * @param port the port number
     * @param aliveTime the time the member was last alive
     * @throws IOException if serialization fails
     */
    public StaticMember(String host, int port, long aliveTime) throws IOException {
        super(host, port, aliveTime);
    }

    /**
     * Creates a static member with the given host, port, alive time, and payload.
     * @param host the host address
     * @param port the port number
     * @param aliveTime the time the member was last alive
     * @param payload the member payload data
     * @throws IOException if serialization fails
     */
    public StaticMember(String host, int port, long aliveTime, byte[] payload) throws IOException {
        super(host, port, aliveTime, payload);
    }

    /**
     * Sets the host for this member.
     * @param host String, either in byte array string format, like {214,116,1,3} or as a regular hostname, 127.0.0.1 or
     *                 tomcat01.mydomain.com
     */
    public void setHost(String host) {
        if (host == null) {
            return;
        }
        if (host.startsWith("{")) {
            setHost(Arrays.fromString(host));
        } else {
            try {
                setHostname(host);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

    }

    /**
     * Sets the domain for this member.
     * @param domain String, either in byte array string format, like {214,116,1,3} or as a regular string value like
     *                   'mydomain'. The latter will be converted using ISO-8859-1 encoding
     */
    public void setDomain(String domain) {
        if (domain == null) {
            return;
        }
        if (domain.startsWith("{")) {
            setDomain(Arrays.fromString(domain));
        } else {
            setDomain(Arrays.convert(domain));
        }
    }

    /**
     * Sets the unique identifier for this member.
     * @param id String, must be in byte array string format, like {214,116,1,3} and exactly 16 bytes long
     */
    public void setUniqueId(String id) {
        byte[] uuid = Arrays.fromString(id);
        if (uuid == null || uuid.length != 16) {
            throw new RuntimeException(sm.getString("staticMember.invalid.uuidLength", id));
        }
        setUniqueId(uuid);
    }


}