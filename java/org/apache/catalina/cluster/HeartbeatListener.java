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


package org.apache.catalina.cluster;

import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Engine;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import org.apache.catalina.connector.Connector;

import java.net.MulticastSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.io.UnsupportedEncodingException;

import org.apache.tomcat.util.modeler.Registry;

/*
 * Listener to provider informations to mod_heartbeat.c
 * *msg_format = "v=%u&ready=%u&busy=%u"; (message to send).
 * send the muticast merssage using the format...
 * what about the bind(IP. port) only IP makes sense (for the moment).
 * BTW:v  = version :-)
 */
public class HeartbeatListener
    implements LifecycleListener, ContainerListener {

    public static Log log = LogFactory.getLog(HeartbeatListener.class);

    /* To allow to select the connector */
    int port = 0;
    String host = null;
    public void setHost(String host) { this.host = host; }
    public void setPort(int port) { this.port = port; }

    /* for multicasting stuff */
    MulticastSocket s = null;
    InetAddress group = null;
    String ip = "224.0.1.105"; /* Multicast IP */
    int multiport = 23364;     /* Multicast Port */

    public void setGroup(String ip) { this.ip = ip; }
    public void setMultiport(int multiport) { this.multiport = multiport; }

    private CollectedInfo coll = null;

    public void containerEvent(ContainerEvent event) {
    }

    public void lifecycleEvent(LifecycleEvent event) {
        Object source = event.getLifecycle();
        if (Lifecycle.PERIODIC_EVENT.equals(event.getType())) {
            if (s == null) {
                try {
                    group = InetAddress.getByName(ip);
                    s = new MulticastSocket(port);
                    s.setTimeToLive(16);
                    s.joinGroup(group);
                } catch (Exception ex) {
                    log.error("Unable to use multicast: " + ex);
                    s = null;
                    return;
                } 
            }
// * *msg_format = "v=%u&ready=%u&busy=%u"; (message to send).
// v = version (1)
// ready & ready are read from the scoreboard in httpd.
// Endpoint ( getCurrentThreadsBusy ) ( getMaxThreads )
            if (coll == null) {
                try {
                    coll = new CollectedInfo(host, port);
                } catch (Exception ex) {
                    log.error("Unable to initialize info collection: " + ex);
                    coll = null;
                    return;
                } 
            }
            try {
                coll.refresh();
            } catch (Exception ex) {
                log.error("Unable to collect load information: " + ex);
                coll = null;
                return;
            }
            String output = new String();
            output = "v=1&ready=" + coll.ready + "&busy=" + coll.busy;
            byte[] buf;
            try {
                buf = output.getBytes("US-ASCII");
            } catch (UnsupportedEncodingException ex) {
                buf = output.getBytes();
            }
            DatagramPacket data = new DatagramPacket(buf, buf.length, group, multiport);
            try {
                s.send(data);
            } catch (Exception ex) {
                log.error("Unable to send colllected load information: " + ex);
                System.out.println(ex);
                s.close();
                s = null;
            }
        }
    }

}
