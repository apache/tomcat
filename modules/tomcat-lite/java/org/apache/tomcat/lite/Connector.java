/*
 */
package org.apache.tomcat.lite;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.integration.ObjectManager;

/**
 * What we need to plugin a connector.
 * 
 * Currently we have lots of deps on coyote Request, but I plan to
 * change this and allow other HTTP implementations - like MINA, the
 * experimental async connector, etc. Most important will be 
 * different support for IO - i.e. a more non-blocking mode.
 * We'll probably keep MessageBytes as wrappers for request/res
 * properties. 
 * 
 * This interface has no dep on coyote.
 *  
 */
public interface Connector {

    public void setDaemon(boolean b);
    
    public void start();
    
    public void stop();
    
    public void finishResponse(HttpServletResponse res) throws IOException;
    
    public void recycle(HttpServletRequest req, HttpServletResponse res);

    void initRequest(HttpServletRequest req, HttpServletResponse res);

    public void setTomcatLite(TomcatLite tomcatLite);

    public void setObjectManager(ObjectManager objectManager);
}
