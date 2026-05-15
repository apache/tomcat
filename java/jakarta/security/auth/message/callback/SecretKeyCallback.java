/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jakarta.security.auth.message.callback;

import javax.crypto.SecretKey;
import javax.security.auth.callback.Callback;

/**
 * A callback enabling an authentication module to request a secret key from the runtime, by supplying an alias. Other
 * request types may also be supported.
 */
public class SecretKeyCallback implements Callback {

    private final Request request;
    private SecretKey key;

    /**
     * Constructs a SecretKeyCallback with the specified request.
     *
     * @param request The request describing the secret key to obtain
     */
    public SecretKeyCallback(Request request) {
        this.request = request;
    }

    /**
     * Returns the request associated with this callback.
     *
     * @return The request describing the secret key to obtain
     */
    public Request getRequest() {
        return request;
    }

    /**
     * Sets the secret key obtained by the runtime.
     *
     * @param key The secret key
     */
    public void setKey(SecretKey key) {
        this.key = key;
    }

    /**
     * Returns the secret key.
     *
     * @return The secret key, or {@code null} if not set
     */
    public SecretKey getKey() {
        return key;
    }

    /**
     * Represents a request for a secret key.
     * Implementations specify the criteria for locating the desired key.
     */
    public interface Request {
    }

    /**
     * A request for a secret key identified by its alias.
     */
    public static class AliasRequest implements Request {

        private final String alias;

        /**
         * Constructs an AliasRequest with the specified alias.
         *
         * @param alias The alias of the secret key to obtain
         */
        public AliasRequest(String alias) {
            this.alias = alias;
        }

        /**
         * Returns the alias of the requested secret key.
         *
         * @return The alias string
         */
        public String getAlias() {
            return alias;
        }
    }
}
