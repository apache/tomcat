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
package org.apache.tomcat.bayeux;

import java.io.IOException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.comet.CometProcessor;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.cometd.bayeux.Bayeux;

/**
 *
 * @author Filip Hanik
 * @author Guy Molinari
 * @version 1.0
 */
public class BayeuxServlet implements CometProcessor {

    /**
     * Attribute to hold the TomcatBayeux object in the servlet context
     */
    public static final String TOMCAT_BAYEUX_ATTR = Bayeux.DOJOX_COMETD_BAYEUX;

    /**
     * Logger object
     */
    private static final Log log = LogFactory.getLog(BayeuxServlet.class);

    /**
     * Servlet config - for future use
     */
    protected ServletConfig servletConfig;

    /**
     * Reference to the global TomcatBayeux object
     */
    protected TomcatBayeux tb;

    /**
     * Upon servlet destruction, the servlet will clean up the
     * TomcatBayeux object and terminate any outstanding events.
     */
    public void destroy() {
        servletConfig = null;
        //to do, close all outstanding comet events
        //tb.destroy();
        tb = null;//TO DO, close everything down

    }

    /**
     * Returns the preconfigured connection timeout.
     * If no timeout has been configured as a servlet init parameter named <code>timeout</code>
     * then the default of 2min will be used.
     * @return int - the timeout for a connection in milliseconds
     */
    protected int getTimeout() {
        String timeoutS = servletConfig.getInitParameter("timeout");
        int timeout = 120*1000; //2 min
        try {
            timeout = Integer.parseInt(timeoutS);
        }catch (NumberFormatException nfe) {
            //ignore, we have a default value
        }
        return timeout;
    }

    protected int getReconnectInterval() {
        String rs = servletConfig.getInitParameter("reconnectInterval");
        int rct = 1000; //1 seconds
        try {
            rct = Integer.parseInt(rs);
        }catch (NumberFormatException nfe) {
            //ignore, we have a default value
        }
        return rct;
    }


    public void event(CometEvent cometEvent) throws IOException, ServletException {
        CometEvent.EventType type = cometEvent.getEventType();
        if (log.isDebugEnabled()) {
            log.debug("["+Thread.currentThread().getName()+"] Received Comet Event type="+type+" subtype:"+cometEvent.getEventSubType());
        }
        synchronized (cometEvent) {
            if (type==CometEvent.EventType.BEGIN) {
                //begin event, set the timeout
                cometEvent.setTimeout(getTimeout());
                //checkBayeux(cometEvent); - READ event should always come
            } else if (type==CometEvent.EventType.READ) {
                checkBayeux(cometEvent);
            } else if (type==CometEvent.EventType.ERROR) {
                tb.remove(cometEvent);
                cometEvent.close();
            } else if (type==CometEvent.EventType.END) {
                tb.remove(cometEvent);
                cometEvent.close();
            }//end if

        }//synchronized
    }//event

    /**
     *
     * @param cometEvent CometEvent
     * @return boolean - true if we comet event stays open
     * @throws IOException
     * @throws UnsupportedOperationException
     */
    protected void checkBayeux(CometEvent cometEvent) throws IOException, UnsupportedOperationException {
        //we actually have data.
        //data can be text/json or
        if (Bayeux.JSON_CONTENT_TYPE.equals(cometEvent.getHttpServletRequest().getContentType())) {
            //read and decode the bytes according to content length
            log.warn("["+Thread.currentThread().getName()+"] JSON encoding not supported, will throw an exception and abort the request.");
            int contentlength = cometEvent.getHttpServletRequest().getContentLength();
            throw new UnsupportedOperationException("Decoding "+Bayeux.JSON_CONTENT_TYPE+" not yet implemented.");
        } else { //GET method or application/x-www-form-urlencoded
            String message = cometEvent.getHttpServletRequest().getParameter(Bayeux.MESSAGE_PARAMETER);
            if (log.isTraceEnabled()) {
                log.trace("["+Thread.currentThread().getName()+"] Received JSON message:"+message);
            }
            try {
                int action = handleBayeux(message, cometEvent);
                if (log.isDebugEnabled()) {
                    log.debug("["+Thread.currentThread().getName()+"] Bayeux handling complete, action result="+action);
                }
                if (action<=0) {
                    cometEvent.close();
                }
            }catch (Exception x) {
                x.printStackTrace();
                tb.remove(cometEvent);
                log.error(x);
                cometEvent.close();
            }
        }
    }

    protected int handleBayeux(String message, CometEvent event) throws IOException, ServletException {
        int result = 0;
        if (message==null || message.length()==0) return result;
        try {
            BayeuxRequest request = null;
            //a message can be an array of messages
            JSONArray jsArray = new JSONArray(message);
            for (int i = 0; i < jsArray.length(); i++) {
                JSONObject msg = jsArray.getJSONObject(i);

                if (log.isDebugEnabled()) {
                    log.debug("["+Thread.currentThread().getName()+"] Processing bayeux message:"+msg);
                }
                request = RequestFactory.getRequest(tb,event,msg);
                if (log.isDebugEnabled()) {
                    log.debug("["+Thread.currentThread().getName()+"] Processing bayeux message using request:"+request);
                }
                result = request.process(result);
                if (log.isDebugEnabled()) {
                    log.debug("["+Thread.currentThread().getName()+"] Processing bayeux message result:"+result);
                }
            }
            if (result>0 && request!=null) {
                event.getHttpServletRequest().setAttribute(BayeuxRequest.LAST_REQ_ATTR, request);
                ClientImpl ci = (ClientImpl)tb.getClient(((RequestBase)request).getClientId());
                ci.addCometEvent(event);
                if (log.isDebugEnabled()) {
                    log.debug("["+Thread.currentThread().getName()+"] Done bayeux message added to request attribute");
                }
            } else if (result == 0 && request!=null) {
                RequestBase.deliver(event,(ClientImpl)tb.getClient(((RequestBase)request).getClientId()));
                if (log.isDebugEnabled()) {
                    log.debug("["+Thread.currentThread().getName()+"] Done bayeux message, delivered to client");
                }
            }

        }catch (JSONException x) {
            log.error(x);//to do impl error handling
            result = -1;
        }catch (BayeuxException x) {
            log.error(x); //to do impl error handling
            result = -1;
        }
        return result;
    }

    public ServletConfig getServletConfig() {
        return servletConfig;
    }

    public String getServletInfo() {
        return "Tomcat/BayeuxServlet/1.0";
    }

    public void init(ServletConfig servletConfig) throws ServletException {

        this.servletConfig = servletConfig;
        ServletContext ctx = servletConfig.getServletContext();
        if (ctx.getAttribute(TOMCAT_BAYEUX_ATTR)==null)
            ctx.setAttribute(TOMCAT_BAYEUX_ATTR,new TomcatBayeux());
        this.tb = (TomcatBayeux)ctx.getAttribute(TOMCAT_BAYEUX_ATTR);
        tb.setReconnectInterval(getReconnectInterval());
    }

    public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
        if (servletResponse instanceof HttpServletResponse) {
            ( (HttpServletResponse) servletResponse).sendError(500, "Misconfigured Tomcat server, must be configured to support Comet operations.");
        } else {
            throw new ServletException("Misconfigured Tomcat server, must be configured to support Comet operations for the Bayeux protocol.");
        }
    }
}
