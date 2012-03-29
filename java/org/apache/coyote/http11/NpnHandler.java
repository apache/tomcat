/*
 */
package org.apache.coyote.http11;

import org.apache.coyote.Adapter;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Interface specific for protocols that negotiate at NPN level, like
 * SPDY. This is only available for APR, will replace the HTTP framing.
 */
public interface NpnHandler {
    
    /** 
     * Check if the socket has negotiated the right NPN and process it.
     *  
     * @param socket
     * @param status
     * @return OPEN if the socket doesn't have the right npn.
     *    CLOSE if processing is done. LONG to request read polling.
     */
    SocketState process(SocketWrapper<?> socket, SocketStatus status);
    
    /**
     * Initialize the npn handler.
     * 
     * @param ep
     * @param sslContext
     * @param adapter
     */
    public void init(final AbstractEndpoint ep, long sslContext, Adapter adapter);

    /** 
     * Called when a SSLSocket or SSLEngine are first used, to initialize 
     * NPN extension.
     * 
     * @param socket SSLEngine or SSLSocket
     */
    void onCreateEngine(Object socket);
}