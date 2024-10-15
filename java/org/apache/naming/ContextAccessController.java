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
package org.apache.naming;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the access control on the JNDI contexts.
 *
 * @author Remy Maucherat
 */
public class ContextAccessController {

    // -------------------------------------------------------------- Variables

    /**
     * Catalina context names on which writing is not allowed.
     */
    private static final Map<Object,Object> readOnlyContexts = new ConcurrentHashMap<>();


    /**
     * Security tokens repository.
     */
    private static final Map<Object,Object> securityTokens = new ConcurrentHashMap<>();


    // --------------------------------------------------------- Public Methods

    /**
     * Set a security token for a Catalina context. Can be set only once.
     *
     * @param name Name of the Catalina context
     * @param token Security token
     */
    public static void setSecurityToken(Object name, Object token) {
        if ((!securityTokens.containsKey(name)) && (token != null)) {
            securityTokens.put(name, token);
        }
    }


    /**
     * Remove a security token for a context.
     *
     * @param name Name of the Catalina context
     * @param token Security token
     */
    public static void unsetSecurityToken(Object name, Object token) {
        if (checkSecurityToken(name, token)) {
            securityTokens.remove(name);
        }
    }


    /**
     * Check a submitted security token.
     *
     * @param name Name of the Catalina context
     * @param token Submitted security token
     *
     * @return <code>true</code> if the submitted token is equal to the token
     *         in the repository or if no token is present in the repository.
     *         Otherwise, <code>false</code>
     */
    public static boolean checkSecurityToken
        (Object name, Object token) {
        Object refToken = securityTokens.get(name);
        return (refToken == null || refToken.equals(token));
    }


    /**
     * Allow writing to a context.
     *
     * @param name Name of the Catalina context
     * @param token Security token
     */
    public static void setWritable(Object name, Object token) {
        if (checkSecurityToken(name, token)) {
            readOnlyContexts.remove(name);
        }
    }


    /**
     * Set whether or not a Catalina context is writable.
     *
     * @param name Name of the Catalina context
     */
    public static void setReadOnly(Object name) {
        readOnlyContexts.put(name, name);
    }


    /**
     * Is the context is writable?
     *
     * @param name Name of the Catalina context
     *
     * @return <code>true</code> if it is writable, otherwise <code>false</code>
     */
    public static boolean isWritable(Object name) {
        return !(readOnlyContexts.containsKey(name));
    }
}

