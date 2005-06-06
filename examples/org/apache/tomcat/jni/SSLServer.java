package org.apache.tomcat.jni;

import java.util.Properties;

import java.io.*;
import java.net.*;
import java.lang.*;

/** SSL Server server example
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

public class SSLServer {

    public static String serverAddr = null;
    public static int serverPort    = 0;
    public static int serverNmax    = 0;
    public static long serverPool   = 0;
    public static long serverCtx    = 0;
    public static String serverCert = null;
    public static String serverKey  = null;
    public static String serverCiphers  = null;

    private static Object threadLock = new Object();

    static {

        try {
            InputStream is = SSLServer.class.getResourceAsStream
                ("/org/apache/tomcat/jni/SSL.properties");
            Properties props = new Properties();
            props.load(is);
            is.close();
            serverAddr = props.getProperty("server.ip", "127.0.0.1");
            serverPort = Integer.decode(props.getProperty("server.port", "4443")).intValue();
            serverNmax = Integer.decode(props.getProperty("server.max", "1")).intValue();
            serverCert = props.getProperty("server.cert", "server.pem");
            serverKey  = props.getProperty("server.key", null);
            serverCiphers = props.getProperty("server.ciphers", "ALL");
        }
        catch (Throwable t) {
            ; // Nothing
        }
    }

    public SSLServer()
    {
        int i;
        serverPool = Pool.create(0);
        try {
            /* Create SSL Context, one for each Virtual Host */
            serverCtx = SSLContext.make(serverPool, SSL.SSL_PROTOCOL_SSLV2 | SSL.SSL_PROTOCOL_SSLV3, SSL.SSL_MODE_SERVER);
            /* List the ciphers that the client is permitted to negotiate. */
            SSLContext.setCipherSuite(serverCtx, serverCiphers);
            /* Load Server key and certificate */
            SSLContext.setCertificate(serverCtx, serverCert, serverKey, null, SSL.SSL_AIDX_RSA);
            SSLContext.setVerifyDepth(serverCtx, 10);
            SSLContext.setVerifyClient(serverCtx, SSL.SSL_CVERIFY_REQUIRE);
            
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String [] args) {
        try {
            Library.initialize(null);
            SSL.initialize(null);

            SSLServer server = new SSLServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
 }
