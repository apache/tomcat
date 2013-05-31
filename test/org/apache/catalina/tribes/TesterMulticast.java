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
package org.apache.catalina.tribes;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

/**
 * A simple multicast test that replicates the core elements of Tomcat's
 * multicast membership. If this works then multicast membership should work.
 * Useful notes for various operating systems follow.<p>
 * OSX
 * <ul>
 * <li>The firewall blocks multicast between processes on the local machine so
 *     you will need to disable the OSX firewall before the test below will
 *     work.</li>
 * </ul>
 * Windows Server 2008
 * <ul>
 * <li>This works out of the box</li>
 * </ul>
 */
public class TesterMulticast {

    private static final String ADDRESS = "228.0.0.4";
    private static final int PORT = 56565;
    private static final InetAddress INET_ADDRESS;

    static {
        InetAddress result = null;
        try {
             result = InetAddress.getByName(ADDRESS);
        } catch (UnknownHostException e) {
            // deal with later
        }
        INET_ADDRESS = result;
    }


    public static void main(String[] args) throws Exception {
        // Start Rx Thread
        Rx rx = new Rx();
        Thread rxThread = new Thread(rx);
        rxThread.setDaemon(true);
        rxThread.start();

        // Start Tx Thread
        Tx tx = new Tx();
        Thread txThread = new Thread(tx);
        txThread.setDaemon(true);
        txThread.start();


        Thread.sleep(10000);

        tx.stop();
        rx.stop();
    }

    private static class Rx implements Runnable {

        private volatile boolean run = true;

        @Override
        public void run() {
            try (MulticastSocket s = new MulticastSocket(PORT)) {
                s.setLoopbackMode(false);
                s.joinGroup(INET_ADDRESS);
                DatagramPacket p = new DatagramPacket(new byte[4], 4);
                p.setAddress(INET_ADDRESS);
                p.setPort(PORT);
                while (run) {
                    s.receive(p);
                    String d = new String (p.getData());
                    System.out.println("Rx: " + d);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void stop() {
            run = false;
        }
    }

    private static class Tx implements Runnable {

        private volatile boolean run = true;

        @Override
        public void run() {
            try (MulticastSocket s = new MulticastSocket(PORT)) {
                s.setLoopbackMode(false);
                s.joinGroup(INET_ADDRESS);
                DatagramPacket p = new DatagramPacket(new byte[4], 4);
                p.setAddress(INET_ADDRESS);
                p.setPort(PORT);
                long counter = 0;
                String msg;
                while (run) {
                    msg = String.format("%04d", Long.valueOf(counter));
                    p.setData(msg.getBytes());
                    System.out.println("Tx: " + msg);
                    s.send(p);
                    counter++;
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void stop() {
            run = false;
        }
    }
}
