/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.catalina.connector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;

import junit.framework.TestCase;

/**
 * Test case for {@link Request}. 
 */
public class TestRequest extends TestCase {
    /**
     * Test case for https://issues.apache.org/bugzilla/show_bug.cgi?id=37794
     * POST parameters are not returned from a call to 
     * any of the {@link HttpServletRequest} getParameterXXX() methods if the
     * request is chunked.
     */
    public void testBug37794() throws Exception {
        Bug37794Client client = new Bug37794Client();

        // Edge cases around zero
        client.doRequest(-1, false); // Unlimited
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        client.reset();
        client.doRequest(0, false); // Unlimited
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        client.reset();
        client.doRequest(1, false); // 1 byte - too small should fail
        assertTrue(client.isResponse500());
        
        client.reset();
        
        // Edge cases around actual content length
        client.reset();
        client.doRequest(6, false); // Too small should fail
        assertTrue(client.isResponse500());
        client.reset();
        client.doRequest(7, false); // Just enough should pass
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        client.reset();
        client.doRequest(8, false); // 1 extra - should pass
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        
        // Much larger
        client.reset();
        client.doRequest(8096, false); // Plenty of space - should pass
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());

        // Check for case insensitivity
        client.reset();
        client.doRequest(8096, true); // Plenty of space - should pass
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
    }
    
    private static class Bug37794Servlet extends HttpServlet {
        
        /**
         * Only interested in the parameters and values for POST requests.
         */
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Just echo the parameters and values back as plain text
            resp.setContentType("text/plain");
            
            PrintWriter out = resp.getWriter();
            
            // Assume one value per attribute
            Enumeration<String> names = req.getParameterNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                out.println(name + "=" + req.getParameter(name));
            }
        }
    }
    
    /**
     * Bug 37794 test client.
     */
    private static class Bug37794Client extends SimpleHttpClient {
        private Exception doRequest(int postLimit, boolean ucChunkedHead) {
            Tomcat tomcat = new Tomcat();
            try {
                StandardContext root = tomcat.addContext("", TEMP_DIR);
                Tomcat.addServlet(root, "Bug37794", new Bug37794Servlet());
                root.addServletMapping("/test", "Bug37794");
                tomcat.getConnector().setMaxPostSize(postLimit);
                tomcat.start();
                
                // Open connection
                connect();
                
                // Send request in two parts
                String[] request = new String[2];
                if (ucChunkedHead) {
                    request[0] =
                        "POST http://localhost:8080/test HTTP/1.1" + CRLF +
                        "content-type: application/x-www-form-urlencoded" + CRLF +
                        "Transfer-Encoding: CHUNKED" + CRLF +
                        "Connection: close" + CRLF +
                        CRLF +
                        "3" + CRLF +
                        "a=1" + CRLF;
                } else {
                    request[0] =
                        "POST http://localhost:8080/test HTTP/1.1" + CRLF +
                        "content-type: application/x-www-form-urlencoded" + CRLF +
                        "Transfer-Encoding: chunked" + CRLF +
                        "Connection: close" + CRLF +
                        CRLF +
                        "3" + CRLF +
                        "a=1" + CRLF;
                }
                request[1] =
                    "4" + CRLF +
                    "&b=2" + CRLF +
                    "0" + CRLF +
                    CRLF;
                
                setRequest(request);
                processRequest(); // blocks until response has been read
                
                // Close the connection
                disconnect();
            } catch (Exception e) {
                return e;
            } finally {
                try {
                    tomcat.stop();
                } catch (Exception e) {
                    // Ignore
                }
            }
            return null;
        }

        public boolean isResponseBodyOK() {
            if (getResponseBody() == null) {
                return false;
            }
            if (!getResponseBody().contains("a=1")) {
                return false;
            }
            if (!getResponseBody().contains("b=2")) {
                return false;
            }
            return true;
        }
        
    }

    /**
     * Simple client for unit testing. It isn't robust, it isn't secure and
     * should not be used as the basis for production code. Its only purpose
     * is to do the bare minimum for the unit tests. 
     */
    private abstract static class SimpleHttpClient {
        public static final String TEMP_DIR =
            System.getProperty("java.io.tmpdir");
        
        public static final String CRLF = "\r\n";

        public static final String OK_200 = "HTTP/1.1 200";
        public static final String FAIL_500 = "HTTP/1.1 500";
        
        private Socket socket;
        private Writer writer;
        private BufferedReader reader;
        
        private String[] request;
        private int requestPause = 1000;
        
        private String responseLine;
        private List<String> responseHeaders = new ArrayList<String>();
        private String responseBody;

        public void setRequest(String[] theRequest) {
            request = theRequest;
        }
        
        public void setRequestPause(int theRequestPause) {
            requestPause = theRequestPause;
        }

        public String getResponseLine() {
            return responseLine;
        }

        public List<String> getResponseHeaders() {
            return responseHeaders;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public void connect() throws UnknownHostException, IOException {
            socket = new Socket("localhost", 8080);
            OutputStream os = socket.getOutputStream();
            writer = new OutputStreamWriter(os);
            InputStream is = socket.getInputStream();
            Reader r = new InputStreamReader(is);
            reader = new BufferedReader(r);
        }
        
        public void processRequest() throws IOException, InterruptedException {
            // Send the request
            boolean first = true;
            for (String requestPart : request) {
                if (first) {
                    first = false;
                } else {
                    Thread.sleep(requestPause);
                }
                writer.write(requestPart);
                writer.flush();
            }

            // Read the response
            responseLine = readLine();
            
            // Put the headers into the map
            String line = readLine();
            while (line.length() > 0) {
                responseHeaders.add(line);
                line = readLine();
            }
            
            // Read the body, if any
            StringBuilder builder = new StringBuilder();
            line = readLine();
            while (line != null && line.length() > 0) {
                builder.append(line);
                line = readLine();
            }
            responseBody = builder.toString();

        }

        public String readLine() throws IOException {
            return reader.readLine();
        }
        
        public void disconnect() throws IOException {
            writer.close();
            reader.close();
            socket.close();
        }
        
        public void reset() {
            socket = null;
            writer = null;
            reader = null;
            
            request = null;
            requestPause = 1000;
            
            responseLine = null;
            responseHeaders = new ArrayList<String>();
            responseBody = null;
        }
        
        public boolean isResponse200() {
            return getResponseLine().startsWith(OK_200);
        }
        
        public boolean isResponse500() {
            return getResponseLine().startsWith(FAIL_500);
        }

        public abstract boolean isResponseBodyOK();
    }
}
