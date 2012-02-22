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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class implementing SPDY protocol. Works with both blocking and
 * non-blocking sockets. To simplify integration in various endpoints there is
 * no 'socket' layer/abstraction, but read/write abstract methods.
 * 
 * Because SPDY is multiplexing, a blocking socket needs a second thread to
 * handle writes ( the read thread may be blocked while a servlet is writing ).
 * The intended use of SPDY with blocking sockets is for frontend(load-balancer)
 * to tomcat, where each tomcat will have a few spdy connections.
 * 
 */
public abstract class SpdyConnection { // implements Runnable {

    // TODO: this can be pooled, to avoid allocation on idle connections
    // TODO: override socket timeout

    protected volatile SpdyFrame inFrame;

    protected CompressSupport compressSupport;

    // Fields stored for each spdy connection
    Map<Integer, SpdyStream> channels = new HashMap<Integer, SpdyStream>();

    // --------------
    protected static final Logger log = Logger.getLogger(SpdyConnection.class
            .getName());

    public static final int TYPE_SYN_STREAM = 1;

    public static final int TYPE_SYN_REPLY = 2;

    public static final int TYPE_RST_STREAM = 3;

    public static final int TYPE_SETTINGS = 4;

    public static final int TYPE_PING = 6;

    public static final int TYPE_GOAWAY = 7;

    public static final int TYPE_HEADERS = 8;

    public static final int TYPE_WINDOW = 8;

    public static String[] TYPES = { "SYN_STREAM", "SYN_REPLY", "RST_STREAM",
            "SETTINGS", "5", "PING", "GOAWAY", "HEADERS", "WINDOW_UPDATE" };

    static int FLAG_HALF_CLOSE = 1;

    public static String[] RST_ERRORS = {
            // This is a generic error, and should only be used if a more
            // specific error is not available.
            "PROTOCOL_ERROR", "INVALID_STREAM",
            // This is returned when a frame is received for a stream which is
            // not
            // active.
            "REFUSED_STREAM",
            // Indicates that the stream was refused before any processing has
            // been
            // done on the stream.
            "UNSUPPORTED_VERSION",
            // 4 Indicates that the recipient of a stream does not support the
            // SPDY version requested.
            "CANCEL",
            // 5 Used by the creator of a stream to indicate that the stream is
            // no longer needed.
            "FLOW_CONTROL_ERROR",
            // 6 The endpoint detected that its peer violated the flow control
            // protocol.
            "STREAM_IN_USE",
            // 7 The endpoint received a SYN_REPLY for a stream already open.
            "STREAM_ALREADY_CLOSED"
    // 8 The endpoint received a data or SYN_REPLY frame for a stream which
    // is half closed.
    };

    // protected SpdyFrame currentOutFrame = new SpdyFrame();

    protected SpdyContext spdyContext;

    protected boolean inClosed;

    int lastChannel;

    int outStreamId = 1;

    // TODO: finer handling of priorities
    LinkedList<SpdyFrame> prioriyQueue = new LinkedList<SpdyFrame>();

    LinkedList<SpdyFrame> outQueue = new LinkedList<SpdyFrame>();

    Lock framerLock = new ReentrantLock();

    // --------------

    public static byte[] NPN = "spdy/2".getBytes();

    private Condition outCondition;

    public SpdyConnection(SpdyContext spdyContext) {
        this.setSpdyContext(spdyContext);
        outCondition = framerLock.newCondition();
    }

    /**
     * Write.
     */
    public abstract int write(byte[] data, int off, int len) throws IOException;

    /**
     * Like read, but may return 0 if no data is available and the channel
     * supports polling.
     */
    public abstract int read(byte[] data, int off, int len) throws IOException;

    public void setCompressSupport(CompressSupport cs) {
        compressSupport = cs;
    }

    public SpdyFrame getFrame(int type) {
        SpdyFrame frame = getSpdyContext().getFrame();
        frame.c = true;
        frame.type = type;
        return frame;
    }

    public SpdyFrame getDataFrame() throws IOException {
        SpdyFrame frame = getSpdyContext().getFrame();
        return frame;
    }

    /*
     * Output requirements: - common case: sendFrame called from a thread ( like
     * servlets ), wants to be blocked anyways
     * 
     * - but also need to support 'non-blocking' mode ( ping )
     * 
     * - we need to queue frames when write would block, so we can prioritize.
     * 
     * - for fully non-blocking write: there will be a drain callback.
     */

    /**
     * Handles the out queue for blocking sockets.
     */
    SpdyFrame out;

    boolean draining = false;

    /**
     * Non blocking if the socket is not blocking.
     */
    private boolean drain() {
        while (true) {
            framerLock.lock();

            try {
                if (out == null) {
                    out = prioriyQueue.poll();
                    if (out == null) {
                        out = outQueue.poll();
                    }
                    if (out == null) {
                        return false;
                    }
                    SpdyFrame oframe = out;
                    try {
                        if (oframe.type == TYPE_SYN_STREAM) {
                            oframe.fixNV(18);
                            if (compressSupport != null) {
                                compressSupport.compress(oframe, 18);
                            }
                        } else if (oframe.type == TYPE_SYN_REPLY
                                || oframe.type == TYPE_HEADERS) {
                            oframe.fixNV(14);
                            if (compressSupport != null) {
                                compressSupport.compress(oframe, 14);
                            }
                        }
                    } catch (IOException ex) {
                        abort("Compress error");
                        return false;
                    }
                    if (oframe.type == TYPE_SYN_STREAM) {
                        oframe.streamId = outStreamId;
                        outStreamId += 2;
                        channels.put(oframe.streamId, oframe.stream);
                    }

                    oframe.serializeHead();

                }
                if (out.endData == out.off) {
                    out = null;
                    continue;
                }
            } finally {
                framerLock.unlock();
            }

            if (getSpdyContext().debug) {
                trace("> " + out);
            }

            try {
                int toWrite = out.endData - out.off;
                int wr = write(out.data, out.off, toWrite);
                if (wr < 0) {
                    return false;
                }
                if (wr < toWrite) {
                    out.off += wr;
                    return true; // non blocking connection
                }
                out.off += wr;
                // Frame was sent
                framerLock.lock();
                try {
                    outCondition.signalAll();
                } finally {
                    framerLock.unlock();
                }
                out = null;
            } catch (IOException e) {
                // connection closed - abort all streams
                e.printStackTrace();
                onClose();
                return false;
            }
        }
    }

    /**
     * Blocking call for sendFrame: must be called from a thread pool.
     * 
     * Will wait until the actual frame is sent.
     */
    public void sendFrameBlocking(SpdyFrame oframe, SpdyStream proc)
            throws IOException {
        queueFrame(oframe, proc, oframe.pri == 0 ? outQueue : prioriyQueue);

        nonBlockingDrain();

        while (!inClosed) {
            framerLock.lock();
            try {
                if (oframe.off == oframe.endData) {
                    return; // was sent
                }
                outCondition.await();
            } catch (InterruptedException e) {
            } finally {
                framerLock.unlock();
            }
        }
    }

    /**
     * Send as much as possible without blocking.
     * 
     * With a nb transport it should call drain directly.
     */
    public void nonBlockingDrain() {
        // TODO: if (nonBlocking()) { drain() }
        getSpdyContext().getExecutor().execute(nbDrain);
    }

    static int drainCnt = 0;

    Runnable nbDrain = new Runnable() {
        public void run() {
            int i = drainCnt++;
            long t0 = System.currentTimeMillis();
            synchronized (nbDrain) {
                if (draining) {
                    return;
                }
                draining = true;
            }

            drain();
            synchronized (nbDrain) {
                draining = false;
            }
        }
    };

    public void sendFrameNonBlocking(SpdyFrame oframe, SpdyStream proc)
            throws IOException {
        queueFrame(oframe, proc, oframe.pri == 0 ? outQueue : prioriyQueue);
        nonBlockingDrain();
    }

    private void queueFrame(SpdyFrame oframe, SpdyStream proc,
            LinkedList<SpdyFrame> outQueue) throws IOException {

        oframe.endData = oframe.off;
        oframe.off = 0;
        // We can't assing a stream ID until it is sent - priorities
        // we can't compress either - it's stateful.
        oframe.stream = proc;

        framerLock.lock();
        try {
            outQueue.add(oframe);
            outCondition.signalAll();
        } finally {
            framerLock.unlock();
        }
    }

    public void onClose() {
        // TODO: abort
    }

    private void trace(String s) {
        System.err.println(s);
    }

    public SpdyFrame inFrame() {
        return inFrame;
    }

    /**
     * Process a SPDY connection using a blocking socket.
     */
    public int onBlockingSocket() {
        try {
            if (getSpdyContext().debug) {
                trace("< onConnection() " + lastChannel);
            }
            int rc = processInput();

            if (getSpdyContext().debug) {
                trace("< onConnection() " + rc + " " + lastChannel);
            }
            return rc;
        } catch (Throwable t) {
            t.printStackTrace();
            trace("< onData-ERROR() " + lastChannel);
            abort("Error processing socket" + t);
            return CLOSE;
        }
    }

    public static final int LONG = 1;

    public static final int CLOSE = -1;

    private SpdyFrame nextFrame;

    /**
     * Non-blocking method, read as much as possible and return.
     */
    public int processInput() throws IOException {
        while (true) {
            if (inFrame == null) {
                inFrame = getSpdyContext().getFrame();
            }

            if (inFrame.data == null) {
                inFrame.data = new byte[16 * 1024];
            }
            // we might already have data from previous frame
            if (inFrame.endData < 8 || // we don't have the header
                    inFrame.endData < inFrame.endFrame) { // size != 0 - we
                                                          // parsed the header

                int rd = read(inFrame.data, inFrame.endData,
                        inFrame.data.length - inFrame.endData);
                if (rd < 0) {
                    abort("Closed");
                    return CLOSE;
                }
                if (rd == 0) {
                    return LONG;
                    // Non-blocking channel - will resume reading at off
                }
                inFrame.endData += rd;
            }
            if (inFrame.endData < 8) {
                continue; // keep reading
            }
            // We got the frame head
            if (inFrame.endFrame == 0) {
                inFrame.parse();
                if (inFrame.version != 2) {
                    abort("Wrong version");
                    return CLOSE;
                }

                // MAx_FRAME_SIZE
                if (inFrame.endFrame < 0 || inFrame.endFrame > 32000) {
                    abort("Framing error, size = " + inFrame.endFrame);
                    return CLOSE;
                }

                // grow the buffer if needed. no need to copy the head, parsed
                // ( maybe for debugging ).
                if (inFrame.data.length < inFrame.endFrame) {
                    inFrame.data = new byte[inFrame.endFrame];
                }
            }

            if (inFrame.endData < inFrame.endFrame) {
                continue; // keep reading to fill current frame
            }
            // else: we have at least the current frame
            int extra = inFrame.endData - inFrame.endFrame;
            if (extra > 0) {
                // and a bit more - to keep things simple for now we
                // copy them to next frame, at least we saved reads.
                // it is possible to avoid copy - but later.
                nextFrame = getSpdyContext().getFrame();
                nextFrame.makeSpace(extra);
                System.arraycopy(inFrame.data, inFrame.endFrame,
                        nextFrame.data, 0, extra);
                nextFrame.endData = extra;
                inFrame.endData = inFrame.endFrame;
            }

            // decompress
            if (inFrame.type == TYPE_SYN_STREAM) {
                inFrame.streamId = inFrame.readInt(); // 4
                lastChannel = inFrame.streamId;
                inFrame.associated = inFrame.readInt(); // 8
                inFrame.pri = inFrame.read16(); // 10 pri and unused
                if (compressSupport != null) {
                    compressSupport.decompress(inFrame, 18);
                }
                inFrame.nvCount = inFrame.read16();

            } else if (inFrame.type == TYPE_SYN_REPLY
                    || inFrame.type == TYPE_HEADERS) {
                inFrame.streamId = inFrame.readInt(); // 4
                inFrame.read16();
                if (compressSupport != null) {
                    compressSupport.decompress(inFrame, 14);
                }
                inFrame.nvCount = inFrame.read16();
            }

            if (getSpdyContext().debug) {
                trace("< " + inFrame);
            }

            try {
                int state = handleFrame();
                if (state == CLOSE) {
                    return state;
                }
            } catch (Throwable t) {
                abort("Error handling frame");
                t.printStackTrace();
                return CLOSE;
            }

            if (inFrame != null) {
                inFrame.recyle();
                if (nextFrame != null) {
                    getSpdyContext().releaseFrame(inFrame);
                    inFrame = nextFrame;
                    nextFrame = null;
                }
            } else {
                inFrame = nextFrame;
                nextFrame = null;
                if (inFrame == null) {
                    inFrame = getSpdyContext().getFrame();
                }
            }
        }
    }

    // Framing error or shutdown- close all streams.
    public void abort(String msg) {
        System.err.println(msg);
        inClosed = true;
        // TODO: close all streams

    }

    /**
     * Process a SPDY connection. Called in a separate thread.
     * 
     * @return
     * @throws IOException
     */
    public int handleFrame() throws IOException {
        if (inFrame.c) {
            switch (inFrame.type) {
            case TYPE_SETTINGS: {
                int cnt = inFrame.readInt();
                for (int i = 0; i < cnt; i++) {
                    int flag = inFrame.readByte();
                    int id = inFrame.read24();
                    int value = inFrame.readInt();
                }
                break;
                // receivedHello = currentInFrame;
            }
            case TYPE_GOAWAY: {
                int lastStream = inFrame.readInt();
                log.info("GOAWAY last=" + lastStream);
                abort("GOAWAY");
                return CLOSE;
            }
            case TYPE_RST_STREAM: {
                inFrame.streamId = inFrame.read32();
                int errCode = inFrame.read32();
                trace("> RST "
                        + inFrame.streamId
                        + " "
                        + ((errCode < RST_ERRORS.length) ? RST_ERRORS[errCode]
                                : errCode));
                SpdyStream sch = channels.get(inFrame.streamId);
                if (sch == null) {
                    abort("Missing channel " + inFrame.streamId);
                    return CLOSE;
                }
                sch.onCtlFrame(inFrame);
                inFrame = null;
                break;
            }
            case TYPE_SYN_STREAM: {

                SpdyStream ch = getSpdyContext().getStream(this);

                synchronized (channels) {
                    channels.put(inFrame.streamId, ch);
                }

                try {
                    ch.onCtlFrame(inFrame);
                    inFrame = null;
                } catch (Throwable t) {
                    log.log(Level.SEVERE, "Error parsing head SYN_STREAM", t);
                    abort("Error reading headers " + t);
                    return CLOSE;
                }
                spdyContext.onSynStream(this, ch);
                break;
            }
            case TYPE_SYN_REPLY: {
                SpdyStream sch = channels.get(inFrame.streamId);
                if (sch == null) {
                    abort("Missing channel");
                    return CLOSE;
                }
                try {
                    sch.onCtlFrame(inFrame);
                    inFrame = null;
                } catch (Throwable t) {
                    log.info("Error parsing head SYN_STREAM" + t);
                    abort("Error reading headers " + t);
                    return CLOSE;
                }
                break;
            }
            case TYPE_PING: {

                SpdyFrame oframe = getSpdyContext().getFrame();
                oframe.type = TYPE_PING;
                oframe.c = true;

                oframe.append32(inFrame.read32());
                oframe.pri = 0x80;

                sendFrameNonBlocking(oframe, null);
                break;
            }
            }
        } else {
            // Data frame
            SpdyStream sch = channels.get(inFrame.streamId);
            if (sch == null) {
                abort("Missing channel");
                return CLOSE;
            }
            sch.onDataFrame(inFrame);
            inFrame = null;
        }
        return LONG;
    }

    public SpdyContext getSpdyContext() {
        return spdyContext;
    }

    public void setSpdyContext(SpdyContext spdyContext) {
        this.spdyContext = spdyContext;
    }
    
    public SpdyStream get(String host, String url) throws IOException {
        SpdyStream sch = new SpdyStream(this);
        sch.addHeader("host", host);
        sch.addHeader("url", url);

        sch.send();

        return sch;
    }

    /**
     * Abstract compression support. When using spdy on intranet ( between load
     * balancer and tomcat) there is no need for the compression overhead. There
     * are also multiple possible implementations.
     */
    public static interface CompressSupport {
        public void compress(SpdyFrame frame, int start) throws IOException;

        public void decompress(SpdyFrame frame, int start) throws IOException;
    }
}
