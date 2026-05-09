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

import java.util.Map;

/**
 * Represents the request and response messages associated with a
 * message-based authentication exchange.  The {@code MessageInfo} object
 * provides access to the request and response message objects, as well
 * as a map of additional context information.  The types of the request
 * and response message objects are protocol-specific.
 */
public interface MessageInfo {

    /**
     * Returns the request message object associated with this
     * authentication exchange.  The type of the returned object is
     * protocol-specific.
     *
     * @return the request message object, or {@code null} if not available
     */
    Object getRequestMessage();

    /**
     * Returns the response message object associated with this
     * authentication exchange.  The type of the returned object is
     * protocol-specific.
     *
     * @return the response message object, or {@code null} if not available
     */
    Object getResponseMessage();

    /**
     * Sets the request message object for this authentication exchange.
     *
     * @param request the request message object
     */
    void setRequestMessage(Object request);

    /**
     * Sets the response message object for this authentication exchange.
     *
     * @param response the response message object
     */
    void setResponseMessage(Object response);

    /**
     * Returns a map of additional context information associated with
     * this authentication exchange.  The map may contain protocol-specific
     * attributes such as message headers.
     *
     * @return a {@code Map} of context information, or {@code null} if not available
     */
    Map<String,Object> getMap();
}
