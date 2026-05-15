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
package jakarta.security.auth.message.module;

import java.util.Map;

import javax.security.auth.callback.CallbackHandler;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.MessagePolicy;
import jakarta.security.auth.message.ServerAuth;

/**
 * Provides server-side authentication of requests and response signing.
 * <p>
 * This interface extends {@link ServerAuth} and is implemented by authentication
 * module providers. It is initialized by the container and provides the actual
 * authentication logic for server-side message authentication.
 */
public interface ServerAuthModule extends ServerAuth {

    /**
     * Initialize the authentication module.
     * <p>
     * This method is called by the container to initialize the module with the
     * request and response policies, a callback handler, and a set of options.
     *
     * @param requestPolicy  The policy for the request message
     * @param responsePolicy The policy for the response message
     * @param handler        The callback handler used to obtain credentials
     * @param options        The options provided to the module
     *
     * @throws AuthException If an error occurs during initialization
     */
    void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy, CallbackHandler handler,
            Map<String,Object> options) throws AuthException;

    /**
     * Returns the array of message types supported by the module.
     *
     * @return The array of supported message type classes
     */
    Class<?>[] getSupportedMessageTypes();
}
