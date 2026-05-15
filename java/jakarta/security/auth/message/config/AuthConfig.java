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
package jakarta.security.auth.message.config;

import jakarta.security.auth.message.MessageInfo;

/**
 * Provides configuration information for a JASPIC authentication mechanism. An AuthConfig defines the message layer,
 * application context, and authentication context ID for a given message, and indicates whether the mechanism is
 * protected.
 */
public interface AuthConfig {

    /**
     * Returns the message layer for which this configuration applies.
     *
     * @return the message layer
     */
    String getMessageLayer();

    /**
     * Returns the application context for which this configuration applies.
     *
     * @return the application context
     */
    String getAppContext();

    /**
     * Determines the authentication context ID for the given message.
     *
     * @param messageInfo the message information used to determine the context ID
     *
     * @return the authentication context ID, or {@code null} if no context applies
     */
    String getAuthContextID(MessageInfo messageInfo);

    /**
     * Refreshes the configuration, reloading any changed settings.
     */
    void refresh();

    /**
     * Indicates whether the authentication mechanism is protected, meaning that the configuration data is stored
     * securely.
     *
     * @return {@code true} if the mechanism is protected, {@code false} otherwise
     */
    boolean isProtected();
}
