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
import java.io.PrintWriter;
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

    public SpdyFrame resFrame;

    /**
     * For blocking support.
     */
    protected BlockingQueue<SpdyFrame> inData = new LinkedBlockingQueue<SpdyFrame>();

    protected boolean finSent;

    protected boolean finRcvd;
    
    /**
     *  Dummy data frame to insert on reset / go away
     */
    static SpdyFrame END_FRAME;
    
    static {
        END_FRAME = new SpdyFrame(16);
        END_FRAME.endData = 0;
        END_FRAME.off = 0;
        END_FRAME.c = false; 
        END_FRAME.flags =SpdyConnection.FLAG_HALF_CLOSE; 
    }

    public SpdyStream(SpdyConnection spdy) {
        this.spdy = spdy;
    }

    public void dump(PrintWriter out) {
        if (reqFrame != null) {
            out.println("Req: " + reqFrame);
        }
        if (resFrame != null) {
            out.println("Res: " + resFrame);
        }
        out.println("In: " + inData.size() + (finRcvd ? " FIN":""));
    }

    /**
     * Non-blocking, called when a data frame is received.
     *
     * The processor must consume the data, or set frame.data to null or a fresh
     * buffer ( to avoid a copy ).
     */
    public void onDataFrame(SpdyFrame inFrame) {
        synchronized(this) {
            inData.add(inFrame);
            if (inFrame.closed()) {
                finRcvd = true;
            }
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
        } else if (frame.type == SpdyConnection.TYPE_RST_STREAM) {
            onReset();
        }
        synchronized (this) {
            inData.add(frame);
            if (frame.isHalfClose()) {
                finRcvd = true;
            }            
        }
    }

    /** 
     * Called on GOAWAY or reset.
     */
    public void onReset() {
        finRcvd = true;
        finSent = true;
        
        // To unblock
        inData.add(END_FRAME);
    }
    
    /**
     * True if the channel both received and sent FIN frames.
     * 
     * This is tracked by the processor, to avoid extra storage in framer.
     */
    public boolean isFinished() {
        return finSent && finRcvd;
    }
    
    /**
     * Waits and return the next data frame.
     */
    public SpdyFrame getDataFrame(long to) throws IOException {
        while (true) {
            SpdyFrame res = getFrame(to);
            if (res == null || res.isData()) {
                return res;
            }
        }
    }
    
    /**
     * Waits and return the next frame. First frame will be the control frame
     */
    public SpdyFrame getFrame(long to) throws IOException {
        SpdyFrame in;
        try {
            synchronized (this) {
                if (inData.size() == 0 && finRcvd) {
                    return null;
                }                
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

    public SpdyFrame getResponse() {
        if (resFrame == null) {
            resFrame = spdy.getFrame(SpdyConnection.TYPE_SYN_REPLY);
            resFrame.streamId = reqFrame.streamId;
        }
        return resFrame;
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

        if (close)
            oframe.halfClose();

        oframe.append(data, start, length);
        spdy.send(oframe, this);
    }

    public void send() throws IOException {
        send("http", "GET");
    }

    public void send(String host, String url, String scheme, String method) throws IOException {
        getRequest().addHeader("host", host);
        getRequest().addHeader("url", url);

        send(scheme, method);
    }

    public void send(String scheme, String method) throws IOException {
        getRequest();
        if ("GET".equalsIgnoreCase(method)) {
            // TODO: add the others
            reqFrame.halfClose();
        }
        getRequest().addHeader("scheme", "http"); // todo
        getRequest().addHeader("method", method);
        getRequest().addHeader("version", "HTTP/1.1");
        if (reqFrame.isHalfClose()) {
            finSent = true;
        }
        spdy.send(reqFrame, this);
    }


}
