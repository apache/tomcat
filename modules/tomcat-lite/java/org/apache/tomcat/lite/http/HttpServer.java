/*
 */
package org.apache.tomcat.lite.http;

import org.apache.tomcat.lite.io.SocketConnector;
import org.apache.tomcat.lite.io.SslProvider;
import org.apache.tomcat.lite.io.jsse.JsseSslProvider;

/**
 * Main entry point for HTTP server code.
 *
 * ( initial draft - will replace statics, add helpers, etc )
 */
public class HttpServer {
    static SslProvider sslConC = new JsseSslProvider();

    public synchronized static HttpConnector newServer(int port) {
        return new HttpConnector(new SocketConnector()).
            withSsl(sslConC).setPort(port);
    }

    public synchronized static HttpConnector newSslServer(int port) {
        // DHE broken in harmony - will replace with a flag
        //      SslConnector.setEnabledCiphers(new String[] {
        //              "TLS_RSA_WITH_3DES_EDE_CBC_SHA"
        //      });
        // -cipher DES-CBC3-SHA

        SslProvider sslCon = new JsseSslProvider();

        return new HttpConnector(new SocketConnector()).
            withSsl(sslCon).setPort(port).setServerSsl(true);
    }

}
