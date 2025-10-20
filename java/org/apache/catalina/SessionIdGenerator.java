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
     * @return the node identifier associated with this node which will be included in the generated session ID.
     */
    String getJvmRoute();

    /**
     * Specify the node identifier associated with this node which will be included in the generated session ID.
     *
     * @param jvmRoute The node identifier
     */
    void setJvmRoute(String jvmRoute);

    /**
     * @return the number of bytes for a session ID
     */
    int getSessionIdLength();

    /**
     * Specify the number of bytes for a session ID
     *
     * @param sessionIdLength Number of bytes
     */
    void setSessionIdLength(int sessionIdLength);

    /**
     * Generate and return a new session identifier.
     *
     * @return the newly generated session id
     */
    String generateSessionId();

    /**
     * Generate and return a new session identifier.
     *
     * @param route node identifier to include in generated id
     *
     * @return the newly generated session id
     */
    String generateSessionId(String route);
}
