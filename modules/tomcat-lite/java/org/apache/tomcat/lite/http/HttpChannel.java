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
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpMessage.HttpMessageBytes;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.DumpChannel;
import org.apache.tomcat.lite.io.FastHttpDateFormat;
import org.apache.tomcat.lite.io.BBucket;
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

    protected static Logger log = Logger.getLogger("HttpCh");

    boolean debug = false;
    // Used to receive an entity - headers + maybe some body
    // read() must first consume extra data from this buffer.
    // Next reads will be direct from socket.
    protected BBuffer headRecvBuf = BBuffer.allocate(HEADER_SIZE);
    BBuffer line = BBuffer.wrapper(); 
    
    // ---- Buffers owned by the AsyncHttp object ----
    
    BBuffer headB = BBuffer.wrapper();
    FutureCallbacks<HttpChannel> doneLock = new FutureCallbacks<HttpChannel>();
    ArrayList<IOChannel> filters = new ArrayList<IOChannel>();

    // ---------- Body read side ------------

    // Set if Exect: 100-continue was set on reqest.
    // If this is the case - body won't be sent until
    // server responds ( client ) and server will only
    // read body after ack() - or skip to next request 
    // without swallowing the body.
    protected boolean expectation = false;
    
    /** Ready for recycle, if send/receive are done */
    protected boolean release = false;
    
    protected boolean sendReceiveDone = false; 
    
    
    // ----------- Body write side ------------
    
    
    // TODO: setters
    /**
     * Called when the incoming headers have been received.
     * ( response for client mode, request for server mode )
     * @throws IOException 
     */
    HttpService httpService;
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
    
    
    HttpMessage inMessage;
    
    
    HttpMessage outMessage;
    
    // receive can be for request ( server mode ) or response ( client )
    HttpBody receiveBody = new HttpBody(this, false);
    HttpBody sendBody = new HttpBody(this, true);
    
    private HttpRequest httpReq;
    private HttpResponse httpRes;

    boolean headersDone = false;
    protected boolean serverMode = false;
    // ---------- Client only ------------
    
    //protected HttpParser parser = new HttpParser();
    
    protected String dbgName = this.getClass().getSimpleName();
    // ----- Pools - sockets, heavy objects -------------
    // If client mode - what host we are connected to.
    protected String host;

    protected int port; 
    
    private HttpConnector httpConnector;
    // ------ JMX 
    protected int ser; // id - for jmx registration and logs
    // Server side only 
    protected String serverHeader = "TomcatLite";
    
    protected boolean http11 = false;
    
    protected boolean http09 = false;
    protected boolean error = false;
    protected boolean abortDone = false;
    FutureCallbacks<HttpChannel> doneFuture;
    boolean doneCallbackCalled = false;

    /** 
     * Close connection when done writting, no content-length/chunked, 
     * or no keep-alive ( http/1.0 ) or error.
     * 
     * ServerMode: set if HTTP/0.9 &1.0 || !keep-alive
     * ClientMode: not currently used
     */    
    boolean keepAlive = true;

    // Will be signalled (open) when the buffer is empty. 
    private FutureCallbacks<IOChannel> flushLock = new FutureCallbacks<IOChannel>();

    // -- Lifecycle --
    
    Runnable dispatcherRunnable = new Runnable() {
        @Override
        public void run() {
            getConnector().getDispatcher().runService(HttpChannel.this);
        }
    };
    
    long ioTimeout = 30 * 60000; // 30 min seems high enough 
        
    public static final String CONTENT_LENGTH= "Content-Length";

    /**
     * HTTP/1.0.
     */
    public static final String HTTP_10 = "HTTP/1.0";
    
    public static final String HTTP_11 = "HTTP/1.1";
    
    public static final String CHUNKED = "chunked";
    
    public static final String CLOSE = "close"; 
    
    public static final String KEEPALIVE_S = "keep-alive";


    public static final String CONNECTION = "Connection";
    
    public static final String TRANSFERENCODING = "Transfer-Encoding";
    
    /**
     * SEMI_COLON.
     */
    public static final byte SEMI_COLON = (byte) ';';
    
    static byte[] END_CHUNK_BYTES = {
    (byte) '\r', (byte) '\n', 
    (byte) '0', 
    (byte) '\r', (byte) '\n', 
    (byte) '\r', (byte) '\n'};
    
    public static final byte QUESTION = (byte) '?';
    
    static final byte COLON = (byte) ':';
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
    public void abort(Throwable t) throws IOException {
        abort(t.toString());
    }
    
    public void abort(String t) throws IOException {
        synchronized (this) {
            if (abortDone) {
                return;
            }
            abortDone = true;
        }

        checkRelease();
        trace("abort " + t);
        log.info("Abort connection " + t);
        if (net != null ) {
            if (net.isOpen()) {
                net.close();
                net.startSending();
            }
        }
        inMessage.state = HttpMessage.State.DONE;
        outMessage.state = HttpMessage.State.DONE;
        sendReceiveDone = true;
        error = true;
        close();
        handleEndSendReceive();
    }
    
    public HttpChannel addFilterAfter(IOChannel filter) {
        filters.add(filter);
        return this;
    }

    
    private void checkRelease() throws IOException {
        if (release && sendReceiveDone) {
            throw new IOException("Object released");
        }        
    }
    
    void closeStreamOnEnd(String cause) {
        if (debug) 
            log.info("Not reusing connection because: " + cause);
        keepAlive = false;
    }

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
    
    public void flushNet() throws IOException {
        checkRelease();
        if (net != null) {
            net.startSending();
        }
    }
    
    public HttpConnector getConnector() {
        return httpConnector;
    }

    public FutureCallbacks<HttpChannel> getDoneFuture() {
        return doneLock;
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

    public IOChannel getNet() {
        return net;
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
            (serverMode ? "SRV:" : "") + 
            (keepAlive() ? " KA " : "")  
            + "RCV=[" + inMessage.state.toString() + " " + 
            receiveBody.toString()  
            + "] SND=[" + outMessage.state.toString() 
            + " " + sendBody.toString() + "]";
    }

    
    public String getStatus() {
        return getResponse().getStatus() + " " + getResponse().getMessage();
    }
    
    
 
    public String getTarget() {
        if (host == null) {
            return ":" + port;
        }
        return host + ":" + port;
    }


    /**
     * Called from IO thread, after the request body 
     * is completed ( or if there is no req body )
     * @throws IOException 
     */
    protected void handleEndReceive(boolean frameError) throws IOException {
        if (inMessage.state == HttpMessage.State.DONE) {
            return;
        }
        if (debug) {
            trace("END_RECV " + ((frameError) ? " FRAME_ERROR" : ""));
        }
        if (frameError) {
            closeStreamOnEnd("frame error");
            // TODO: next read() should throw exception !!
            error = true;
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
        
        if (!keepAlive() && net != null) {
            net.getOut().close(); // shutdown output if not done
            net.getIn().close(); // this should close the socket
            net.startSending();
        }

        if (doneAllCallback != null) {
            doneAllCallback.handle(this, error ? new Throwable() : null);
        }
        
        // Remove the net object - will be pooled separtely
        IOChannel ch = this.net;
        if (ch != null && keepAlive()) {
            
            boolean keepOpen = ch.isOpen(); 
        
            resetBuffers(); // net is now NULL - can't send anything more
            if (getConnector() != null) {
                getConnector().returnSocket(ch, serverMode, keepOpen);
            }
        }
        
        if (debug) {
            trace("END_SEND_RECEIVE" 
                    + (!keepAlive() ? " CLOSE_ON_END " : "")
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

    @Override
    public void handleFlushed(IOChannel net) throws IOException {
        flushLock.signal(this);
        super.handleFlushed(this);
        if (sendBody.isClosedAndEmpty()) {
            handleEndSent();
        }
    }
    
    /**
     * Called when the net has readable data.
     */
    @Override
    public void handleReceived(IOChannel net) throws IOException {
        try {
            if (getConnector() == null) {
                throw new IOException("Data received after release");
            }
            if (net == null) {
                return; // connection released
            }
            if (net.getIn().isClosedAndEmpty()) {
                // Close received
                closeStreamOnEnd("close on input 2");
                if (inMessage.state == HttpMessage.State.HEAD) {
                    trace("NET CLOSE WHILE READING HEAD");
                    abort(new IOException("Connection closed"));
                    return;
                } else if (inMessage.state == HttpMessage.State.DONE) {
                    // Close received - make sure we close out
                    if (sendBody.isClosedAndEmpty()) {
                        net.getOut().close();
                    }
                    return;
                }
            }
            if (debug) {
                trace("Http data received " + inMessage.state + " " + 
                        net.getIn() + " headerDone=" + headersDone);
            }

            if (inMessage.state == HttpMessage.State.HEAD) {
                headDataReceived();
                if (inMessage.state == HttpMessage.State.HEAD) {
                    return; // still parsing head
                }
                if (serverMode && httpReq.decodedUri.remaining() == 0) {
                    abort("Invalid url");
                }
            } 

            if (inMessage.state == HttpMessage.State.BODY_DATA) {
                if (net != null) {
                    receiveBody.rawDataReceived(net.getIn());
                }
            }
            
            // Send header callbacks - we process any incoming data 
            // first, so callbacks have more info
            if (httpService != null && !headersDone) {
                headersDone = true;
                try {
                    httpService.service(getRequest(), getResponse());
                } catch (Throwable t) {
                    t.printStackTrace();
                    abort(t);
                }
            }

            // If header callback or previous dataReceived() hasn't consumed all 
            if (receiveBody.getBufferCount() > 0) {
                // Has data 
                super.sendHandleReceivedCallback(); // callback
            }

            // Receive has marked the body as closed
            if (receiveBody.isAppendClosed() 
                    && inMessage.state != HttpMessage.State.DONE) {
                if (net != null && net.getIn().getBufferCount() > 0) {
                    if (debug) {
                        trace("Pipelined requests"); // may be a crlf
                    }
                }                
                handleEndReceive(receiveBody.frameError);
            }

            if (inMessage.state == HttpMessage.State.DONE) {
                // TCP end ? 
                if (net == null || net.getIn() == null) {
                    trace("NO NET");
                    return;
                }
                if (net.getIn().isClosedAndEmpty()) {
                    // If not already closed.
                    closeStreamOnEnd("closed on input 3");
                    if (outMessage.state == HttpMessage.State.DONE) {
                        // need to flush out.
                        net.getOut().close();
                        flushNet();
                    }
                } else {
                    // Next request, ignore it.
                }

            }
        } catch (IOException ex) {
            ex.printStackTrace();
            abort(ex);
        }
    }
 

    /** 
     * Read and process a chunk of head, called from dataReceived() if 
     * in HEAD mode.
     * 
     * @return <= 0 - still in head mode. > 0 moved to body mode, some 
     * body chunk may have been received. 
     */
    protected void headDataReceived() throws IOException {
        while (true) {
            // we know we have one
            int read = net.getIn().readLine(headRecvBuf);
            if (read < 0) {
                if (debug) {
                    trace("CLOSE while reading HEAD");    
                }
                // too early - we don't have the head
                abort("Close in head");
                return;
            }
            // Remove starting empty lines.
            headRecvBuf.skipEmptyLines();

            // Do we have another full line in the input ?
            if (BBuffer.hasLFLF(headRecvBuf)) {
                break;
            }
            if (read == 0) { // no more data
                return;
            }
        }
        headRecvBuf.wrapTo(headB);

        
        parseMessage(headB);

        
        if (debug) {
            trace("HEAD_RECV " + getRequest().requestURI() + " " + 
                    getResponse().getMsgBytes().status() + " " + net.getIn());
        }
        
    }
    
    public void parseMessage(BBuffer headB) throws IOException {
        //Parse the response
        headB.readLine(line);
        
        HttpMessageBytes msgBytes;

        if (serverMode) {
            msgBytes = httpReq.getMsgBytes();
            parseRequestLine(line, msgBytes.method(),
                    msgBytes.url(),
                    msgBytes.query(),
                    msgBytes.protocol());
        } else {
            msgBytes = httpRes.getMsgBytes();
            parseResponseLine(line, msgBytes.protocol(), 
                    msgBytes.status(), msgBytes.message());
        }
        
        parseHeaders(msgBytes, headB);

        inMessage.state = HttpMessage.State.BODY_DATA;
        
        // TODO: hook to allow specific charsets ( can be done later )

        inMessage.processReceivedHeaders();
    }

    private void init() {
        headRecvBuf.recycle();
        headersDone = false;
        sendReceiveDone = false;
        
        receiveBody.recycle();
        sendBody.recycle();
        expectation = false;
        
        http11 = false;
        http09 = false;
        error = false;
        abortDone = false;
       
        
        getRequest().recycle();
        getResponse().recycle();
        host = null;
        filters.clear();
        
        line.recycle();
        headB.recycle();
        
        doneLock.recycle();
        flushLock.recycle();
        
        doneCallbackCalled = false;
        keepAlive = true;
        // Will be set again after pool
        setHttpService(null);
        doneAllCallback = null;
        release = false;
    }
    
    public boolean isDone() {
        return outMessage.state == HttpMessage.State.DONE && inMessage.state == HttpMessage.State.DONE;
    }
    
    public boolean keepAlive() {
        if (http09) {
            return false;
        }
        return keepAlive;
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
    
    
    /**
     * Parse one header. 
     * Line must be populated. On return line will be populated
     * with the next header:
     * 
     * @param line current header line, not empty.
     */
    public int parseHeader(BBuffer head, 
            BBuffer line, BBuffer name, BBuffer value)
          throws IOException {
        
        int newPos = line.readToDelimOrSpace(COLON, name);
        line.skipSpace();
        if (line.readByte() != COLON) {
            throw new IOException("Missing ':' in header name " + line);
        }
        line.skipSpace();
        line.read(value); // remaining of the line
        
        while (true) {
            head.readLine(line);
            if (line.remaining() == 0) {
                break;
            }
            byte first = line.get(0);
            if (first != BBuffer.SP && first != BBuffer.HT) {
                break;
            }
            // continuation line - append it to value
            value.setEnd(line.getEnd());
            line.position(line.limit());
        }

        // We may want to keep the original and use separate buffer ?
        normalizeHeader(value);
        return 1;
    }
    
    public void parseHeaders(HttpMessageBytes msgBytes,
            BBuffer head) 
                throws IOException {
        
        head.readLine(line);
        
        int idx = 0;
        while(line.remaining() > 0) {
            // not empty..
            idx = msgBytes.addHeader();
            BBuffer nameBuf = msgBytes.getHeaderName(idx);
            BBuffer valBuf = msgBytes.getHeaderValue(idx);
            parseHeader(head, line, nameBuf, valBuf);
            
            // TODO: process 'interesting' headers here.
        }
    }

    /**
     * Read the request line. This function is meant to be used during the 
     * HTTP request header parsing. Do NOT attempt to read the request body 
     * using it.
     *
     * @throws IOException If an exception occurs during the underlying socket
     * read operations, or if the given buffer is not big enough to accomodate
     * the whole line.
     */
    public boolean parseRequestLine(BBuffer line, 
            BBuffer methodMB, BBuffer requestURIMB,
            BBuffer queryMB,
            BBuffer protoMB)
        throws IOException {

        line.readToSpace(methodMB);
        line.skipSpace();
        
        line.readToDelimOrSpace(QUESTION, requestURIMB);
        if (line.remaining() > 0 && line.get(0) == QUESTION) {
            // Has query
            line.readToSpace(queryMB);
            // don't include '?'
            queryMB.position(queryMB.position() + 1);
        } else {
            queryMB.setBytes(line.array(), line.position(), 0);
        }
        line.skipSpace();

        line.readToSpace(protoMB);
        
        // proto is optional ( for 0.9 )
        return requestURIMB.remaining() > 0;
    }

    public boolean parseResponseLine(BBuffer line,
            BBuffer protoMB, BBuffer statusCode, BBuffer status)
            throws IOException {
        line.skipEmptyLines();

        line.readToSpace(protoMB);
        line.skipSpace();
        line.readToSpace(statusCode);
        line.skipSpace();
        line.wrapTo(status);
        
        // message may be empty
        return statusCode.remaining() > 0;
    }

    /**
     * Update keepAlive based on Connection header and protocol.
     */
    void processConnectionHeader(MultiMap headers) {
        if (http09) {
            return;
        }

        CBuffer value = headers.getHeader(HttpChannel.CONNECTION);
        String conHeader = (value == null) ? null : value.toString();
        if (conHeader != null) {
            if (HttpChannel.CLOSE.equalsIgnoreCase(conHeader)) {
                closeStreamOnEnd("connection close");
            }
            if (!HttpChannel.KEEPALIVE_S.equalsIgnoreCase(conHeader)) {
                closeStreamOnEnd("connection != keep alive");
            }
        } else {
            // no connection header
            if (!http11) {
                closeStreamOnEnd("http1.0 no connection header");
            }
        }
    }

    void processExpectation() throws IOException {
        expectation = false;
        MultiMap headers = getRequest().getMimeHeaders();

        CBuffer expect = headers.getHeader("expect");
        if ((expect != null)
                && (expect.indexOf("100-continue") != -1)) {
            expectation = true;

            // TODO: configure, use the callback or the servlet 'read'. 
            net.getOut().append("HTTP/1.1 100 Continue\r\n\r\n");
            net.startSending();
        }
    }

    void processProtocol() throws IOException {
        http11 = true;
        http09 = false;
        
        CBuffer protocolMB = getRequest().protocol();
        if (protocolMB.equals(HttpChannel.HTTP_11)) {
            http11 = true;
        } else if (protocolMB.equals(HttpChannel.HTTP_10)) {
            http11 = false;
        } else if (protocolMB.equals("")) {
            http09 = true;
            http11 = false;
        } else {
            // Unsupported protocol
            http11 = false;
            error = true;
            // Send 505; Unsupported HTTP version
            getResponse().setStatus(505);
            abort("Invalid protocol");
        }
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

    public void resetBuffers() {
        if (net != null) {
            net.setDataFlushedCallback(null);
            net.setDataReceivedCallback(null);
            setSink(null);
        }
    }

    public void sendHeaders() throws IOException {
        checkRelease();
        if (serverMode) {
            sendResponseHeaders();
        } else {
            sendRequest();
        }
    }

    /** 
     * Can be called from any thread.
     * 
     * @param host
     * @param port
     * @throws IOException
     */
    public void sendRequest() throws IOException {
        if (getRequest().isCommitted()) {
            return;
        }
        getRequest().setCommitted(true);

        String target = host + ":" + port;
        
        if (getRequest().getMimeHeaders().getHeader("Host") == null
                && host != null) {
            CBuffer hostH = getRequest().getMimeHeaders().addValue("Host");
            hostH.set(host); // TODO: port
        }
        
        outMessage.state = HttpMessage.State.HEAD;

        IOChannel ch = getConnector().cpool.getChannel(target);

        if (ch == null) {
            if (debug) {
                trace("HTTP_CONNECT: New connection " + target);
            }
            IOConnector.ConnectedCallback connected = new IOConnector.ConnectedCallback() {
                @Override
                public void handleConnected(IOChannel ch) throws IOException {
                    if (httpConnector.debugHttp) {
                        IOChannel ch1 = new DumpChannel("");
                        ch.addFilterAfter(ch1);
                        ch = ch1;                        
                    }
                    
                    sendRequestHeaders(ch);
                }
            };
            getConnector().getIOConnector().connect(host, port, connected);
        } else {
            if (debug) {
                trace("HTTP_CONNECT: Reuse connection " + target + " " + this);
            }
            // TODO retry if closed
            sendRequestHeaders(ch);
        }
    }

    /** 
     * Used in request mode.  
     * 
     * @throws IOException
     */
    void sendRequestHeaders(IOChannel ch) throws IOException {
        if (getConnector() == null) {
            throw new IOException("after release");
        }
        if (!ch.isOpen()) {
            abort("Closed channel");
            return;
        }
        setChannel(ch); // register read/write callbacks
        
        // Update transfer fields based on headers.
        processProtocol();
        
        processConnectionHeader(getRequest().getMimeHeaders());


        // 1.0: The presence of an entity body in a request is signaled by 
        // the inclusion of a Content-Length header field in the request 
        // message headers. HTTP/1.0 requests containing an entity body 
        // must include a valid Content-Length header field.

        if (!sendBody.isContentDelimited()) {
            // Will not close connection - just flush and mark the body 
            // as sent
            sendBody.noBody = true;
            getOut().close();
        }

        if (sendBody.noBody) {
            getRequest().getMimeHeaders().remove(HttpChannel.CONTENT_LENGTH);
            getRequest().getMimeHeaders().remove(HttpChannel.TRANSFERENCODING);
        } else {
            long contentLength = 
                getRequest().getContentLength();
            if (contentLength < 0) {
                getRequest().getMimeHeaders().addValue(HttpChannel.TRANSFERENCODING).
                    set(HttpChannel.CHUNKED);
            }
            sendBody.processContentDelimitation();
        }

        sendBody.updateCloseOnEnd();

        try {
            getRequest().serialize(net.getOut());
            if (debug) {
                trace("S: \n" + net.getOut());
            }

        } catch (Throwable t) {
            log.log(Level.SEVERE, "Error sending request", t);
        }

        if (outMessage.state == HttpMessage.State.HEAD) {
            outMessage.state = HttpMessage.State.BODY_DATA;
        }

        // TODO: add any body and flush. More body can be added later - 
        // including 'end'.

        startSending();
        
    }

    /**
     * When committing the response, we have to validate the set of headers, as
     * well as setup the response filters.
     * Only in server mode.
     */
    void sendResponseHeaders() throws IOException {
        checkRelease();
        if (!serverMode) {
            throw new IOException("Only in server mode");
        }

        if (getResponse().isCommitted()) {
            return; 
        }
        getResponse().setCommitted(true);
        
        sendBody.noBody = !getResponse().hasBody();

        if (sendBody.statusDropsConnection(getResponse().getStatus())) {
            closeStreamOnEnd("status drops connection");
        }
        if (error) {
            closeStreamOnEnd("error");
        }

        // A header explicitely set.
        CBuffer transferEncHeader = 
            getResponse().getMimeHeaders().getHeader(HttpChannel.TRANSFERENCODING);
        if (!sendBody.noBody 
                && keepAlive()) {
            if (getResponse().getContentLength() < 0) {
                // Use chunked by default, if no c-l
                if (transferEncHeader == null) {
                    getResponse().getMimeHeaders().addValue(HttpChannel.TRANSFERENCODING).set(HttpChannel.CHUNKED);
                } else {
                    transferEncHeader.set(HttpChannel.CHUNKED);                    
                }
            }
        }
        
        sendBody.processContentDelimitation();
        
        sendBody.updateCloseOnEnd();
        
        MultiMap headers = getResponse().getMimeHeaders();

        // Add date header
        if (headers.getHeader("Date") == null) {
            headers.setValue("Date").set(FastHttpDateFormat.getCurrentDate());
        }

        // Add server header
        if (serverHeader.length() > 0) {
            headers.setValue("Server").set(serverHeader);
        }

        // did the user set a connection header that may override what we have ?
        processConnectionHeader(headers);
        
        if (!keepAlive()) {
            headers.setValue(HttpChannel.CONNECTION).set(HttpChannel.CLOSE);
        } else {
            if (!http11 && !http09) {
                headers.setValue(HttpChannel.CONNECTION).set(HttpChannel.KEEPALIVE_S);                
            }
        }
    
        if (debug) {
            trace("Send response headers " + net);
        }
        if (net != null) {
            getResponse().serialize(net.getOut());
        }
        
        if (outMessage.state == HttpMessage.State.HEAD) {
            outMessage.state = HttpMessage.State.BODY_DATA;
        }
        
        if (sendBody.isDone()) {
            getOut().close();
        }

        if (net != null) {
            net.startSending();
        }
    }

    public HttpChannel serverMode(boolean enabled) {
        if (enabled) {
            serverMode = true;
            dbgName = "AsyncHttpServer";
            httpReq.setBody(receiveBody);
            httpRes.setBody(sendBody);
            sendBody.setMessage(httpRes);
            receiveBody.setMessage(httpReq);
            inMessage = httpReq;
            outMessage = httpRes;
        } else {
            serverMode = false;
            dbgName = "AsyncHttp";         
            httpReq.setBody(sendBody);
            httpRes.setBody(receiveBody);
            sendBody.setMessage(httpReq);
            receiveBody.setMessage(httpRes);
            inMessage = httpRes;
            outMessage = httpReq;
        }
        if (debug) {
            log = Logger.getLogger(dbgName);
        }
        return this;
    }
    
    public void setChannel(IOChannel ch) throws IOException {
        for (IOChannel filter: filters) {
            ch.addFilterAfter(filter);
            ch = filter;
        }
        
        withBuffers(ch);
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

    public void setTarget(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    public void startSending() throws IOException {
        checkRelease();
        
        sendBody.flushToNext();
        flushNet();
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
 
    public void waitFlush(long timeMs) throws IOException {
        if (getOut().getBufferCount() == 0) {
            return;
        }
        flushLock.waitSignal(timeMs);
    }
 
    public HttpChannel withBuffers(IOChannel net) {
        setSink(net);
        net.setDataFlushedCallback(this);
        net.setDataReceivedCallback(this);
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
    
    
    
    public static interface HttpService {
        void service(HttpRequest httpReq, HttpResponse httpRes) throws IOException;
    }
    
    public static interface RequestCompleted {
        void handle(HttpChannel data, Object extraData) throws IOException;
    }

    
}