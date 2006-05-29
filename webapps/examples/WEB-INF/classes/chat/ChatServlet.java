/*
 * Copyright 2006 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.apache.catalina.servlets.CometServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Helper class to implement Comet functionality.
 */
public class ChatServlet
    extends CometServlet {

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

    public void begin(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        super.begin(request, response);
        log("Begin for session: " + request.getSession(true).getId());
        
        PrintWriter writer = response.getWriter();
        writer.println("<!doctype html public \"-//w3c//dtd html 4.0 transitional//en\">");
        writer.println("<head><title>JSP Chat</title></head><body bgcolor=\"#FFFFFF\">");
        writer.flush();

        synchronized(connections) {
            connections.add(response);
        }
    }
    
    public void end(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        super.end(request, response);
        log("End for session: " + request.getSession(true).getId());
        synchronized(connections) {
            connections.remove(response);
        }
        
        PrintWriter writer = response.getWriter();
        writer.println("</body></html>");
        
    }
    
    public void error(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        log("Error for session: " + request.getSession(true).getId());
        end(request, response);
    }
    
    public boolean read(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        InputStream is = request.getInputStream();
        byte[] buf = new byte[512];
        do {
            int n = is.read(buf);
            if (n > 0) {
                log("Read " + n + " bytes: " + new String(buf, 0, n) 
                        + " for session: " + request.getSession(true).getId());
            } else if (n < 0) {
                return false;
            }
        } while (is.available() > 0);
        return true;
    }

    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        String action = request.getParameter("action");
        if (action != null) {
            if ("login".equals(action)) {
                String nickname = request.getParameter("nickname");
                request.getSession(true).setAttribute("nickname", nickname);
                response.sendRedirect("post.jsp");
            } else {
                String nickname = (String) request.getSession(true).getAttribute("nickname");
                String message = request.getParameter("message");
                messageSender.send(nickname, message);
                response.sendRedirect("post.jsp");
            }
        } else {
            if (request.getSession(true).getAttribute("nickname") == null) {
                // Redirect to "login"
                response.sendRedirect("login.jsp");
            } else {
                // Request to view the chet, so use Comet
                super.service(request, response);
            }
        }
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
                                writer.println(pendingMessages[j] + "<br>");
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
