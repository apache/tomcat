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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.StringTokenizer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * TCP-based sender for sending heartbeat messages to proxy servers.
 */
public class TcpSender implements Sender {

    /**
     * Constructs a new TcpSender.
     */
    public TcpSender() {
    }

    private static final Log log = LogFactory.getLog(TcpSender.class);
    private static final StringManager sm = StringManager.getManager(TcpSender.class);

    /**
     * Timeout, in milliseconds, when connecting to a proxy.
     */
    private static final int CONNECT_TIMEOUT = 5000;

    /**
     * Timeout, in milliseconds, when reading a response from a proxy.
     */
    private static final int READ_TIMEOUT = 5000;

    /**
     * The heartbeat listener configuration.
     */
    HeartbeatListener config = null;

    /**
     * Proxies.
     */
    protected Proxy[] proxies = null;


    /**
     * Active socket connections to proxies.
     */
    protected Socket[] connections = null;

    /**
     * Readers for active connections.
     */
    protected BufferedReader[] connectionReaders = null;

    /**
     * Writers for active connections.
     */
    protected BufferedWriter[] connectionWriters = null;

    /**
     * The proxy list that the current connections were established for.
     */
    protected String proxyList = null;


    @Override
    public void init(HeartbeatListener config) throws Exception {
        String newProxyList = config.getProxyList();
        if (connections != null && newProxyList != null && newProxyList.equals(proxyList)) {
            // The proxy list has not changed since the previous init, so the
            // existing connections can be kept. The init is called for every
            // heartbeat and closing the connections here would defeat the
            // keep-alive design.
            this.config = config;
            return;
        }
        // Close any existing connections from a previous init
        if (connections != null) {
            for (int i = 0; i < connections.length; i++) {
                close(i);
            }
        }
        this.config = config;
        StringTokenizer tok = new StringTokenizer(config.getProxyList(), ",");
        proxies = new Proxy[tok.countTokens()];
        int i = 0;
        while (tok.hasMoreTokens()) {
            String token = tok.nextToken().trim();
            int pos = token.indexOf(':');
            if (pos <= 0) {
                throw new Exception(sm.getString("tcpSender.invalidProxyList"));
            }
            proxies[i] = new Proxy();
            try {
                proxies[i].port = Integer.parseInt(token.substring(pos + 1));
                proxies[i].host = token.substring(0, pos);
                proxies[i].address = InetAddress.getByName(proxies[i].host);
            } catch (Exception e) {
                throw new Exception(sm.getString("tcpSender.invalidProxyList"));
            }
            i++;
        }
        connections = new Socket[proxies.length];
        connectionReaders = new BufferedReader[proxies.length];
        connectionWriters = new BufferedWriter[proxies.length];
        proxyList = newProxyList;

    }

    @Override
    public int send(String mess) throws Exception {
        if (connections == null) {
            log.error(sm.getString("tcpSender.notInitialized"));
            return -1;
        }
        String requestLine = "POST " + config.getProxyURL() + " HTTP/1.0";

        for (int i = 0; i < connections.length; i++) {
            if (connections[i] == null) {
                try {
                    // Pick up any DNS change since this proxy was last resolved
                    refreshProxyAddress(i);
                    connections[i] = new Socket();
                    // Never block the periodic event thread indefinitely
                    connections[i].setSoTimeout(READ_TIMEOUT);
                    if (config.getHost() != null) {
                        InetAddress addr = InetAddress.getByName(config.getHost());
                        InetSocketAddress addrs = new InetSocketAddress(addr, 0);
                        connections[i].setReuseAddress(true);
                        connections[i].bind(addrs);
                        addrs = new InetSocketAddress(proxies[i].address, proxies[i].port);
                        connections[i].connect(addrs, CONNECT_TIMEOUT);
                    } else {
                        connections[i].connect(new InetSocketAddress(proxies[i].address, proxies[i].port),
                                CONNECT_TIMEOUT);
                    }
                    connectionReaders[i] = new BufferedReader(new InputStreamReader(connections[i].getInputStream()));
                    connectionWriters[i] = new BufferedWriter(new OutputStreamWriter(connections[i].getOutputStream()));
                } catch (Exception e) {
                    log.error(sm.getString("tcpSender.connectionFailed"), e);
                    close(i);
                }
            }
            if (connections[i] == null) {
                continue; // try next proxy in the list
            }
            BufferedWriter writer = connectionWriters[i];
            try {
                writer.write(requestLine);
                writer.write("\r\n");
                writer.write("Content-Length: " + mess.length() + "\r\n");
                writer.write("User-Agent: HeartbeatListener/1.0\r\n");
                writer.write("Connection: Keep-Alive\r\n");
                writer.write("\r\n");
                writer.write(mess);
                writer.write("\r\n");
                writer.flush();
            } catch (Exception e) {
                log.error(sm.getString("tcpSender.sendFailed"), e);
                close(i);
            }
            if (connections[i] == null) {
                continue; // try next proxy in the list
            }

            /* Read httpd answer */
            try {
                String responseStatus = connectionReaders[i].readLine();
                if (responseStatus == null) {
                    log.error(sm.getString("tcpSender.responseError"));
                    close(i);
                    continue;
                }

                int firstSpace = responseStatus.indexOf(' ');
                int secondSpace = responseStatus.indexOf(' ', firstSpace + 1);
                if (firstSpace < 0 || secondSpace < 0 || secondSpace <= firstSpace + 1) {
                    log.error(sm.getString("tcpSender.responseError"));
                    close(i);
                    continue;
                }
                responseStatus = responseStatus.substring(firstSpace + 1, secondSpace);
                int status = 500;
                try {
                    status = Integer.parseInt(responseStatus);
                } catch (NumberFormatException e) {
                    // Ignore
                }
                if (status != 200) {
                    log.error(sm.getString("tcpSender.responseErrorCode", responseStatus));
                    close(i);
                    continue;
                }

                // read all the headers.
                String header = connectionReaders[i].readLine();
                int contentLength = 0;
                boolean contentLengthSeen = false;
                while (header != null && !header.isEmpty()) {
                    int colon = header.indexOf(':');
                    if (colon >= 0) {
                        String headerName = header.substring(0, colon).trim();
                        String headerValue = header.substring(colon + 1).trim();
                        if ("content-length".equalsIgnoreCase(headerName)) {
                            if (contentLengthSeen) {
                                log.error(sm.getString("tcpSender.duplicateContentLength"));
                                close(i);
                                // Clear any content length if one has been read.
                                contentLength = 0;
                                break;
                            } else {
                                contentLengthSeen = true;
                            }
                            try {
                                contentLength = Integer.parseInt(headerValue);
                            } catch (NumberFormatException e) {
                                log.error(sm.getString("tcpSender.invalidContentLength", headerValue));
                                close(i);
                                // Clear any content length if one has been read.
                                contentLength = 0;
                                break;
                            }
                        }
                    } else {
                        log.error(sm.getString("tcpSender.invalidHeaderLine", header));
                        close(i);
                        // Clear any content length if one has been read.
                        contentLength = 0;
                        break;
                    }
                    header = connectionReaders[i].readLine();
                }
                if (contentLength > 0) {
                    char[] buf = new char[512];
                    while (contentLength > 0) {
                        int thisTime = Math.min(contentLength, buf.length);
                        int n = connectionReaders[i].read(buf, 0, thisTime);
                        if (n <= 0) {
                            log.error(sm.getString("tcpSender.readError"));
                            close(i);
                            break;
                        } else {
                            contentLength -= n;
                        }
                    }
                }
            } catch (IOException e) {
                // Includes read timeouts. Close the connection so it is not
                // reused in a desynchronised state
                log.error(sm.getString("tcpSender.responseError"), e);
                close(i);
            }
        }

        return 0;
    }

    /**
     * Close connection.
     *
     * @param i The index of the connection that will be closed
     */
    protected void close(int i) {
        try {
            if (connectionReaders[i] != null) {
                connectionReaders[i].close();
            }
        } catch (IOException ignore) {
            // Ignore
        }
        connectionReaders[i] = null;
        try {
            if (connectionWriters[i] != null) {
                connectionWriters[i].close();
            }
        } catch (IOException ignore) {
            // Ignore
        }
        connectionWriters[i] = null;
        try {
            if (connections[i] != null) {
                connections[i].close();
            }
        } catch (IOException ignore) {
            // Ignore
        }
        connections[i] = null;
    }


    /**
     * Re-resolve the address of the given proxy. If resolution fails the previously resolved address is kept so a
     * transient DNS failure does not stop heartbeats to a proxy that is still reachable.
     *
     * @param i The index of the proxy
     */
    private void refreshProxyAddress(int i) {
        try {
            proxies[i].address = InetAddress.getByName(proxies[i].host);
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("tcpSender.resolveFailed", proxies[i].host), e);
            }
        }
    }
}
