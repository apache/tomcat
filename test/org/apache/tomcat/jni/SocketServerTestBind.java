/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tomcat.jni;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import junit.framework.TestCase;

import java.io.OutputStream;
import java.io.InputStream;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.*;

/**
 * A basic test suite that tests Socket Server feature.
 * 
 * @author Jean-Frederic Clere
 * @version $Revision: 466585 $, $Date: 2006-10-22 00:16:34 +0200 (Sun, 22 Oct 2006) $ 
 * @see org.apache.tomcat.jni
 */
public class SocketServerTestBind extends TestCase {

    private long serverSock = 0;
    private int port=6666;
    private String host=null;

    public static long serverPool = 0;

    public void testSocketServerTestBind() throws Exception {

        System.out.println("Starting: testSocketServerTestBind");
        /* Load APR library */
        Library.initialize(null);

        /* Create the server socket and listen on it */
        serverPool = Pool.create(0);
        long inetAddress = Address.info(host, Socket.APR_UNSPEC,
                                        port, 0, serverPool);
        serverSock = Socket.create(Address.getInfo(inetAddress).family,
                                   Socket.SOCK_STREAM,
                                   Socket.APR_PROTO_TCP, serverPool);
        int rc = Socket.bind(serverSock, inetAddress);
        if (rc != 0) {
            throw(new Exception("Can't bind: " + Error.strerror(rc)));
        }
        Socket.listen(serverSock, 5);

        /* Start the client that connects to the server */
        Client client = new Client();
        client.start(); 
        java.lang.Thread.sleep(100);
   
        boolean running = true;
        while (running) { 
            /* Accept it */
            long clientSock = Socket.accept(serverSock);
            Socket.timeoutSet(clientSock, 10000);
            byte [] buf = new byte[1];
            while (Socket.recv(clientSock, buf, 0, 1) == 1) {
                if (buf[0] == 'A') {
                    buf[0] = 'Z';
                    Socket.send(clientSock, buf, 0, 1);
                }
            }
            Socket.close(clientSock);
            if (buf[0] != 'Z')
                running = false;
        }
        client.join();
        Library.terminate();
        System.out.println("Done: testSocketServerTestBind");
    }

    /* small client that connects and sends one byte */
    private class Client extends java.lang.Thread {
        public void run() {
            try {
               Enumeration nets = NetworkInterface.getNetworkInterfaces();
               while (nets.hasMoreElements()) {
                   NetworkInterface net = (NetworkInterface) nets.nextElement();
        
                   Enumeration addrs = net.getInetAddresses();

                   while (addrs.hasMoreElements()) {
                       InetAddress ia = (InetAddress)addrs.nextElement();
                       System.out.println("Trying: " + ia.getHostAddress());
                       java.net.Socket sock = new java.net.Socket(ia, port);
                       sock.setSoTimeout(10000);
                       OutputStream ou = sock.getOutputStream();
                       InputStream in =  sock.getInputStream();
                       ou.write('A');
                       ou.flush();
                       int rep = in.read();
                       sock.close();
                       if (rep != 'Z')
                            throw new Exception("Read wrong data");
                  }
               }
            } catch(Exception ex ) {
                ex.printStackTrace();
            }

            /* Now use localhost to write 'E' */
            try {
               java.net.Socket sock = new java.net.Socket("localhost", port);
               OutputStream ou = sock.getOutputStream();
               ou.write('E');
               ou.flush();
               sock.close();
            } catch(Exception ex ) {
                ex.printStackTrace();
            }
        }
    }
}
