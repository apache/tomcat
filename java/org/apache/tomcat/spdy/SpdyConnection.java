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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

    private volatile SpdyFrame inFrame;

    private CompressSupport compressSupport;

    // Fields stored for each spdy connection
    private final Map<Integer, SpdyStream> channels = new HashMap<>();

    // --------------
    private static final Logger log = Logger.getLogger(SpdyConnection.class
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

    static final int FLAG_HALF_CLOSE = 1;

    private static final String[] RST_ERRORS = {
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

    protected final SpdyContext spdyContext;

    protected boolean inClosed;

    private int lastChannel;

    private int outStreamId = 1;

    // TODO: finer handling of priorities
    private final LinkedList<SpdyFrame> prioriyQueue = new LinkedList<>();

    private final LinkedList<SpdyFrame> outQueue = new LinkedList<>();

    // --------------

    public static final int LONG = 1;

    public static final int CLOSE = -1;

    private SpdyFrame nextFrame;

    /**
     * Handles the out queue for blocking sockets.
     */
    private SpdyFrame out;

    private int goAway = Integer.MAX_VALUE;

    public SpdyConnection(SpdyContext spdyContext) {
        this.spdyContext = spdyContext;
        if (spdyContext.compression) {
            setCompressSupport(CompressDeflater6.get());
        }
    }

    @Override
    public String toString() {
        return "SpdyCon open=" + channels.size() + " " + lastChannel;
    }

    public void dump(PrintWriter out) {
        out.println("SpdyConnection open=" + channels.size() +
                " outQ:" + outQueue.size());
        for (SpdyStream str: channels.values()) {
            str.dump(out);
        }

        out.println();

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

    public abstract void close() throws IOException;

    public void setCompressSupport(CompressSupport cs) {
        compressSupport = cs;
    }

    public SpdyFrame getFrame(int type) {
        SpdyFrame frame = getSpdyContext().getFrame();
        frame.c = true;
        frame.type = type;
        return frame;
    }

    public SpdyFrame getDataFrame() {
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

    public void drain() {
        synchronized (nbDrain) {
            _drain();
        }
    }

    /**
     * Non blocking if the socket is not blocking.
     */
    private boolean _drain() {
        while (true) {
            synchronized (outQueue) {
                if (out == null) {
                    out = prioriyQueue.poll();
                    if (out == null) {
                        out = outQueue.poll();
                    }
                    if (out == null) {
                        return false;
                    }
                    if (goAway < out.streamId) {
                        // TODO
                    }
                    try {
                        if (!out.c) {
                            // late: IDs are assigned as we send ( priorities may affect
                            // the transmission order )
                            if (out.stream != null) {
                                out.streamId = out.stream.getRequest().streamId;
                            }
                        } else if (out.type == TYPE_SYN_STREAM) {
                            out.fixNV(18);
                            if (compressSupport != null) {
                                compressSupport.compress(out, 18);
                            }
                        } else if (out.type == TYPE_SYN_REPLY
                                || out.type == TYPE_HEADERS) {
                            out.fixNV(14);
                            if (compressSupport != null) {
                                compressSupport.compress(out, 14);
                            }
                        }
                    } catch (IOException ex) {
                        abort("Compress error");
                        return false;
                    }
                    if (out.type == TYPE_SYN_STREAM) {
                        out.streamId = outStreamId;
                        outStreamId += 2;
                        synchronized(channels) {
                            channels.put(Integer.valueOf(out.streamId),
                                    out.stream);
                        }
                    }

                    out.serializeHead();

                }
                if (out.endData == out.off) {
                    out = null;
                    continue;
                }
            }

            if (SpdyContext.debug) {
                trace("> " + out);
            }

            try {
                int toWrite = out.endData - out.off;
                int wr;
                while (toWrite > 0) {
                    wr = write(out.data, out.off, toWrite);
                    if (wr < 0) {
                        return false;
                    }
                    if (wr == 0) {
                        return true; // non blocking or to
                    }
                    if (wr <= toWrite) {
                        out.off += wr;
                        toWrite -= wr;
                    }
                }

                synchronized (channels) {
                    if (out.stream != null) {
                        if (out.isHalfClose()) {
                            out.stream.finSent = true;
                        }
                        if (out.stream.finRcvd && out.stream.finSent) {
                            channels.remove(Integer.valueOf(out.streamId));
                        }
                    }
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
     * Send as much as possible without blocking.
     *
     * With a nb transport it should call drain directly.
     */
    public void nonBlockingSend(SpdyFrame oframe, SpdyStream proc) {
        queueFrame(oframe, proc, oframe.pri == 0 ? outQueue : prioriyQueue);
        getSpdyContext().getExecutor().execute(nbDrain);
    }

    private final Runnable nbDrain = new Runnable() {
        @Override
        public void run() {
            drain();
        }
    };

    /**
     * Add the frame to the queue and send until the queue is empty.
     *
     */
    public void send(SpdyFrame oframe, SpdyStream proc) {
        queueFrame(oframe, proc, oframe.pri == 0 ? outQueue : prioriyQueue);
        drain();
    }

    private void queueFrame(SpdyFrame oframe, SpdyStream proc,
            LinkedList<SpdyFrame> queue) {

        oframe.endData = oframe.off;
        oframe.off = 0;
        // We can't assing a stream ID until it is sent - priorities
        // we can't compress either - it's stateful.
        oframe.stream = proc;

        // all sync for adding/removing is on outQueue
        synchronized (outQueue) {
            queue.add(oframe);
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
            if (SpdyContext.debug) {
                trace("< onConnection() " + lastChannel);
            }
            int rc = processInput();

            if (SpdyContext.debug) {
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
            if (inFrame.endReadData < 8 || // we don't have the header
                    inFrame.endReadData < inFrame.endData) {

                int rd = read(inFrame.data, inFrame.endReadData,
                        inFrame.data.length - inFrame.endReadData);
                if (rd == -1) {
                    if (channels.size() == 0) {
                        return CLOSE;
                    } else {
                        abort("Closed");
                    }
                } else if (rd < 0) {
                    abort("Closed - read error");
                    return CLOSE;
                } else if (rd == 0) {
                    return LONG;
                    // Non-blocking channel - will resume reading at off
                }
                inFrame.endReadData += rd;
            }
            if (inFrame.endReadData < 8) {
                continue; // keep reading
            }
            if (inFrame.endData == 0) {
                inFrame.parse();
                if (inFrame.version != 2) {
                    abort("Wrong version");
                    return CLOSE;
                }

                // MAX_FRAME_SIZE
                if (inFrame.endData < 0 || inFrame.endData > 32000) {
                    abort("Framing error, size = " + inFrame.endData);
                    return CLOSE;
                }

                // TODO: if data, split it in 2 frames
                // grow the buffer if needed.
                if (inFrame.data.length < inFrame.endData) {
                    byte[] tmp = new byte[inFrame.endData];
                    System.arraycopy(inFrame.data, 0, tmp, 0, inFrame.endReadData);
                    inFrame.data = tmp;
                }
            }

            if (inFrame.endReadData < inFrame.endData) {
                continue; // keep reading to fill current frame
            }
            // else: we have at least the current frame
            int extra = inFrame.endReadData - inFrame.endData;
            if (extra > 0) {
                // and a bit more - to keep things simple for now we
                // copy them to next frame, at least we saved reads.
                // it is possible to avoid copy - but later.
                nextFrame = getSpdyContext().getFrame();
                nextFrame.makeSpace(extra);
                System.arraycopy(inFrame.data, inFrame.endData,
                        nextFrame.data, 0, extra);
                nextFrame.endReadData = extra;
                inFrame.endReadData = inFrame.endData;
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

            if (SpdyContext.debug) {
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

        List<Integer> ch = new ArrayList<>(channels.keySet());
        for (Integer i: ch) {
            SpdyStream stream = channels.remove(i);
            if (stream != null) {
                stream.onReset();
            }
        }
    }

    public void abort(String msg, int last) {
        System.err.println(msg);
        inClosed = true;

        List<Integer> ch = new ArrayList<>(channels.keySet());
        for (Integer i: ch) {
            if (i.intValue() > last) {
                SpdyStream stream = channels.remove(i);
                if (stream != null) {
                    stream.onReset();
                }
            }
        }
    }

    /**
     * Process a SPDY connection. Called in the input thread, should not
     * block.
     *
     * @throws IOException
     */
    protected int handleFrame() throws IOException {
        if (inFrame.c) {
            switch (inFrame.type) {
            case TYPE_SETTINGS: {
                int cnt = inFrame.readInt();
                for (int i = 0; i < cnt; i++) {
                    inFrame.readByte();
                    inFrame.read24();
                    inFrame.readInt();
                }
                // TODO: save/interpret settings
                break;
            }
            case TYPE_GOAWAY: {
                int lastStream = inFrame.readInt();
                log.info("GOAWAY last=" + lastStream);

                // Server will shut down - but will keep processing the current requests,
                // up to lastStream. If we sent any new ones - they need to be canceled.
                abort("GO_AWAY", lastStream);
                goAway  = lastStream;
                return CLOSE;
            }
            case TYPE_RST_STREAM: {
                inFrame.streamId = inFrame.read32();
                int errCode = inFrame.read32();
                if (SpdyContext.debug) {
                    trace("> RST "
                            + inFrame.streamId
                            + " "
                            + ((errCode < RST_ERRORS.length) ? RST_ERRORS[errCode]
                                    : Integer.valueOf(errCode)));
                }
                SpdyStream sch;
                synchronized(channels) {
                        sch = channels.remove(
                                Integer.valueOf(inFrame.streamId));
                }
                // if RST stream is for a closed channel - we can ignore.
                if (sch != null) {
                    sch.onReset();
                }

                inFrame = null;
                break;
            }
            case TYPE_SYN_STREAM: {

                SpdyStream ch = getSpdyContext().getStream(this);

                synchronized (channels) {
                    channels.put(Integer.valueOf(inFrame.streamId), ch);
                }

                try {
                    ch.onCtlFrame(inFrame);
                    inFrame = null;
                } catch (Throwable t) {
                    log.log(Level.SEVERE, "Error parsing head SYN_STREAM", t);
                    abort("Error reading headers " + t);
                    return CLOSE;
                }
                spdyContext.onStream(this, ch);
                break;
            }
            case TYPE_SYN_REPLY: {
                SpdyStream sch;
                synchronized(channels) {
                    sch = channels.get(Integer.valueOf(inFrame.streamId));
                }
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

                send(oframe, null);
                break;
            }
            }
        } else {
            // Data frame
            SpdyStream sch;
            synchronized (channels) {
                sch = channels.get(Integer.valueOf(inFrame.streamId));
            }
            if (sch == null) {
                abort("Missing channel");
                return CLOSE;
            }
            sch.onDataFrame(inFrame);
            synchronized (channels) {
                if (sch.finRcvd && sch.finSent) {
                    channels.remove(Integer.valueOf(inFrame.streamId));
                }
            }
            inFrame = null;
        }
        return LONG;
    }

    public SpdyContext getSpdyContext() {
        return spdyContext;
    }

    public SpdyStream get(String host, String url) {
        SpdyStream sch = new SpdyStream(this);
        sch.getRequest().addHeader("host", host);
        sch.getRequest().addHeader("url", url);

        sch.send();

        return sch;
    }

    /**
     * Abstract compression support. When using spdy on intranet ( between load
     * balancer and tomcat) there is no need for the compression overhead. There
     * are also multiple possible implementations.
     */
    static interface CompressSupport {
        public void compress(SpdyFrame frame, int start) throws IOException;

        public void decompress(SpdyFrame frame, int start) throws IOException;
    }
}
