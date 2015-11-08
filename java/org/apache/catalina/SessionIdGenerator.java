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
package org.apache.catalina;

public interface SessionIdGenerator {

    /**
     * Return the node identifier associated with this node which will be
     * included in the generated session ID.
     */
    public String getJvmRoute();

    /**
     * Specify the node identifier associated with this node which will be
     * included in the generated session ID.
     *
     * @param jvmRoute  The node identifier
     */
    public void setJvmRoute(String jvmRoute);

    /**
     * Return the number of bytes for a session ID
     */
    public int getSessionIdLength();

    /**
     * Specify the number of bytes for a session ID
     *
     * @param sessionIdLength   Number of bytes
     */
    public void setSessionIdLength(int sessionIdLength);

    /**
     * Generate and return a new session identifier.
     */
    public String generateSessionId();

    /**
     * Generate and return a new session identifier.
     *
     * @param route   node identifier to include in generated id
     */
    public String generateSessionId(String route);

    /**
     * Determine, based on implementation specific rules which may be as strict
     * or as relaxed as the implementor wishes, if the provided session ID is
     * valid. This may be used when generating sessions with user provided
     * session IDs to ensure that they are suitable or if a new ID needs to be
     * generated.
     *
     * @param sessionId The proposed session ID to test
     *
     * @return {@code true} if the proposed session ID is acceptable, otherwise
     *         {@code false}
     */
    public boolean validateSessionId(String sessionId);
}
