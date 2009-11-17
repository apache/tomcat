/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.addons;

import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

/**
 * Session management plugin. No dependency on tomcat-lite, should
 * be possible to add this to tomcat-trunk or other containers.
 * 
 * The container will:
 * - extract the session id from request ( via a filter or built-ins )
 * - call this interface when the user makes the related calls in the 
 * servlet API.
 * - provide a context attribute 'context-listeners' with the 
 * List<EventListener> from web.xml 
 *
 * Implementation of this class must provide HttpSession object 
 * and implement the spec. 
 * 
 */
public interface UserSessionManager {

    
    
    HttpSession findSession(String requestedSessionId) throws IOException;

    HttpSession createSession(String requestedSessionId);
  
    boolean isValid(HttpSession session);

    void access(HttpSession session);
  
    void endAccess(HttpSession session);
  
  
    void setSessionTimeout(int to);
    
    void setContext(ServletContext ctx);


}
