/*
 */
package org.apache.tomcat.lite.http;

import org.apache.tomcat.lite.io.SocketConnector;

public class DefaultHttpConnector {

    public synchronized static HttpConnector getNew() {
        return new HttpConnector(new SocketConnector());
    }

    public synchronized static HttpConnector get() {
        if (DefaultHttpConnector.socketConnector == null) {
            socketConnector =
                new SocketConnector();
        }
        return new HttpConnector(socketConnector);
    }

    private static SocketConnector socketConnector;

}
