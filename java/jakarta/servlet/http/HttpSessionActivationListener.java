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
package jakarta.servlet.http;

import java.util.EventListener;

/**
 * Objects that are bound to a session may listen to container events notifying
 * them that sessions will be passivated and that session will be activated. A
 * container that migrates session between VMs or persists sessions is required
 * to notify all attributes bound to sessions implementing
 * HttpSessionActivationListener.
 *
 * @since 2.3
 */
public interface HttpSessionActivationListener extends EventListener {

    /**
     * Notification that the session is about to be passivated.
     * The default implementation is a NO-OP.
     *
     * @param se Information about the session this is about to be passivated
     */
    public default void sessionWillPassivate(HttpSessionEvent se) {
    }

    /**
     * Notification that the session has just been activated.
     * The default implementation is a NO-OP.
     *
     * @param se Information about the session this has just been activated
     */
    public default void sessionDidActivate(HttpSessionEvent se) {
    }
}

