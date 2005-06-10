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

public class BIOSSLServer {

    public static String serverAddr = null;
    public static int serverPort    = 0;
    public static int serverNmax    = 0;
    public static long serverPool   = 0;
    public static long serverCtx    = 0;
    public static String serverCert = null;
    public static String serverKey  = null;
    public static String serverCiphers  = null;
    public static String serverPassword = null;

    private static Object threadLock = new Object();

    static {

        try {
            InputStream is = BIOSSLServer.class.getResourceAsStream
                ("/org/apache/tomcat/jni/SSL.properties");
            Properties props = new Properties();
            props.load(is);
            is.close();
            serverAddr = props.getProperty("server.ip", "127.0.0.1");
            serverPort = Integer.decode(props.getProperty("server.port", "4443")).intValue();
            serverNmax = Integer.decode(props.getProperty("server.max", "1")).intValue();
            serverCert = props.getProperty("server.cert", "server.pem");
            serverKey  = props.getProperty("server.key", null);
            serverCiphers  = props.getProperty("server.ciphers", "ALL");
            serverPassword = props.getProperty("server.password", null);
        }
        catch (Throwable t) {
            ; // Nothing
        }
    }

    private class CallBack implements BIOCallback {
        long clientSock = 0;
        public int write(byte [] buf) {
            return(Socket.send(clientSock, buf, 0, buf.length)); 
        }
        public int read(byte [] buf) { 
            return(Socket.recv(clientSock, buf, 0, buf.length));
        }
        public int puts(String data) {
            System.out.println("CallBack.puts");
            return -1;
        }
        public String gets(int len) {
            System.out.println("CallBack.gets");
            return "";
        }
        public void setsock(long sock) {
            clientSock = sock;
        }
    }

    public BIOSSLServer()
    {
        int i;
        serverPool = Pool.create(0);
        try {
            /* Create SSL Context, one for each Virtual Host */
            serverCtx = SSLContext.make(serverPool, SSL.SSL_PROTOCOL_SSLV2 | SSL.SSL_PROTOCOL_SSLV3, SSL.SSL_MODE_SERVER);
            // serverCtx = SSLContext.make(serverPool, SSL.SSL_PROTOCOL_TLSV1, SSL.SSL_MODE_SERVER);
            /* List the ciphers that the client is permitted to negotiate. */
            SSLContext.setCipherSuite(serverCtx, serverCiphers);
            /* Load Server key and certificate */
            SSLContext.setCertificate(serverCtx, serverCert, serverKey, serverPassword, SSL.SSL_AIDX_RSA);
            SSLContext.setVerify(serverCtx, SSL.SSL_CVERIFY_NONE, 0);

            /*
            CallBack SSLCallBack = new CallBack();
            long callback = SSL.newBIO(serverPool, SSLCallBack);
            SSLContext.setBIO(serverCtx, callback, 1);
            SSLContext.setBIO(serverCtx, callback, 0);
            long serverSSL = SSLBIO.make(serverCtx, callback, callback);
             */

            long serverSock = Socket.create(Socket.APR_INET, Socket.SOCK_STREAM,
                                            Socket.APR_PROTO_TCP, serverPool);
            long inetAddress = Address.info(BIOSSLServer.serverAddr, Socket.APR_INET, BIOSSLServer.serverPort, 0, serverPool);
            int rc = Socket.bind(serverSock, inetAddress);
            if (rc != 0) {
                throw(new Exception("Can't bind: " + Error.strerror(rc)));
            }
            Socket.listen(serverSock, 5);
            long clientSock = Socket.accept(serverSock, serverPool);
            long sa = Address.get(Socket.APR_REMOTE, clientSock);
            Sockaddr raddr = new Sockaddr();
            if (Address.fill(raddr, sa)) {
                System.out.println("Remote Host: " + Address.getnameinfo(sa, 0));
                System.out.println("Remote IP: " + Address.getip(sa) +
                                   ":" + raddr.port);
            }
            // SSLCallBack.setsock(clientSock);
            int retcode = SSLSocket.accept(serverCtx, clientSock, serverPool);
            if (retcode<=0) {
                throw(new Exception("Can't SSL accept: " + SSLBIO.geterror(serverSSL, retcode)));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String [] args) {
        try {
            Library.initialize(null);
            SSL.initialize(null);

            BIOSSLServer server = new BIOSSLServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
 }
