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
package javax.servlet.http;

import java.util.EventListener;

/**
 * Implementations of this interface are notified when an {@link HttpSession}'s
 * ID changes. To receive notification events, the implementation class must be
 * configured in the deployment descriptor for the web application, annotated
 * with {@link javax.servlet.annotation.WebListener} or registered by calling an
 * addListener method on the {@link javax.servlet.ServletContext}.
 *
 * @see HttpSessionEvent
 * @see HttpServletRequest#changeSessionId()
 * @since Servlet 3.1
 */
public interface HttpSessionIdListener extends EventListener {

    /**
     * Notification that a session ID has been changed.
     *
     * @param se the notification event
     * @param oldSessionId the old session ID
     */
    public void sessionIdChanged(HttpSessionEvent se, String oldSessionId);
}
