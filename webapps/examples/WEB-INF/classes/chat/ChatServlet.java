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


package chat;


import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.apache.catalina.CometEvent;
import org.apache.catalina.CometProcessor;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Helper class to implement Comet functionality.
 */
public class ChatServlet
    extends HttpServlet implements CometProcessor {

    protected ArrayList<HttpServletResponse> connections = 
        new ArrayList<HttpServletResponse>();
    protected MessageSender messageSender = null;
    
    public void init() throws ServletException {
        messageSender = new MessageSender();
        Thread messageSenderThread = 
            new Thread(messageSender, "MessageSender[" + getServletContext().getContextPath() + "]");
        messageSenderThread.setDaemon(true);
        messageSenderThread.start();
    }

    public void destroy() {
        connections.clear();
        messageSender.stop();
        messageSender = null;
    }

    /**
     * Process the given Comet event.
     * 
     * @param event The Comet event that will be processed
     * @throws IOException
     * @throws ServletException
     */
    public void event(CometEvent event)
        throws IOException, ServletException {

        // Note: There should really be two servlets in this example, to avoid
        // mixing Comet stuff with regular connection processing
        HttpServletRequest request = event.getHttpServletRequest();
        HttpServletResponse response = event.getHttpServletResponse();
        
        if (event.getEventType() == CometEvent.EventType.BEGIN) {
            String action = request.getParameter("action");
            if (action != null) {
                if ("login".equals(action)) {
                    String nickname = request.getParameter("nickname");
                    request.getSession(true).setAttribute("nickname", nickname);
                    response.sendRedirect("post.jsp");
                    event.close();
                    return;
                } else {
                    String nickname = (String) request.getSession(true).getAttribute("nickname");
                    String message = request.getParameter("message");
                    messageSender.send(nickname, message);
                    response.sendRedirect("post.jsp");
                    event.close();
                    return;
                }
            } else {
                if (request.getSession(true).getAttribute("nickname") == null) {
                    // Redirect to "login"
                    log("Redirect to login for session: " + request.getSession(true).getId());
                    response.sendRedirect("login.jsp");
                    event.close();
                    return;
                }
            }
            begin(event, request, response);
        } else if (event.getEventType() == CometEvent.EventType.ERROR) {
            error(event, request, response);
        } else if (event.getEventType() == CometEvent.EventType.END) {
            end(event, request, response);
        } else if (event.getEventType() == CometEvent.EventType.READ) {
            read(event, request, response);
        }
    }

    protected void begin(CometEvent event, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        log("Begin for session: " + request.getSession(true).getId());
        
        PrintWriter writer = response.getWriter();
        writer.println("<!doctype html public \"-//w3c//dtd html 4.0 transitional//en\">");
        writer.println("<html><head><title>JSP Chat</title></head><body bgcolor=\"#FFFFFF\">");
        writer.flush();

        synchronized(connections) {
            connections.add(response);
        }
    }
    
    protected void end(CometEvent event, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        log("End for session: " + request.getSession(true).getId());
        synchronized(connections) {
            connections.remove(response);
        }
        
        PrintWriter writer = response.getWriter();
        writer.println("</body></html>");
        
        event.close();
        
    }
    
    protected void error(CometEvent event, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        log("Error for session: " + request.getSession(true).getId());
        synchronized(connections) {
            connections.remove(response);
        }
        event.close();
    }
    
    protected void read(CometEvent event, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        InputStream is = request.getInputStream();
        byte[] buf = new byte[512];
        while (is.available() > 0) {
            log("Available: " + is.available());
            int n = is.read(buf);
            if (n > 0) {
                log("Read " + n + " bytes: " + new String(buf, 0, n) 
                        + " for session: " + request.getSession(true).getId());
            } else if (n < 0) {
                log("End of file: " + n);
                end(event, request, response);
                return;
            }
        }
    }

    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        // Compatibility method: equivalent method using the regular connection model
        PrintWriter writer = response.getWriter();
        writer.println("<!doctype html public \"-//w3c//dtd html 4.0 transitional//en\">");
        writer.println("<html><head><title>JSP Chat</title></head><body bgcolor=\"#FFFFFF\">");
        writer.println("Chat example only supports Comet processing");
        writer.println("</body></html>");
    }
    

    /**
     * Poller class.
     */
    public class MessageSender implements Runnable {

        protected boolean running = true;
        protected ArrayList<String> messages = new ArrayList<String>();
        
        public MessageSender() {
        }
        
        public void stop() {
            running = false;
        }

        /**
         * Add specified socket and associated pool to the poller. The socket will
         * be added to a temporary array, and polled first after a maximum amount
         * of time equal to pollTime (in most cases, latency will be much lower,
         * however).
         *
         * @param socket to add to the poller
         */
        public void send(String user, String message) {
            synchronized (messages) {
                messages.add("[" + user + "]: " + message);
                messages.notify();
            }
        }

        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {

            // Loop until we receive a shutdown command
            while (running) {
                // Loop if endpoint is paused

                if (messages.size() == 0) {
                    try {
                        synchronized (messages) {
                            messages.wait();
                        }
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                synchronized (connections) {
                    String[] pendingMessages = null;
                    synchronized (messages) {
                        pendingMessages = messages.toArray(new String[0]);
                        messages.clear();
                    }
                    for (int i = 0; i < connections.size(); i++) {
                        try {
                            PrintWriter writer = connections.get(i).getWriter();
                            for (int j = 0; j < pendingMessages.length; j++) {
                                // FIXME: Add HTML filtering
                                writer.println(pendingMessages[j] + "<br/>");
                            }
                            writer.flush();
                        } catch (IOException e) {
                            log("IOExeption sending message", e);
                        }
                    }
                }

            }

        }

    }




}
