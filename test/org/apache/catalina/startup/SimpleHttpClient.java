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

package org.apache.catalina.startup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple client for unit testing. It isn't robust, it isn't secure and
 * should not be used as the basis for production code. Its only purpose
 * is to do the bare minimum for the unit tests. It does not support keep-alive
 * connections - make sure you send a Connection: close header with the request.
 */
public abstract class SimpleHttpClient {
    public static final String TEMP_DIR =
        System.getProperty("java.io.tmpdir");
    
    public static final String CRLF = "\r\n";

    public static final String OK_200 = "HTTP/1.1 200";
    public static final String FAIL_404 = "HTTP/1.1 404";
    public static final String FAIL_500 = "HTTP/1.1 500";
    
    private Socket socket;
    private Writer writer;
    private BufferedReader reader;
    private int port = 8080;
    
    private String[] request;
    private int requestPause = 1000;
    
    private String responseLine;
    private List<String> responseHeaders = new ArrayList<String>();
    private String responseBody;

    public void setPort(int thePort) {
        port = thePort;
    }

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
        socket = new Socket("localhost", port);
        OutputStream os = socket.getOutputStream();
        writer = new OutputStreamWriter(os);
        InputStream is = socket.getInputStream();
        Reader r = new InputStreamReader(is);
        reader = new BufferedReader(r);
    }
    
    public void processRequest() throws IOException, InterruptedException {
        processRequest(true);
    }
    public void processRequest(boolean readBody) throws IOException, InterruptedException {
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
        while (line!=null && line.length() > 0) {
            responseHeaders.add(line);
            line = readLine();
        }
        
        // Read the body, if any
        StringBuilder builder = new StringBuilder();
        if (readBody) {
            line = readLine();
            while (line != null && line.length() > 0) {
                builder.append(line);
                line = readLine();
            }
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
    
    public boolean isResponse404() {
        return getResponseLine().startsWith(FAIL_404);
    }

    public boolean isResponse500() {
        return getResponseLine().startsWith(FAIL_500);
    }
    
    public Socket getSocket() {
        return socket;
    }

    public abstract boolean isResponseBodyOK();
}