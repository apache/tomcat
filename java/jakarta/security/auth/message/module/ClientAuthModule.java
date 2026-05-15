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
import jakarta.security.auth.message.ClientAuth;
import jakarta.security.auth.message.MessagePolicy;

/**
 * A pluggable client-side authentication module in JASPIC. A ClientAuthModule implements the authentication logic
 * for client-side operations and is initialized with message policies, a callback handler, and configuration
 * options.
 */
public interface ClientAuthModule extends ClientAuth {

    /**
     * Initializes the authentication module with the given policies, callback handler, and options. This method is
     * called once when the module is created.
     *
     * @param requestPolicy  the message policy for requests
     * @param responsePolicy the message policy for responses
     * @param handler        the callback handler for obtaining sensitive data
     * @param options        configuration options for the module
     *
     * @throws AuthException if initialization fails
     */
    void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy, CallbackHandler handler,
            Map<String,Object> options) throws AuthException;

    /**
     * Returns the array of MessageInfo implementation classes supported by this module.
     *
     * @return an array of supported MessageInfo classes
     */
    Class<?>[] getSupportedMessageTypes();
}
