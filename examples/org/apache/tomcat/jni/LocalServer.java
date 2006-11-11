package org.apache.tomcat.jni;

import java.io.InputStream;
import java.util.Properties;

/** Local Socket server example
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

public class LocalServer {

    public static String serverAddr = null;
    public static int serverNmax    = 0;
    public static int serverNrun    = 0;
    public static long serverPool   = 0;

    private static Acceptor serverAcceptor = null;

    private static Object threadLock = new Object();

    static {

        try {
            InputStream is = LocalServer.class.getResourceAsStream
                ("/org/apache/tomcat/jni/Local.properties");
            Properties props = new Properties();
            props.load(is);
            is.close();
            serverAddr = props.getProperty("local.path", null);
            serverNmax = Integer.decode(props.getProperty("local.max", "0")).intValue();
        }
        catch (Throwable t) {
            ; // Nothing
        }
    }

    public LocalServer()
    {
        serverPool = Pool.create(0);
        try {
            serverAcceptor = new Acceptor();
            serverAcceptor.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    public static void incThreads() {
        synchronized(threadLock) {
            serverNrun++;
        }
    }

    public static void decThreads() {
        synchronized(threadLock) {
            serverNrun--;
        }
    }

    /* Acceptor thread. Listens for new connections */
    private class Acceptor extends java.lang.Thread {
        private long serverSock = 0;
        private long inetAddress = 0;
        private long pool = 0;
        public Acceptor() throws Exception {
            try {

                pool = Pool.create(LocalServer.serverPool);
                System.out.println("Accepting: " +  LocalServer.serverAddr);
                serverSock = Local.create(LocalServer.serverAddr, pool);
                int rc = Local.bind(serverSock, inetAddress);
                if (rc != 0) {
                  throw(new Exception("Can't create Acceptor: bind: " + Error.strerror(rc)));
                }
                Local.listen(serverSock, LocalServer.serverNmax);
            }
            catch( Exception ex ) {
                ex.printStackTrace();
                throw(new Exception("Can't create Acceptor"));
            }
        }

        public void run() {
            int i = 0;
            try {
                while (true) {
                    long clientSock = Local.accept(serverSock);
                    System.out.println("Accepted id: " +  i);

                    Socket.timeoutSet(clientSock, 10000000);
                    Worker worker = new Worker(clientSock, i++,
                                               this.getClass().getName());
                    LocalServer.incThreads();
                    worker.start();
                }
            }
            catch( Exception ex ) {
                ex.printStackTrace();
            }
        }
    }

    private class Worker extends java.lang.Thread {
        private int workerId = 0;
        private long clientSock = 0;
        private byte [] wellcomeMsg = null;

        public Worker(long clientSocket, int workerId, String from) {
            this.clientSock = clientSocket;
            this.workerId = workerId;
            wellcomeMsg = ("LocalServer server id: " + this.workerId + " from " +
                           from).getBytes();
        }

        public void run() {
            boolean doClose = false;
            try {
                Socket.send(clientSock, wellcomeMsg, 0, wellcomeMsg.length);
                while (!doClose) {
                    /* Do a blocking read byte at a time */
                    byte [] buf = new byte[1];
                    byte [] msg = new byte[256];
                    int p = 0;
                    while (Socket.recv(clientSock, buf, 0, 1) == 1) {
                        if (buf[0] == '\n')
                            break;
                        else if (buf[0] == '!') {
                            doClose = true;
                            break;
                        }
                        if (p > 250)
                           break;
                        msg[p++] = buf[0];
                    }
                    if (doClose) {
                        try {
                            byte [] snd = ("Bye from worker: " + workerId).getBytes();
                            Socket.send(clientSock, snd, 0, snd.length);
                        } catch(Exception e) { }

                        Socket.close(clientSock);
                    }
                    else
                        Socket.send(clientSock, msg, 0, p);                
                }
            } catch (Exception e) {
                Socket.destroy(clientSock);
                e.printStackTrace();
            }
            LocalServer.decThreads();
            System.out.println("Worker: " +  workerId + " finished");
        }
    }


    public static void main(String [] args) {
        try {
            Library.initialize(null);

            LocalServer server = new LocalServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
 }
