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

import java.security.Principal;

import org.ietf.jgss.GSSCredential;

/**
 * Defines additional methods implemented by {@link Principal}s created by
 * Tomcat's standard {@link Realm} implementations.
 */
public interface TomcatPrincipal extends Principal {

    /**
     * The authenticated Principal to be exposed to applications.
     */
    Principal getUserPrincipal();

    /**
     * The user's delegated credentials.
     */
    GSSCredential getGssCredential();

    /**
     * Calls logout, if necessary, on any associated JAASLoginContext. May in
     * the future be extended to cover other logout requirements.
     *
     * @throws Exception If something goes wrong with the logout. Uses Exception
     *                   to allow for future expansion of this method to cover
     *                   other logout mechanisms that might throw a different
     *                   exception to LoginContext
     *
     */
    void logout() throws Exception;
}
