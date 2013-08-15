/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.websocket;

public abstract class Endpoint {

    public abstract EndpointConfiguration getEndpointConfiguration();

    /**
     * Event that is triggered when a new session starts.
     *
     * @param session   The new session.
     */
    public abstract void onOpen(Session session);

    /**
     * Event that is triggered when a session has closed.
     *
     * @param closeReason   Why the session was closed
     */
    public void onClose(CloseReason closeReason) {
        // NO-OP by default
    }

    /**
     * Event that is triggered when a protocol error occurs.
     *
     * @param throwable The exception
     */
    public void onError(Throwable throwable) {
        // NO-OP by default
    }
}
