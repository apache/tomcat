/*
 */
package org.apache.tomcat.servlets.session;

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
