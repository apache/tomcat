/*  Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpConnector.HttpConnection;
import org.apache.tomcat.lite.io.BBucket;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.FutureCallbacks;
import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;

/**
 * HTTP async client and server, based on tomcat NIO/APR connectors
 *
 * 'Input', 'read', 'Recv' refers to information we get from the remote side -
 * the request body for server-mode or response body for client.
 *
 * 'Output', 'write', 'Send' is for info we send - the post in client mode
 * and the response body for server mode.
 *
 * @author Costin Manolache
 */
public class HttpChannel extends IOChannel {

    static final int HEADER_SIZE = 8192;

    static AtomicInteger serCnt = new AtomicInteger();

    public static final String CONTENT_LENGTH= "Content-Length";

    public static final String HTTP_10 = "HTTP/1.0";

    public static final String HTTP_11 = "HTTP/1.1";

    /**
     * SEMI_COLON.
     */
    public static final byte SEMI_COLON = (byte) ';';

    public static final byte QUESTION = (byte) '?';


    protected static Logger log = Logger.getLogger("HttpChannel");


    boolean debug = false;

    // ---- Callbacks and locks

    FutureCallbacks<HttpChannel> doneLock = new FutureCallbacks<HttpChannel>();
    FutureCallbacks<HttpChannel> headersReceivedLock =
            new FutureCallbacks<HttpChannel>();
    /**
     * Called when the incoming headers have been received.
     * ( response for client mode, request for server mode )
     * @throws IOException
     */
    protected HttpService httpService;
    /**
     * Called when:
     *  - body sent
     *  - body received
     *  - release() called - either service() done or client done with the
     *  buffers.
     *
     *  After this callback:
     *  - socket closed if closeOnEndSend, or put in keep-alive
     *  - AsyncHttp.recycle()
     *  - returned to the pool.
     */
    private RequestCompleted doneAllCallback;
    protected boolean sendReceiveDone = false;

    // Will be signalled (open) when the buffer is empty.
    FutureCallbacks<IOChannel> flushLock = new FutureCallbacks<IOChannel>();

    FutureCallbacks<HttpChannel> doneFuture;
    boolean doneCallbackCalled = false;


    // ----------

    // Set if Exect: 100-continue was set on reqest.
    // If this is the case - body won't be sent until
    // server responds ( client ) and server will only
    // read body after ack() - or skip to next request
    // without swallowing the body.
    protected boolean expectation = false;

    /** Ready for recycle, if send/receive are done */
    protected boolean release = false;

    // -----------

    protected boolean headersDone = false;
    protected boolean error = false;
    protected boolean abortDone = false;


    protected int ser; // id - for jmx registration and logs
    protected int channelId;

    /**
     * Null after endSendReceive and before sending the request
     */
    HttpConnection conn;

    HttpConnector httpConnector;

    // Different ways to point to request response (server/client)
    HttpRequest httpReq;
    HttpResponse httpRes;
    HttpMessage inMessage;
    HttpMessage outMessage;
    // receive can be for request ( server mode ) or response ( client )
    IOBuffer receiveBody = new IOBuffer();

    // notify us that user called close()
    IOBuffer sendBody = new IOBuffer() {
        public void close() throws IOException {
            if (isAppendClosed()) {
                return;
            }
            super.close();
            outClosed();
        }
    };


    // Server side only
    protected String serverHeader = "TomcatLite";

    long ioTimeout = 30 * 60000; // 30 min seems high enough


    public HttpChannel() {
        ser = serCnt.incrementAndGet();
        httpReq = new HttpRequest(this);
        httpRes = new HttpResponse(this);
        init();
        serverMode(false);
    }

    /**
     * Close the connection, return to pool. Called if a
     * framing error happens, or if we want to force the connection
     * to close, without waiting for all data to be sent/received.
     * @param t
     *
     * @throws IOException
     */
    public void abort(Throwable t) {
        abort(t.toString());
    }

    public void abort(String t)  {
        synchronized (this) {
            if (abortDone) {
                return;
            }
            abortDone = true;
        }
        try {
            checkRelease();
            trace("abort " + t);
            if (conn != null) {
                conn.abort(this, t);
            }
            inMessage.state = HttpMessage.State.DONE;
            outMessage.state = HttpMessage.State.DONE;
            sendReceiveDone = true;
            error = true;
            handleEndSendReceive();
        } catch (Throwable ex) {
            log.severe("Exception in abort " + ex);
        }
    }

    /**
     * If release was called - throw exception, you shouldn't use
     * the object again.
     * @throws IOException
     */
    private void checkRelease() throws IOException {
        if (release && sendReceiveDone) {
            throw new IOException("Object released");
        }
    }

    public IOChannel getSink() {
        if (conn == null) {
            return null;
        }
        return conn.getSink();
    }


    /**
     * Called when the request is done. Need to send remaining byte.
     *
     */
    public void complete() throws IOException {
        checkRelease();
        if (!getOut().isAppendClosed()) {
            getOut().close();
        }
        if (!getIn().isAppendClosed()) {
            getIn().close();
        }

        startSending();
   }

    public int doRead(BBuffer chunk)
            throws IOException {
        checkRelease();
        BBucket next = null;
        while (true) {
            getIn().waitData(0);
            next = (BBucket) getIn().popFirst();
            if (next != null) {
                break;
            } else if (getIn().isAppendClosed()) {
                return -1;
            } else {
                System.err.println("Spurious waitData signal, no data");
            }
        }
        chunk.append(next.array(), next.position(), next.remaining());
        int read =  next.remaining();
        next.release();
        return read;
    }

    public HttpConnector getConnector() {
        return httpConnector;
    }

    public boolean getError() {
        return error;
    }

    // ---------------- Writting -------------------------------

    public String getId() {
        return Integer.toString(ser);
    }

    public IOBuffer getIn() {
        return receiveBody;
    }


    public long getIOTimeout() {
        return ioTimeout;
    }

    // TODO: replace with getSocketChannel - used for remote addr, etc
    public IOChannel getNet() {
        if (conn == null) {
            return null;
        }
        return conn.getSink();
    }


    public IOBuffer getOut() {
        return sendBody;
    }

    public HttpRequest getRequest() {
        return httpReq;
    }


    public HttpResponse getResponse() {
        return httpRes;
    }


    public String getState() {
        return
            conn +
            "RCV=[" + inMessage.state.toString() + " " +
            receiveBody.toString()
            + "] SND=[" + outMessage.state.toString()
            + " " + sendBody.toString() + "]";
    }


    public String getStatus() {
        return getResponse().getStatus() + " " + getResponse().getMessage();
    }


    public String getTarget() {
        if (target == null) {
            return ":0"; // server mode ?
        }
        return target.toString();
    }


    /**
     * Called from IO thread, after the request body
     * is completed ( or if there is no req body )
     * @throws IOException
     */
    protected void handleEndReceive() throws IOException {
        if (inMessage.state == HttpMessage.State.DONE) {
            return;
        }
        if (debug) {
            trace("END_RECV");
        }
        getIn().close();

        inMessage.state = HttpMessage.State.DONE;
        handleEndSendReceive();
    }

    /*
     * Called when sending, receiving and processing is done.
     * Can be called:
     *  - from IO thread, if this is a result of a read/write event that
     *  finished the send/recev pair.
     *  - from an arbitrary thread, if read was complete and the last write
     *  was a success and done in that thread ( write is not bound to IO thr)
     *
     */
    protected void handleEndSendReceive() throws IOException {
        // make sure the callback was called ( needed for abort )
        handleHeadersReceived(inMessage);

        this.doneLock.signal(this);
        synchronized (this) {
            if (doneCallbackCalled) {
                return;
            }
            if (outMessage.state != HttpMessage.State.DONE ||
                    inMessage.state != HttpMessage.State.DONE) {
                return;
            }
            doneCallbackCalled = true;
        }

        getIn().close();

        if (doneAllCallback != null) {
            doneAllCallback.handle(this, error ? new Throwable() : null);
        }

        if (conn != null) {
            conn.endSendReceive(this);
        }

        conn = null;

        if (debug) {
            trace("END_SEND_RECEIVE"
                    + (release ? " REL" : ""));
        }

        synchronized(this) {
            sendReceiveDone = true;
            maybeRelease();
        }
    }

    /**
     * called from IO thread OR servlet thread when last block has been sent.
     * If not using the socket ( net.getOut().flushCallback ) - this must
     * be called explicitely after flushing the body.
     */
    void handleEndSent() throws IOException {
        if (outMessage.state == HttpMessage.State.DONE) {
            // Only once.
            if (debug) {
                trace("Duplicate END SEND");
            }
            return;
        }
        outMessage.state = HttpMessage.State.DONE;

        getOut().close();

        // Make sure the send/receive callback is called once
        if (debug) {
            trace("END_SEND");
        }
        handleEndSendReceive();
    }

    // ----- End Selector thread callbacks ----
    public void handleError(String type) {
        System.err.println("Error " + type + " " + outMessage.state);
    }

    void handleHeadersReceived(HttpMessage in) throws IOException {
        if (!headersDone) {
            headersDone = true;
            headersReceivedLock.signal(this);
            if (httpService != null) {
                try {
                    httpService.service(getRequest(), getResponse());
                } catch (Throwable t) {
                    t.printStackTrace();
                    abort(t);
                }
            }
        }
    }


    private void init() {
        headersDone = false;
        sendReceiveDone = false;

        receiveBody.recycle();
        sendBody.recycle();
        expectation = false;

        error = false;
        abortDone = false;


        getRequest().recycle();
        getResponse().recycle();
        target = null;

        doneLock.recycle();
        headersReceivedLock.recycle();
        flushLock.recycle();

        doneCallbackCalled = false;
        // Will be set again after pool
        setHttpService(null);
        doneAllCallback = null;
        release = false;
    }

    public boolean isDone() {
        return outMessage.state == HttpMessage.State.DONE && inMessage.state == HttpMessage.State.DONE;
    }

    /**
     * Called when all done:
     *  - service finished ( endService was called )
     *  - output written
     *  - input read
     *
     * or by abort().
     *
     * @throws IOException
     */
    private void maybeRelease() throws IOException {
        synchronized (this) {
            if (release && sendReceiveDone) {
                if (debug) {
                    trace("RELEASE");
                }
                if (getConnector() != null) {
                    getConnector().returnToPool(this);
                } else {
                    log.severe("Attempt to release with no pool");
                }
            }
        }
    }


    /*
    The field-content does not include any leading or trailing LWS:
    linear white space occurring before the first non-whitespace
    character of the field-value or after the last non-whitespace
     character of the field-value. Such leading or trailing LWS MAY
     be removed without changing the semantics of the field value.
     Any LWS that occurs between field-content MAY be replaced with
     a single Http11Parser.SP before interpreting the field value or forwarding
     the message downstream.
     */
    int normalizeHeader(BBuffer value) {
        byte[] buf = value.array();
        int cstart = value.position();
        int end = value.limit();

        int realPos = cstart;
        int lastChar = cstart;
        byte chr = 0;
        boolean gotSpace = true;

        for (int i = cstart; i < end; i++) {
            chr = buf[i];
            if (chr == BBuffer.CR) {
                // skip
            } else if(chr == BBuffer.LF) {
                // skip
            } else if (chr == BBuffer.SP || chr == BBuffer.HT) {
                if (gotSpace) {
                    // skip
                } else {
                    buf[realPos++] = BBuffer.SP;
                    gotSpace = true;
                }
            } else {
                buf[realPos++] = chr;
                lastChar = realPos; // to skip trailing spaces
                gotSpace = false;
            }
        }
        realPos = lastChar;

        // so buffer is clean
        for (int i = realPos; i < end; i++) {
            buf[i] = BBuffer.SP;
        }
        value.setEnd(realPos);
        return realPos;
    }


    protected void recycle() {
        if (debug) {
            trace("RECYCLE");
        }
        init();
    }

    /**
     * Finalize sending and receiving.
     * Indicates client is no longer interested, some IO may still be in flight.
     * If in a POST and you're not interested in the body - it may be
     * better to call abort().
     *
     * MUST be called to allow connection reuse and pooling.
     *
     * @throws IOException
     */
    public void release() throws IOException {
        synchronized(this) {
            if (release) {
                return;
            }
            trace("RELEASE");
            release = true;
            // If send/receive is done - we can reuse this object
            maybeRelease();
        }
    }

    public void send() throws IOException {
        checkRelease();
        if (httpReq == inMessage) {
            conn.sendResponseHeaders(this);
        } else {
            if (getRequest().isCommitted()) {
                return;
            }
            getRequest().setCommitted(true);

            outMessage.state = HttpMessage.State.HEAD;

            getConnector().connectAndSend(this);
        }
    }

    /** Called when the outgoing stream is closed:
     * - by an explicit call to close()
     * - when all content has been sent.
     */
    protected void outClosed() throws IOException {
        if (conn != null) {
            conn.outClosed(this);
        }
    }

    public HttpChannel serverMode(boolean enabled) {
        if (enabled) {
            httpReq.setBody(receiveBody);
            httpRes.setBody(sendBody);
            inMessage = httpReq;
            outMessage = httpRes;
        } else {
            httpReq.setBody(sendBody);
            httpRes.setBody(receiveBody);
            inMessage = httpRes;
            outMessage = httpReq;
        }
        if (debug) {
        }
        return this;
    }

    public void setCompletedCallback(RequestCompleted doneAllCallback)
            throws IOException {
        this.doneAllCallback = doneAllCallback;
        synchronized (this) {
            if (doneCallbackCalled) {
                return;
            }
            if (outMessage.state != HttpMessage.State.DONE || inMessage.state != HttpMessage.State.DONE) {
                return;
            }
        }
        doneCallbackCalled = true;
        if (doneAllCallback != null) {
            doneAllCallback.handle(this, error ? new Throwable() : null);
        }
    }

    public void setConnector(HttpConnector pool) {
        this.httpConnector = pool;
    }

    public void setHttpService(HttpService headersReceivedCallback) {
        this.httpService = headersReceivedCallback;
    }

    public void setIOTimeout(long timeout) {
        ioTimeout = timeout;
    }


    public void setTarget(String host) {
        this.target = host;
    }

    public void startSending() throws IOException {
        checkRelease();
        if (conn != null) {
            conn.startSending(this);
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("id=").append(ser)
            .append(",rs=").append(getState())
            .append(")");
        return sb.toString();
    }


    void trace(String msg) {
        if(debug) {
            log.info(this.toString() + " " + msg + " done=" + doneCallbackCalled);
        }
    }

    @Override
    public void waitFlush(long timeMs) throws IOException {
        if (getOut().getBufferCount() == 0) {
            return;
        }
        flushLock.waitSignal(timeMs);
    }

    public HttpChannel setConnection(HttpConnection conn) {
        this.conn = conn;
        return this;
    }

    /**
     * Normalize URI.
     * <p>
     * This method normalizes "\", "//", "/./" and "/../". This method will
     * return false when trying to go above the root, or if the URI contains
     * a null byte.
     *
     * @param uriMB URI to be normalized, will be modified
     */
    public static boolean normalize(BBuffer uriBC) {

        byte[] b = uriBC.array();
        int start = uriBC.getStart();
        int end = uriBC.getEnd();

        // URL * is acceptable
        if ((end - start == 1) && b[start] == (byte) '*')
            return true;

        if (b[start] != '/') {
            // TODO: http://.... URLs
            return true;
        }

        int pos = 0;
        int index = 0;

        // Replace '\' with '/'
        // Check for null byte
        for (pos = start; pos < end; pos++) {
            if (b[pos] == (byte) '\\')
                b[pos] = (byte) '/';
            if (b[pos] == (byte) 0)
                return false;
        }

        // The URL must start with '/'
        if (b[start] != (byte) '/') {
            return false;
        }

        // Replace "//" with "/"
        for (pos = start; pos < (end - 1); pos++) {
            if (b[pos] == (byte) '/') {
                while ((pos + 1 < end) && (b[pos + 1] == (byte) '/')) {
                    copyBytes(b, pos, pos + 1, end - pos - 1);
                    end--;
                }
            }
        }

        // If the URI ends with "/." or "/..", then we append an extra "/"
        // Note: It is possible to extend the URI by 1 without any side effect
        // as the next character is a non-significant WS.
        if (((end - start) >= 2) && (b[end - 1] == (byte) '.')) {
            if ((b[end - 2] == (byte) '/')
                    || ((b[end - 2] == (byte) '.')
                            && (b[end - 3] == (byte) '/'))) {
                b[end] = (byte) '/';
                end++;
            }
        }

        uriBC.setEnd(end);

        index = 0;

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            index = uriBC.indexOf("/./", 0, 3, index);
            if (index < 0)
                break;
            copyBytes(b, start + index, start + index + 2,
                    end - start - index - 2);
            end = end - 2;
            uriBC.setEnd(end);
        }

        index = 0;

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            index = uriBC.indexOf("/../", 0, 4, index);
            if (index < 0)
                break;
            // Prevent from going outside our context
            if (index == 0)
                return false;
            int index2 = -1;
            for (pos = start + index - 1; (pos >= 0) && (index2 < 0); pos --) {
                if (b[pos] == (byte) '/') {
                    index2 = pos;
                }
            }
            copyBytes(b, start + index2, start + index + 3,
                    end - start - index - 3);
            end = end + index2 - index - 3;
            uriBC.setEnd(end);
            index = index2;
        }

        //uriBC.setBytes(b, start, end);
        uriBC.setEnd(end);
        return true;

    }

    /**
     * Copy an array of bytes to a different position. Used during
     * normalization.
     */
    private static void copyBytes(byte[] b, int dest, int src, int len) {
        for (int pos = 0; pos < len; pos++) {
            b[pos + dest] = b[pos + src];
        }
    }


    /**
     * This method will be called when the http headers have been received -
     * the body may or may not be available.
     *
     * In server mode this is equivalent with a servlet request.
     * This is also called for http client, when the response headers
     * are received.
     *
     * TODO: rename it to HttMessageReceived or something similar.
     */
    public static interface HttpService {
        void service(HttpRequest httpReq, HttpResponse httpRes) throws IOException;
    }

    /**
     * Called when both request and response bodies have been sent/
     * received. After this call the HttpChannel will be disconnected
     * from the http connection, which can be used for other requests.
     */
    public static interface RequestCompleted {
        void handle(HttpChannel data, Object extraData) throws IOException;
    }

    Runnable dispatcherRunnable = new Runnable() {
        @Override
        public void run() {
            getConnector().getDispatcher().runService(HttpChannel.this);
        }
    };


}