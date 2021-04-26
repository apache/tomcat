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


package org.apache.catalina.ha.backend;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/*
 * Listener to provider informations to mod_heartbeat.c
 * *msg_format = "v=%u&ready=%u&busy=%u"; (message to send).
 * send the multicast message using the format...
 * what about the bind(IP. port) only IP makes sense (for the moment).
 * BTW:v  = version :-)
 */
public class HeartbeatListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(HeartbeatListener.class);
    private static final StringManager sm = StringManager.getManager(HeartbeatListener.class);

    /* To allow to select the connector */
    protected int port = 8009;
    protected String host = null;

    /**
     * @return the host corresponding to the connector
     * we want to proxy.
     */
    public String getHost() {
        return this.host;
    }

    /**
     * Set the host corresponding to the connector.
     *
     * @param host the hostname or ip string.
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return the port of the connector we want to proxy.
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Set the port corresponding to the connector.
     *
     * @param port default 8009 the ajp one.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /* for multicasting stuff */
    protected String ip = "224.0.1.105"; /* Multicast IP */
    protected int multiport = 23364;     /* Multicast Port */
    protected int ttl = 16;

    /* corresponding setters and getters */

    /**
     * @return the Multicast IP we are using for Multicast
     */
    public String getGroup() { return ip; }

    /**
     * Set the Multicast IP to use for Multicast
     *
     * @param group the multi address to use.
     */
    public void setGroup(String group) { this.ip = group; }

    /**
     * @return the Multicast Port we are using for Multicast.
     */
    public int getMultiport() { return multiport; }

    /**
     * Set the Port to use for Multicast
     *
     * @param port the port to use.
     */
    public void setMultiport(int port) { this.multiport=port; }

    /**
     * @return the TTL for Multicast packets.
     */
    public int getTtl() { return ttl; }

    /**
     * Set the TTL for Multicast packets.
     *
     * @param ttl value for TTL.
     */
    public void setTtl(int ttl) { this.ttl=ttl; }

    /**
     * Proxy list, format "address:port,address:port".
     */
    protected String proxyList = null;

    /**
     * @return the list of proxies that send us requests.
     */
    public String getProxyList() { return proxyList; }

    /**
     * Set the list of Proxies that send is requests, when not empty it toggles
     * the multi to off. A SetHandler heartbeat must be existing in httpd.conf.
     *
     * @param proxyList the list of proxy, format "address:port,address:port".
     */
    public void setProxyList(String proxyList) { this.proxyList = proxyList; }

    /**
     * URL prefix.
     */
    protected String proxyURL = "/HeartbeatListener";

    /**
     * @return the URL specified in &lt;Location/&gt; for the SetHandler heartbeat.
     */
    public String getProxyURL() { return proxyURL; }

    /**
     * Set the URL of receiver in httpd. That is the location used in
     * <pre>
     * &lt;Location "/HeartbeatListener"&gt;
     *    SetHandler heartbeat
     * &lt;/Location&gt;
     * </pre>
     * All proxies MUST use the same location.
     *
     * @param proxyURL a String with the URL starting with /
     */
    public void setProxyURLString(String proxyURL) { this.proxyURL = proxyURL; }

    private CollectedInfo coll = null;

    private Sender sender = null;

    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        if (Lifecycle.PERIODIC_EVENT.equals(event.getType())) {
            if (sender == null) {
                if (proxyList == null)
                    sender = new MultiCastSender();
                else
                    sender = new TcpSender();
            }

            /* Read busy and ready */
            if (coll == null) {
                try {
                    coll = new CollectedInfo(host, port);
                    this.port = coll.port;
                    this.host = coll.host;
                } catch (Exception ex) {
                    log.error(sm.getString("heartbeatListener.errorCollectingInfo"), ex);
                    coll = null;
                    return;
                }
            }

            /* Start or restart sender */
            try {
                sender.init(this);
            } catch (Exception ex) {
                log.error(sm.getString("heartbeatListener.senderInitError"), ex);
                sender = null;
                return;
            }

            /* refresh the connector information and send it */
            try {
                coll.refresh();
            } catch (Exception ex) {
                log.error(sm.getString("heartbeatListener.refreshError"), ex);
                coll = null;
                return;
            }
            String output = "v=1&ready=" + coll.ready + "&busy=" + coll.busy +
                    "&port=" + port;
            try {
                sender.send(output);
            } catch (Exception ex) {
                log.error(sm.getString("heartbeatListener.sendError"), ex);
            }
        }
    }

}
