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
package jakarta.security.auth.message;

/**
 * Represents the status returned by JASPIC authentication operations. The authentication status indicates whether
 * authentication succeeded, failed, or requires further message exchange.
 */
public class AuthStatus {

    /**
     * Authentication succeeded and no further message exchange is required.
     */
    public static final AuthStatus SUCCESS = new AuthStatus("SUCCESS");

    /**
     * Authentication failed and no further message exchange will change the outcome.
     */
    public static final AuthStatus FAILURE = new AuthStatus("FAILURE");

    /**
     * Authentication succeeded and the response should be sent to the peer.
     */
    public static final AuthStatus SEND_SUCCESS = new AuthStatus("SEND_SUCCESS");

    /**
     * Authentication failed and the failure response should be sent to the peer.
     */
    public static final AuthStatus SEND_FAILURE = new AuthStatus("SEND_FAILURE");

    /**
     * Authentication is in progress and further message exchange is required.
     */
    public static final AuthStatus SEND_CONTINUE = new AuthStatus("SEND_CONTINUE");

    private final String name;

    private AuthStatus(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
