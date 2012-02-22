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
package org.apache.tomcat.spdy;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One SPDY stream.
 * 
 * Created by SpdyContext.getProcessor(framer).
 * 
 * The methods are called in a IO thread when the framer received a frame for
 * this stream.
 * 
 * They should not block.
 * 
 * The frame must be either consumed or popInFrame must be called, after the
 * call is done the frame will be reused.
 */
public class SpdyStream {
    public static final Charset UTF8 = Charset.forName("UTF-8");

    protected SpdyConnection spdy;

    public SpdyFrame reqFrame;

    SpdyFrame resFrame;

    BlockingQueue<SpdyFrame> inData = new LinkedBlockingQueue<SpdyFrame>();

    public static final SpdyFrame END_FRAME = new SpdyFrame(16);

    boolean finSent;

    protected boolean finRcvd;

    public SpdyStream(SpdyConnection spdy) {
        this.spdy = spdy;
    }
    
    /**
     * Non-blocking, called when a data frame is received.
     * 
     * The processor must consume the data, or set frame.data to null or a fresh
     * buffer ( to avoid a copy ).
     */
    public void onDataFrame(SpdyFrame inFrame) {
        inData.add(inFrame);
        if (inFrame.closed()) {
            finRcvd = true;
            inData.add(END_FRAME);
        }
    }

    /**
     * Non-blocking - handles a syn stream package. The processor must consume
     * frame.data or set it to null.
     * 
     * The base method is for client implementation - servers need to override
     * and process the frame as a request. 
     */
    public void onCtlFrame(SpdyFrame frame) throws IOException {
        // TODO: handle RST
        if (frame.type == SpdyConnection.TYPE_SYN_STREAM) {
            reqFrame = frame;
        } else if (frame.type == SpdyConnection.TYPE_SYN_REPLY) {
            resFrame = frame;
        }
        if (frame.isHalfClose()) {
            finRcvd = true;
        }
    }

    /**
     * True if the channel both received and sent FIN frames.
     * 
     * This is tracked by the processor, to avoid extra storage in framer.
     */
    public boolean isFinished() {
        return finSent && finRcvd;
    }

    public SpdyFrame getIn(long to) throws IOException {
        SpdyFrame in;
        try {
            if (inData.size() == 0 && finRcvd) {
                return null;
            }
            in = inData.poll(to, TimeUnit.MILLISECONDS);

            if (in == END_FRAME) {
                return null;
            }
            return in;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
    
    public void getHeaders(Map<String, String> resHeaders) {
        SpdyFrame f = resFrame;
        int nvCount = f.nvCount;
        for (int i = 0; i < nvCount; i++) {
            int len = f.read16();
            String n = new String(f.data, f.off, len, UTF8);
            f.advance(len);
            len = f.read16();
            String v = new String(f.data, f.off, len, UTF8);
            f.advance(len);
            resHeaders.put(n, v);
        }
    }
    
    public SpdyFrame getRequest() {
        if (reqFrame == null) {
            reqFrame = spdy.getFrame(SpdyConnection.TYPE_SYN_STREAM);
        }
        return reqFrame;
    }

    public void addHeader(String name, String value) {
        byte[] nameB = name.getBytes();
        getRequest().headerName(nameB, 0, nameB.length);
        nameB = value.getBytes();
        reqFrame.headerValue(nameB, 0, nameB.length);
    }
    
    
    public synchronized void sendDataFrame(byte[] data, int start,
            int length, boolean close) throws IOException {

        SpdyFrame oframe = spdy.getDataFrame();

        // Options:
        // 1. wrap the byte[] data, use a separate header[], wait frame sent
        // -> 2 socket writes
        // 2. copy the data to frame byte[] -> non-blocking queue
        // 3. copy the data, blocking drain -> like 1, trade one copy to
        // avoid
        // 1 tcp packet. That's the current choice, seems closer to rest of
        // tomcat

        oframe.streamId = reqFrame.streamId;
        if (close)
            oframe.halfClose();

        oframe.append(data, start, length);
        spdy.sendFrameBlocking(oframe, this);
    }

    public void send() throws IOException {
        send("http", "GET");
    }

    public void send(String host, String url, String scheme, String method) throws IOException {
        addHeader("host", host);
        addHeader("url", url);

        send(scheme, method);
    }
    
    public void send(String scheme, String method) throws IOException {
        getRequest();
        if ("GET".equalsIgnoreCase(method)) {
            // TODO: add the others
            reqFrame.halfClose();
        }
        addHeader("scheme", "http"); // todo
        addHeader("method", method);
        addHeader("version", "HTTP/1.1");
        if (reqFrame.isHalfClose()) {
            finSent = true;
        }
        spdy.sendFrameBlocking(reqFrame, this);
    }
    
}