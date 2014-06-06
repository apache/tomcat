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
package org.apache.catalina.startup;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Manager;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Base test case that provides a Tomcat instance for each test - mainly so we
 * don't have to keep writing the cleanup code.
 */
public abstract class TomcatBaseTest extends LoggingBaseTest {
    private Tomcat tomcat;
    private boolean accessLogEnabled = false;

    public static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * Make Tomcat instance accessible to sub-classes.
     */
    public Tomcat getTomcatInstance() {
        return tomcat;
    }

    /**
     * Sub-classes need to know port so they can connect
     */
    public int getPort() {
        return tomcat.getConnector().getLocalPort();
    }

    /**
     * Sub-classes may want to check, whether an AccessLogValve is active
     */
    public boolean isAccessLogEnabled() {
        return accessLogEnabled;
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Trigger loading of catalina.properties
        CatalinaProperties.getProperty("foo");

        File appBase = new File(getTemporaryDirectory(), "webapps");
        if (!appBase.exists() && !appBase.mkdir()) {
            fail("Unable to create appBase for test");
        }

        tomcat = new TomcatWithFastSessionIDs();

        String protocol = getProtocol();
        Connector connector = new Connector(protocol);
        // Listen only on localhost
        connector.setAttribute("address",
                InetAddress.getByName("localhost").getHostAddress());
        // Use random free port
        connector.setPort(0);
        // Mainly set to reduce timeouts during async tests
        connector.setAttribute("connectionTimeout", "3000");
        tomcat.getService().addConnector(connector);
        tomcat.setConnector(connector);

        // Add AprLifecycleListener if we are using the Apr connector
        if (protocol.contains("Apr")) {
            StandardServer server = (StandardServer) tomcat.getServer();
            AprLifecycleListener listener = new AprLifecycleListener();
            listener.setSSLRandomSeed("/dev/urandom");
            server.addLifecycleListener(listener);
            connector.setAttribute("pollerThreadCount", Integer.valueOf(1));
        }

        File catalinaBase = getTemporaryDirectory();
        tomcat.setBaseDir(catalinaBase.getAbsolutePath());
        tomcat.getHost().setAppBase(appBase.getAbsolutePath());

        accessLogEnabled = Boolean.parseBoolean(
            System.getProperty("tomcat.test.accesslog", "false"));
        if (accessLogEnabled) {
            String accessLogDirectory = System
                    .getProperty("tomcat.test.reports");
            if (accessLogDirectory == null) {
                accessLogDirectory = new File(getBuildDirectory(), "logs")
                        .toString();
            }
            AccessLogValve alv = new AccessLogValve();
            alv.setDirectory(accessLogDirectory);
            alv.setPattern("%h %l %u %t \"%r\" %s %b %I %D");
            tomcat.getHost().getPipeline().addValve(alv);
        }

        // Cannot delete the whole tempDir, because logs are there,
        // but delete known subdirectories of it.
        addDeleteOnTearDown(new File(catalinaBase, "webapps"));
        addDeleteOnTearDown(new File(catalinaBase, "work"));
    }

    protected String getProtocol() {
        // Has a protocol been specified
        String protocol = System.getProperty("tomcat.test.protocol");

        // Use NIO by default starting with Tomcat 8
        if (protocol == null) {
            protocol = Http11NioProtocol.class.getName();
        }

        return protocol;
    }

    @After
    @Override
    public void tearDown() throws Exception {
        try {
            // Some tests may call tomcat.destroy(), some tests may just call
            // tomcat.stop(), some not call either method. Make sure that stop()
            // & destroy() are called as necessary.
            if (tomcat.server != null
                    && tomcat.server.getState() != LifecycleState.DESTROYED) {
                if (tomcat.server.getState() != LifecycleState.STOPPED) {
                    tomcat.stop();
                }
                tomcat.destroy();
            }
        } finally {
            super.tearDown();
        }
    }

    /**
     * Simple Hello World servlet for use by test cases
     */
    public static final class HelloWorldServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        public static final String RESPONSE_TEXT =
            "<html><body><p>Hello World</p></body></html>";

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            PrintWriter out = resp.getWriter();
            out.print(RESPONSE_TEXT);
        }
    }


    /**
     *  Wrapper for getting the response.
     */
    public static ByteChunk getUrl(String path) throws IOException {
        ByteChunk out = new ByteChunk();
        getUrl(path, out, null);
        return out;
    }

    public static int getUrl(String path, ByteChunk out,
            Map<String, List<String>> resHead) throws IOException {
        return getUrl(path, out, null, resHead);
    }

    public static int headUrl(String path, ByteChunk out,
            Map<String, List<String>> resHead) throws IOException {
        return methodUrl(path, out, 1000000, null, resHead, "HEAD");
    }

    public static int getUrl(String path, ByteChunk out,
            Map<String, List<String>> reqHead,
            Map<String, List<String>> resHead) throws IOException {
        return getUrl(path, out, 1000000, reqHead, resHead);
    }

    public static int getUrl(String path, ByteChunk out, int readTimeout,
            Map<String, List<String>> reqHead,
            Map<String, List<String>> resHead) throws IOException {
        return methodUrl(path, out, readTimeout, reqHead, resHead, "GET");
    }

    public static int methodUrl(String path, ByteChunk out, int readTimeout,
            Map<String, List<String>> reqHead,
            Map<String, List<String>> resHead,
            String method) throws IOException {

        URL url = new URL(path);
        HttpURLConnection connection =
            (HttpURLConnection) url.openConnection();
        connection.setUseCaches(false);
        connection.setReadTimeout(readTimeout);
        connection.setRequestMethod(method);
        if (reqHead != null) {
            for (Map.Entry<String, List<String>> entry : reqHead.entrySet()) {
                StringBuilder valueList = new StringBuilder();
                for (String value : entry.getValue()) {
                    if (valueList.length() > 0) {
                        valueList.append(',');
                    }
                    valueList.append(value);
                }
                connection.setRequestProperty(entry.getKey(),
                        valueList.toString());
            }
        }
        connection.connect();
        int rc = connection.getResponseCode();
        if (resHead != null) {
            Map<String, List<String>> head = connection.getHeaderFields();
            resHead.putAll(head);
        }
        InputStream is;
        if (rc < 400) {
            is = connection.getInputStream();
        } else {
            is = connection.getErrorStream();
        }
        if (is != null) {
            try (BufferedInputStream bis = new BufferedInputStream(is)) {
                byte[] buf = new byte[2048];
                int rd = 0;
                while((rd = bis.read(buf)) > 0) {
                    out.append(buf, 0, rd);
                }
            }
        }
        return rc;
    }

    public static ByteChunk postUrl(byte[] body, String path)
            throws IOException {
        ByteChunk out = new ByteChunk();
        postUrl(body, path, out, null);
        return out;
    }

    public static int postUrl(byte[] body, String path, ByteChunk out,
            Map<String, List<String>> resHead) throws IOException {
        return postUrl(body, path, out, null, resHead);
    }

    public static int postUrl(final byte[] body, String path, ByteChunk out,
            Map<String, List<String>> reqHead,
            Map<String, List<String>> resHead) throws IOException {
            BytesStreamer s = new BytesStreamer() {
            boolean done = false;
            @Override
            public byte[] next() {
                done = true;
                return body;

            }

            @Override
            public int getLength() {
                return body!=null?body.length:0;
            }

            @Override
            public int available() {
                if (done) return 0;
                else return getLength();
            }
        };
        return postUrl(false,s,path,out,reqHead,resHead);
    }


    public static int postUrl(boolean stream, BytesStreamer streamer, String path, ByteChunk out,
                Map<String, List<String>> reqHead,
                Map<String, List<String>> resHead) throws IOException {

        URL url = new URL(path);
        HttpURLConnection connection =
            (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setReadTimeout(1000000);
        if (reqHead != null) {
            for (Map.Entry<String, List<String>> entry : reqHead.entrySet()) {
                StringBuilder valueList = new StringBuilder();
                for (String value : entry.getValue()) {
                    if (valueList.length() > 0) {
                        valueList.append(',');
                    }
                    valueList.append(value);
                }
                connection.setRequestProperty(entry.getKey(),
                        valueList.toString());
            }
        }
        if (streamer != null && stream) {
            if (streamer.getLength()>0) {
                connection.setFixedLengthStreamingMode(streamer.getLength());
            } else {
                connection.setChunkedStreamingMode(1024);
            }
        }

        connection.connect();

        // Write the request body
        try (OutputStream os = connection.getOutputStream()) {
            while (streamer != null && streamer.available() > 0) {
                byte[] next = streamer.next();
                os.write(next);
                os.flush();
            }
        }

        int rc = connection.getResponseCode();
        if (resHead != null) {
            Map<String, List<String>> head = connection.getHeaderFields();
            resHead.putAll(head);
        }
        InputStream is;
        if (rc < 400) {
            is = connection.getInputStream();
        } else {
            is = connection.getErrorStream();
        }

        try (BufferedInputStream bis = new BufferedInputStream(is)) {
            byte[] buf = new byte[2048];
            int rd = 0;
            while((rd = bis.read(buf)) > 0) {
                out.append(buf, 0, rd);
            }
        }
        return rc;
    }

    protected static String getStatusCode(String statusLine) {
        if (statusLine == null || statusLine.length() < 12) {
            return statusLine;
        } else {
            return statusLine.substring(9, 12);
        }
    }

    private static class TomcatWithFastSessionIDs extends Tomcat {

        @Override
        public void start() throws LifecycleException {
            // Use fast, insecure session ID generation for all tests
            Server server = getServer();
            for (Service service : server.findServices()) {
                Container e = service.getContainer();
                for (Container h : e.findChildren()) {
                    for (Container c : h.findChildren()) {
                        Manager m = ((Context) c).getManager();
                        if (m == null) {
                            m = new StandardManager();
                            ((Context) c).setManager(m);
                        }
                        if (m instanceof ManagerBase) {
                            ((ManagerBase) m).setSecureRandomClass(
                                    "org.apache.catalina.startup.FastNonSecureRandom");
                        }
                    }
                }
            }
            super.start();
        }
    }
}
