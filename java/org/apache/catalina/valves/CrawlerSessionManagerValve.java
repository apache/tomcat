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
package org.apache.catalina.valves;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Web crawlers can trigger the creation of many thousands of sessions as they
 * crawl a site which may result in significant memory consumption. This Valve
 * ensures that crawlers are associated with a single session - just like normal
 * users - regardless of whether or not they provide a session token with their
 * requests.
 */
public class CrawlerSessionManagerValve extends ValveBase {

    private static final Log log =
        LogFactory.getLog(CrawlerSessionManagerValve.class);

    private Map<String,SessionInfo> uaIpSessionInfo =
        new ConcurrentHashMap<String, SessionInfo>();

    private String crawlerUserAgents =
        ".*GoogleBot.*|.*bingbot.*|.*Yahoo! Slurp.*";
    private Pattern uaPattern = null;
    private int sessionInactiveInterval = 60;


    /**
     * Specify the regular expression (using {@link Pattern}) that will be used
     * to identify crawlers based in the User-Agent header provided. The default
     * is ".*GoogleBot.*|.*bingbot.*|.*Yahoo! Slurp.*"
     *  
     * @param crawlerUserAgents The regular expression using {@link Pattern}
     */
    public void setCrawlerUserAgents(String crawlerUserAgents) {
        this.crawlerUserAgents = crawlerUserAgents;
        if (crawlerUserAgents == null || crawlerUserAgents.length() == 0) {
            uaPattern = null;
        } else {
            uaPattern = Pattern.compile(crawlerUserAgents);
        }
    }

    /**
     * @see #setCrawlerUserAgents(String)
     * @return  The current regular expression being used to match user agents. 
     */
    public String getCrawlerUserAgents() {
        return crawlerUserAgents;
    }


    /**
     * Specify the session timeout (in seconds) for a crawler's session. This is
     * typically lower than that for a user session. The default is 60 seconds.
     *  
     * @param sessionInactiveInterval   The new timeout for crawler sessions
     */
    public void setSessionInactiveInterval(int sessionInactiveInterval) {
        this.sessionInactiveInterval = sessionInactiveInterval;
    }

    /**
     * @see #setSessionInactiveInterval(int)
     * @return  The current timeout in seconds
     */
    public int getSessionInactiveInterval() {
        return sessionInactiveInterval;
    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        
        uaPattern = Pattern.compile(crawlerUserAgents);
    }


    @Override
    public void invoke(Request request, Response response) throws IOException,
            ServletException {

        boolean isBot = false;
        SessionInfo sessionInfo = null;
        String clientIp = null;

        if (log.isDebugEnabled()) {
            log.debug(request.hashCode() + ": ClientIp=" +
                    request.getRemoteAddr() + ", RequestedSessionId=" +
                    request.getRequestedSessionId());
        }

        // If the incoming request has a session ID, no action is required
        if (request.getRequestedSessionId() == null) {

            // Is this a crawler - cheack the UA headers
            Enumeration<String> uaHeaders = request.getHeaders("user-agent");
            String uaHeader = null;
            if (uaHeaders.hasMoreElements()) {
                uaHeader = uaHeaders.nextElement();
            }
            
            // If more than one UA header - assume not a bot
            if (uaHeader != null && !uaHeaders.hasMoreElements()) {

                if (log.isDebugEnabled()) {
                    log.debug(request.hashCode() + ": UserAgent=" + uaHeader);
                }
                
                if (uaPattern.matcher(uaHeader).matches()) {
                    isBot = true;
                    
                    if (log.isDebugEnabled()) {
                        log.debug(request.hashCode() +
                                ": Bot found. UserAgent=" + uaHeader);
                    }
                }
            }
            
            // If this is a bot, is the session ID known?
            if (isBot) {
                clientIp = request.getRemoteAddr();
                sessionInfo = uaIpSessionInfo.get(clientIp);
                if (sessionInfo != null) {
                    request.setRequestedSessionId(sessionInfo.getSessionId());
                    if (log.isDebugEnabled()) {
                        log.debug(request.hashCode() +
                                ": SessionID=" + sessionInfo.getSessionId());
                    }
                }
            }
        }

        getNext().invoke(request, response);
        
        if (isBot) {
            if (sessionInfo == null) {
                // Has bot just created a session, if so make a note of it
                HttpSession s = request.getSession(false);
                if (s != null) {
                    uaIpSessionInfo.put(clientIp, new SessionInfo(s.getId()));
                    s.setMaxInactiveInterval(sessionInactiveInterval);

                    if (log.isDebugEnabled()) {
                        log.debug(request.hashCode() +
                                ": New bot session. SessionID=" + s.getId());
                    }
                }
            } else {
                sessionInfo.access();

                if (log.isDebugEnabled()) {
                    log.debug(request.hashCode() +
                            ": Bot session accessed. SessionID=" +
                            sessionInfo.getSessionId());
                }
            }
        }
    }


    @Override
    public void backgroundProcess() {
        super.backgroundProcess();
        
        long expireTime = System.currentTimeMillis() -
                (sessionInactiveInterval + 60) * 1000;

        Iterator<Entry<String,SessionInfo>> iter =
            uaIpSessionInfo.entrySet().iterator();

        // Remove any sessions in the cache that have expired. 
        while (iter.hasNext()) {
            Entry<String,SessionInfo> entry = iter.next();
            if (entry.getValue().getLastAccessed() < expireTime) {
                iter.remove();
            }
        }
    }


    private static final class SessionInfo {
        private final String sessionId;
        private volatile long lastAccessed;
        
        public SessionInfo(String sessionId) {
            this.sessionId = sessionId;
            this.lastAccessed = System.currentTimeMillis();
        }

        public String getSessionId() {
            return sessionId;
        }

        public long getLastAccessed() {
            return lastAccessed;
        }

        public void access() {
            lastAccessed = System.currentTimeMillis();
        }
    }
}
