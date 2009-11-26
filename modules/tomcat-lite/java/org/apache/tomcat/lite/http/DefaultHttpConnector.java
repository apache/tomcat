/*
 */
package org.apache.tomcat.lite.http;

import org.apache.tomcat.lite.io.SocketConnector;

public class DefaultHttpConnector {

    public synchronized static HttpConnector getNew() {
        return new HttpConnector(new SocketConnector());
    }

    public synchronized static HttpConnector get() {
        if (DefaultHttpConnector.defaultHttpConnector == null) {
            DefaultHttpConnector.defaultHttpConnector = 
                new HttpConnector(new SocketConnector());
        }
        return DefaultHttpConnector.defaultHttpConnector;
    }
    
    private static HttpConnector defaultHttpConnector;

}
