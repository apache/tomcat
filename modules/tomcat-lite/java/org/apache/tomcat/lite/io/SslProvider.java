/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;

public interface SslProvider {

    public static final String ATT_SSL_CERT = "SslCert";
    public static final String ATT_SSL_CIPHER = "SslCipher";
    public static final String ATT_SSL_KEY_SIZE = "SslKeySize";
    public static final String ATT_SSL_SESSION_ID = "SslSessionId";

    /**
     * Wrap channel with SSL.
     *
     * The result will start a handshake
     */
    public IOChannel channel(IOChannel net, String host, int port)
        throws IOException;

    public IOChannel serverChannel(IOChannel net) throws IOException;

}
