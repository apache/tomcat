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
package org.apache.coyote.http2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.startup.Tomcat;

/**
 * Tests for failures during HTTP/2 sendfile writes. Sendfile is only used with asynchronous IO so the tests only run
 * with asynchronous IO enabled.
 */
public class TestHttp2Sendfile extends Http2TestBase {

    // Large enough that it can't be buffered by the OS socket buffers (loopback)
    private static final long FILE_SIZE = 64L * 1024 * 1024;

    private static final long WRITE_TIMEOUT = 2000;
    private static final long KEEP_ALIVE_TIMEOUT = 30000;

    /*
     * Sendfile is only used with asynchronous IO so only generate the parameter sets that use it. This hides
     * Http2TestBase.data().
     */
    @Parameters(name = "{index}: loop [{0}], useAsyncIO[{1}]")
    public static Collection<Object[]> data() {
        List<Object[]> result = new ArrayList<>();
        for (Object[] base : Http2TestBase.data()) {
            if (((Boolean) base[1]).booleanValue()) {
                result.add(base);
            }
        }
        return result;
    }

    private File file;


    @Before
    public void createFile() throws IOException {
        file = File.createTempFile("tomcat-http2-sendfile", ".bin", getTemporaryDirectory());
        file.deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(FILE_SIZE);
        }
    }


    @After
    public void deleteFile() {
        if (file != null && !file.delete()) {
            file.deleteOnExit();
        }
    }


    /*
     * The client requests a file that is far larger than the TCP buffers, grants enough flow control window for the
     * whole file and then stops reading. The sendfile write times out. The server must close the connection rather than
     * leave it open (with the stream counted as active so the connection timeout is disabled and the socket no longer
     * registered with the poller).
     */
    @Test
    public void testSendfileWriteTimeoutClosesConnection() throws Exception {
        startAndSendSendfileRequest();

        // Do not read anything. Wait for the write timeout to trigger. Allow plenty of time for the poller to notice.
        Thread.sleep(WRITE_TIMEOUT * 3);

        // Now start reading again. All the buffered data should be received followed by the connection being closed.
        assertServerClosesConnection();
    }


    /*
     * The client resets the stream part way through a sendfile response. Sendfile reserves the connection flow control
     * window for the whole file before it starts writing. The part of that reservation that is not used because the
     * stream was reset must be returned to the connection window.
     *
     * The connection window is sized so that, if the unused reservation is returned, a second response of
     * SimpleServlet.CONTENT_LENGTH bytes fits. If the unused reservation is lost, the window is exactly one byte too
     * small and the second response can't be completed.
     */
    @Test
    public void testSendfileResetReturnsConnectionWindow() throws Exception {
        doSendfileResetTest(SimpleServlet.CONTENT_LENGTH - 1);
    }


    /*
     * Control for the test above. The window is large enough for the second response even if the unused part of the
     * sendfile reservation is lost. This must pass whether or not the reservation is returned and confirms that the
     * failure of the test above is caused by the window size and not by the test setup.
     */
    @Test
    public void testSendfileResetConnectionWindowControl() throws Exception {
        doSendfileResetTest(SimpleServlet.CONTENT_LENGTH);
    }


    /*
     * The connection window is smaller than the file so sendfile only obtains a partial reservation and the stream is
     * added to the connection's backlog for the remainder. Once the partial reservation has been sent, sendfile waits
     * in the backlog for more window. The client resets the stream while it is waiting. The stream must not remain in
     * the backlog since, if it does, window the client grants later is allocated to the dead stream and is not
     * available to other streams.
     */
    @Test
    public void testSendfileResetWhileInBacklog() throws Exception {
        // Connection window remaining after the response to the initial upgrade request
        long remaining = 65535 - SimpleServlet.CONTENT_LENGTH;
        // Smaller than the file but more than one frame
        int partialReservation = 100000;
        startAndSendSendfileRequest((int) (partialReservation - remaining), 5000);

        // Drain the data for the partial reservation. When the server goes quiet it is waiting for more window. Reset
        // the stream before the connection write timeout fires, else the server will close the connection.
        s.setSoTimeout(500);
        try {
            while (true) {
                parser.readFrame();
            }
        } catch (SocketTimeoutException e) {
            // Server is idle, waiting for window
        }
        s.setSoTimeout(60000);
        sendRst(3, Http2Error.CANCEL.getCode());

        // Allow the server to process the reset
        Thread.sleep(500);

        // Give the connection just enough window for the response to a new request.
        sendSimpleGetRequest(5);
        sendWindowUpdate(0, SimpleServlet.CONTENT_LENGTH);

        int frames = 0;
        boolean ended = false;
        StringBuilder trace = new StringBuilder();
        while (!ended && frames++ < 100) {
            output.clearTrace();
            try {
                parser.readFrame();
            } catch (IOException ioe) {
                Assert.fail("Connection closed before the response was complete. Trace so far: " + trace + " " + ioe);
            }
            trace.append(output.getTrace());
            ended = output.getTrace().contains("5-EndOfStream") || output.getTrace().contains("5-RST");
        }
        Assert.assertTrue(trace.toString(), trace.toString().contains("5-EndOfStream"));
        Assert.assertFalse(trace.toString(), trace.toString().contains("5-RST"));
    }


    /*
     * @param windowAfterReservation The connection window left once sendfile has reserved FILE_SIZE bytes
     */
    private void doSendfileResetTest(long windowAfterReservation) throws Exception {
        // Connection window remaining after the response to the initial upgrade request
        long remaining = 65535 - SimpleServlet.CONTENT_LENGTH;
        long target = FILE_SIZE + windowAfterReservation;
        startAndSendSendfileRequest((int) (target - remaining), 5000);

        // Read the headers and a few data frames so we know sendfile is under way
        for (int i = 0; i < 5; i++) {
            output.clearTrace();
            parser.readFrame();
        }

        // Cancel the stream. Do not return any window to the server for the data received.
        sendRst(3, Http2Error.CANCEL.getCode());

        // Drain anything already in flight until the server goes quiet. This also allows any pending write to complete.
        s.setSoTimeout(1000);
        try {
            while (true) {
                parser.readFrame();
            }
        } catch (SocketTimeoutException e) {
            // Server is idle
        }
        s.setSoTimeout(60000);

        // Request a small response on a new stream. It needs all of the connection window that is left.
        output.clearTrace();
        output.setTraceBody(false);
        sendSimpleGetRequest(5);

        int frames = 0;
        boolean ended = false;
        StringBuilder trace = new StringBuilder();
        while (!ended && frames++ < 100) {
            output.clearTrace();
            try {
                parser.readFrame();
            } catch (IOException ioe) {
                Assert.fail("Connection closed before the response was complete. Trace so far: " + trace + " " + ioe);
            }
            trace.append(output.getTrace());
            ended = output.getTrace().contains("5-EndOfStream") || output.getTrace().contains("5-RST");
        }
        Assert.assertTrue(trace.toString(), trace.toString().contains("5-EndOfStream"));
        Assert.assertFalse(trace.toString(), trace.toString().contains("5-RST"));
    }


    private void startAndSendSendfileRequest() throws Exception {
        startAndSendSendfileRequest(Integer.MAX_VALUE - 65535, 30000);
    }


    private void startAndSendSendfileRequest(int connectionWindowIncrement, long streamWriteTimeout) throws Exception {
        // Short write timeout so the test completes quickly. Long stream timeouts so they do not interfere.
        enableHttp2(200, false, 30000, WRITE_TIMEOUT, KEEP_ALIVE_TIMEOUT, 30000, streamWriteTimeout);

        Tomcat tomcat = getTomcatInstance();
        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "sendfile", new SendfileServlet(file));
        ctxt.addServletMapping("/sendfile", "sendfile");
        tomcat.start();

        openClientConnection();
        // Reading must be able to block for much longer than any of the server side timeouts
        s.setSoTimeout(60000);
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Allow the entire file to be sent without any further window updates from the client (unless the caller has
        // asked for something smaller). The connection window is already partly used by the initial response. The
        // stream window is set before the stream is created.
        sendSettings(0, false, new SettingValue(Setting.INITIAL_WINDOW_SIZE.getId(), Integer.MAX_VALUE));
        sendWindowUpdate(0, connectionWindowIncrement);

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/sendfile");
        writeFrame(frameHeader, headersPayload);
    }


    private void assertServerClosesConnection() throws IOException {
        InputStream is = s.getInputStream();
        byte[] buffer = new byte[64 * 1024];
        try {
            while (is.read(buffer) != -1) {
                // Discard buffered data until the server closes the connection
            }
        } catch (SocketTimeoutException e) {
            Assert.fail("The server did not close the connection after the sendfile write timed out");
        } catch (SocketException e) {
            // Connection reset. That is also the server closing the connection.
        }
    }


    private static class SendfileServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final File file;

        SendfileServlet(File file) {
            this.file = file;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            Assert.assertEquals(Boolean.TRUE, req.getAttribute(Globals.SENDFILE_SUPPORTED_ATTR));
            req.setAttribute(Globals.SENDFILE_FILENAME_ATTR, file.getCanonicalPath());
            req.setAttribute(Globals.SENDFILE_FILE_START_ATTR, Long.valueOf(0));
            req.setAttribute(Globals.SENDFILE_FILE_END_ATTR, Long.valueOf(file.length()));
            resp.setContentType("application/octet-stream");
            resp.setContentLengthLong(file.length());
        }
    }
}
