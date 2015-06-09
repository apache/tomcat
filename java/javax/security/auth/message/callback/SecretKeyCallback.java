/**
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
package javax.security.auth.message.callback;

import javax.crypto.SecretKey;
import javax.security.auth.callback.Callback;

/**
 * A callback enabling an authentication module to request a secret key from the runtime, by supplying an alias.
 * Other request types may also be supported.
 */
public class SecretKeyCallback implements Callback {

    private final Request request;
    private SecretKey key;

    public SecretKeyCallback(Request request) {
        this.request = request;
    }

    public Request getRequest() {
        return request;
    }

    public SecretKey getKey() {
        return key;
    }

    public void setKey(SecretKey key) {
        this.key = key;
    }

    public static interface Request {
    }

    public static class AliasRequest implements Request {

        private final String alias;

        public AliasRequest(String alias) {
            this.alias = alias;
        }

        public String getAlias() {
            return alias;
        }
    }
}
