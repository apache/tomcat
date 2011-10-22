/*
 */
package org.apache.tomcat.lite.http;

import org.apache.tomcat.lite.io.SocketConnector;
import org.apache.tomcat.lite.io.SslProvider;
import org.apache.tomcat.lite.io.jsse.JsseSslProvider;

/**
 * Entry point for http client code.
 *
 * ( initial version after removing 'integration', will add settings,
 * defaults, helpers )
 */
public class HttpClient {
    static SslProvider sslConC = new JsseSslProvider();

    public synchronized static HttpConnector newClient() {
        return new HttpConnector(new SocketConnector()).withSsl(sslConC);
    }

}
