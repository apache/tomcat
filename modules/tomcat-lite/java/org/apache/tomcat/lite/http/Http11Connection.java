/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpConnector.HttpConnection;
import org.apache.tomcat.lite.http.HttpMessage.HttpMessageBytes;
import org.apache.tomcat.lite.io.BBucket;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.DumpChannel;
import org.apache.tomcat.lite.io.FastHttpDateFormat;
import org.apache.tomcat.lite.io.Hex;
import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;

public class Http11Connection extends HttpConnection
        implements IOConnector.ConnectedCallback {
    public static final String CHUNKED = "chunked";

    public static final String CLOSE = "close";

    public static final String KEEPALIVE_S = "keep-alive";

    public static final String CONNECTION = "connection";

    public static final String TRANSFERENCODING = "transfer-encoding";


    protected static Logger log = Logger.getLogger("Http11Connection");
    static final byte COLON = (byte) ':';

    // super.net is the socket

    boolean debug;
    BBuffer line = BBuffer.wrapper();
    boolean endSent = false;

    BodyState receiveBodyState = new BodyState();
    BodyState sendBodyState = new BodyState();

    BBuffer headW = BBuffer.wrapper();

    boolean headersReceived = false;
    boolean bodyReceived = false;

    /**
     * Close connection when done writting, no content-length/chunked,
     * or no keep-alive ( http/1.0 ) or error.
     *
     * ServerMode: set if HTTP/0.9 &1.0 || !keep-alive
     * ClientMode: not currently used
     */
    boolean keepAlive = true;

    protected boolean http11 = true;
    protected boolean http10 = false;
    protected boolean http09 = false;

    HttpConnection switchedProtocol = null;

    private int requestCount = 0;

    // dataReceived and endSendReceive
    private Object readLock = new Object();

    public Http11Connection(HttpConnector httpConnector) {
        this.httpConnector = httpConnector;
        if (httpConnector != null) {
            debug = httpConnector.debugHttp;
        }
    }

    public void beforeRequest() {
        nextRequest();
        headRecvBuf.recycle();
    }

    public void nextRequest() {
        endSent = false;
        keepAlive = true;
        receiveBodyState.recycle();
        sendBodyState.recycle();
        http11 = true;
        http09 = false;
        http10 = false;
        headersReceived = false;
        bodyReceived = false;
    }

    public Http11Connection serverMode() {
        serverMode = true;
        return this;
    }

    private boolean readHead() throws IOException {
        while (true) {
            int read;
            if (requestCount == 0 && headRecvBuf.remaining() < 4) {
                // requests have at least 4 bytes - detect protocol
                read = net.getIn().read(headRecvBuf, 4);
                if (read < 0) {
                    return closeInHead();
                }
                if (read < 4) {
                    return false; // need more
                }
                // we have at least 4 bytes
                if (headRecvBuf.get(0) == 0x80 &&
                        headRecvBuf.get(1) == 0x01) {
                    // SPDY signature ( experimental )
                    switchedProtocol = new SpdyConnection(httpConnector,
                            remoteHost);
                    if (serverMode) {
                        switchedProtocol.serverMode = true;
                    }
                    switchedProtocol.withExtraBuffer(headRecvBuf);
                    // Will also call handleReceived
                    switchedProtocol.setSink(net);
                    return false;
                }

            }

            // we know we have one
            read = net.getIn().readLine(headRecvBuf);
            // Remove starting empty lines.
            headRecvBuf.skipEmptyLines();

            // Do we have another full line in the input ?
            if (BBuffer.hasLFLF(headRecvBuf)) {
                break; // done
            }
            if (read == 0) { // no more data
                return false;
            }
            if (read < 0) {
                return closeInHead();
            }
        }


        return true;
    }

    private boolean closeInHead() throws IOException {
        if (debug) {
            trace("CLOSE while reading HEAD");
        }
        // too early - we don't have the head
        abort("Close in head");
        return false;
    }

    // Unit tests use this to access the HttpChannel
    protected HttpChannel checkHttpChannel() throws IOException {
        if (switchedProtocol != null) {
            return switchedProtocol.checkHttpChannel();
        }
        if (activeHttp == null) {
            if (serverMode) {
                activeHttp = httpConnector.getServer();
                activeHttp.setConnection(this);
                if (httpConnector.defaultService != null) {
                    activeHttp.setHttpService(httpConnector.defaultService);
                }
            } else {
            }
        }
        return activeHttp;
    }

    @Override
    public void dataReceived(IOBuffer netx) throws IOException {
        if (switchedProtocol != null) {
            switchedProtocol.dataReceived(netx);
            return;
        }
        //trace("handleReceived " + headersReceived);
        if (!checkKeepAliveClient()) {
            return; // we were in client keep alive mode
        }
        // endSendReceived uses same lock - it will call this
        // to check outstanding bytes
        synchronized (readLock) {
            if (bodyReceived) {
                return; // leave data in net buffer, for next req
            }

            if (!headersReceived) {
                if (!readHead()) {
                    return;
                }
            }

            // We have a header
            if (activeHttp == null) {
                if (checkHttpChannel() == null) {
                    return;
                }
            }

            IOBuffer receiveBody = activeHttp.receiveBody;

            if (!headersReceived) {
                headRecvBuf.wrapTo(headW);
                parseMessage(activeHttp, headW);
                // Part of parseMessage we can switch the protocol
                if (switchedProtocol != null) {
                    return;
                }

                if (serverMode && activeHttp.httpReq.decodedUri.remaining() == 0) {
                    abort(activeHttp, "Invalid url");
                }

                headersReceived = true;
                // Send header callbacks - we process any incoming data
                // first, so callbacks have more info
                trace("Send headers received callback " + activeHttp.httpService);
                activeHttp.handleHeadersReceived(activeHttp.inMessage);
            }

            // any remaining data will be processed as part of the
            // body - or left in the channel until endSendReceive()

            if (!bodyReceived) {
                // Will close receiveBody when it consummed enough
                rawDataReceived(activeHttp, receiveBody, net.getIn());
                // Did we process anything ?
                if (receiveBody.getBufferCount() > 0) {
                    activeHttp.sendHandleReceivedCallback(); // callback
                }
            }
            // Receive has marked the body as closed
            if (receiveBody.isAppendClosed()) {
                bodyReceived = true;
                activeHttp.handleEndReceive();
            }


            if (net.getIn().isClosedAndEmpty()) {
                // If not already closed.
                closeStreamOnEnd("closed after body");
            }

        }
    }

    /**
     * We got data while in client keep alive ( no activeHttp )
     *
     * @return false if there is an error
     */
    private boolean checkKeepAliveClient() throws IOException {
        // Client, no active connection ( keep alive )
        if (!serverMode && activeHttp == null) {
            if (net.getIn().isClosedAndEmpty() || !net.isOpen()) {
                // server disconnected, fine
                httpConnector.cpool.stopKeepAlive(this);
                return false;
            }
            if (net.getIn().available() == 0) {
                return true;
            }
            log.warning("Unexpected message from server in client keep alive "
                    + net.getIn() + ": " + net.getIn().readAll(null));
            if (net.isOpen()) {
                net.close();
            }
            return false;
        }
        return true;
    }

    private void processProtocol(CBuffer protocolMB) throws IOException {
        http11 = false;
        http09 = false;
        http10 = false;

        if (protocolMB.equals(HttpChannel.HTTP_11)) {
            http11 = true;
        } else if (protocolMB.equals(HttpChannel.HTTP_10)) {
            http10 = true;
        } else if (protocolMB.equals("")) {
            http09 = true;
        } else {
            http11 = true; // hopefully will be backward compat
        }
    }

    void closeStreamOnEnd(String cause) {
        if (debug) {
            log.info("Not reusing connection because: " + cause);
        }
        keepAlive = false;
    }

    boolean keepAlive() {
        if (httpConnector != null) {
            if (serverMode && !httpConnector.serverKeepAlive) {
                keepAlive = false;
            }
            if (!serverMode && !httpConnector.clientKeepAlive) {
                keepAlive = false;
            }
        }
        if (http09) {
            keepAlive = false;
        }
        if (net != null && !net.isOpen()) {
            keepAlive = false;
        }
        return keepAlive;
    }

    @Override
    protected void endSendReceive(HttpChannel http) throws IOException {
        if (switchedProtocol != null) {
            switchedProtocol.endSendReceive(http);
            return;
        }
        chunk.recycle();
        rchunk.recycle();
        boolean keepAlive = keepAlive();
        if (!keepAlive) {
            if (debug) {
                log.info("--- Close socket, no keepalive " + net);
            }
            if (net != null) {
                net.close();
                net.startSending();

            }
        }

        requestCount++;
        beforeRequest();
        httpConnector.cpool.afterRequest(http, this, true);

        if (serverMode && keepAlive) {
            handleReceived(net); // will attempt to read next req
        }
    }

    private void trace(String s) {
        if(debug) {
            log.info(this.toString() + " " + activeHttp + " " + s);
        }
    }

    private boolean isDone(BodyState bodys, IOBuffer body) {
        if (bodys.noBody) {
            return true;
        }
        if (bodys.isContentDelimited()) {
            if (!bodys.chunked && bodys.remaining == 0) {
                return true;
            } else if (bodys.chunked && body.isAppendClosed()) {
                return true;
            }
        }
        return false;
    }

    void parseMessage(HttpChannel http, BBuffer headB) throws IOException {
        //Parse the response
        line.recycle();
        headB.readLine(line);

        HttpMessageBytes msgBytes;

        if (serverMode) {
            msgBytes = http.httpReq.getMsgBytes();
            parseRequestLine(line, msgBytes.method(),
                    msgBytes.url(),
                    msgBytes.query(),
                    msgBytes.protocol());
        } else {
            msgBytes = http.httpRes.getMsgBytes();
            parseResponseLine(line, msgBytes.protocol(),
                    msgBytes.status(), msgBytes.message());
        }

        parseHeaders(http, msgBytes, headB);

        http.inMessage.state = HttpMessage.State.BODY_DATA;

        http.inMessage.processReceivedHeaders();

        // TODO: hook to allow specific charsets ( can be done later )
        processProtocol(http.inMessage.protocol());

        if (serverMode) {
            // requested connection:close/keepAlive and proto
            updateKeepAlive(http.getRequest().getMimeHeaders(), true);

            processExpectation(http);

            processContentDelimitation(receiveBodyState, http.getRequest());
            // Spec:
            // The presence of a message-body in a request is signaled by the
            // inclusion of a Content-Length or Transfer-Encoding header field in
            // the request's message-headers
            // Server should read - but ignore ..
            receiveBodyState.noBody = !receiveBodyState.isContentDelimited();

            updateCloseOnEnd(receiveBodyState, http, http.receiveBody);

            /*
             * The presence of a message-body in a request is signaled by the
             * inclusion of a Content-Length or Transfer-Encoding header field in
             * the request's message-headers. A message-body MUST NOT be included
             * in a request if the specification of the request method
             * (section 5.1.1) does not allow sending an entity-body in requests.
             * A server SHOULD read and forward a message-body on any request; if the request method does not include defined semantics for an entity-body, then the message-body SHOULD be ignored when handling the request.
             */
            if (!receiveBodyState.isContentDelimited()) {
                // No body
                http.getIn().close();
            }

        } else {
            receiveBodyState.noBody = !http.getResponse().hasBody();

            updateKeepAlive(http.getResponse().getMimeHeaders(), false);

            if (statusDropsConnection(http.getResponse().getStatus())) {
                closeStreamOnEnd("response status drops connection");
            }
            IOBuffer body = http.receiveBody;
            processContentDelimitation(receiveBodyState, http.getResponse());

            if (isDone(receiveBodyState, body)) {
                body.close();
            }

            if (!receiveBodyState.isContentDelimited()) {
                closeStreamOnEnd("not content delimited");
            }
        }

    }

    private void processExpectation(HttpChannel http) throws IOException {
        http.expectation = false;
        MultiMap headers = http.getRequest().getMimeHeaders();

        CBuffer expect = headers.getHeader("expect");
        if ((expect != null)
                && (expect.indexOf("100-continue") != -1)) {
            http.expectation = true;

            // TODO: configure, use the callback or the servlet 'read'.
            net.getOut().append("HTTP/1.1 100 Continue\r\n\r\n");
            net.startSending();
        }
    }


    /**
     * Updates chunked, contentLength, remaining - based
     * on headers
     */
    private void processContentDelimitation(BodyState bodys,
            HttpMessage httpMsg) {

        bodys.contentLength = httpMsg.getContentLength();
        if (bodys.contentLength >= 0) {
            bodys.remaining = bodys.contentLength;
        }

        // TODO: multiple transfer encoding headers, only process the last
        String transferEncodingValue = httpMsg.getHeader(TRANSFERENCODING);
        if (transferEncodingValue != null) {
            int startPos = 0;
            int commaPos = transferEncodingValue.indexOf(',');
            String encodingName = null;
            while (commaPos != -1) {
                encodingName = transferEncodingValue.substring
                (startPos, commaPos).toLowerCase().trim();
                if ("chunked".equalsIgnoreCase(encodingName)) {
                    bodys.chunked = true;
                }
                startPos = commaPos + 1;
                commaPos = transferEncodingValue.indexOf(',', startPos);
            }
            encodingName = transferEncodingValue.substring(startPos)
                .toLowerCase().trim();
            if ("chunked".equals(encodingName)) {
                bodys.chunked = true;
                httpMsg.chunked = true;
            } else {
                System.err.println("TODO: ABORT 501");
                //return 501; // Currently only chunked is supported for
                // transfer encoding.
            }
        }

        if (bodys.chunked) {
            bodys.remaining = 0;
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
    boolean parseRequestLine(BBuffer line,
            BBuffer methodMB, BBuffer requestURIMB,
            BBuffer queryMB,
            BBuffer protoMB)
        throws IOException {

        line.readToSpace(methodMB);
        line.skipSpace();

        line.readToDelimOrSpace(HttpChannel.QUESTION, requestURIMB);
        if (line.remaining() > 0 && line.get(0) == HttpChannel.QUESTION) {
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

    boolean parseResponseLine(BBuffer line,
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

    List<String> connectionHeaders = new ArrayList<String>();

    private void parseHeaders(HttpChannel http, HttpMessageBytes msgBytes,
            BBuffer head)
                throws IOException {

        head.readLine(line);

        int idx = 0;

        BBuffer upgrade = null;

        while(line.remaining() > 0) {
            // not empty..
            idx = msgBytes.addHeader();
            BBuffer nameBuf = msgBytes.getHeaderName(idx);
            BBuffer valBuf = msgBytes.getHeaderValue(idx);
            parseHeader(http, head, line, nameBuf, valBuf);

            // TODO: process 'interesting' headers here.
            if (nameBuf.equalsIgnoreCase("connection")) {
                // TODO: save and remove if not recognized
            }
            if (nameBuf.equalsIgnoreCase("upgrade")) {
                upgrade = valBuf;
            }
        }

        if (upgrade != null) {
            if (upgrade.equalsIgnoreCase("WebSocket")) {

            } else if (upgrade.equalsIgnoreCase("SPDY/1.0")) {

            }
        }

        // TODO: process connection headers
    }

    /**
     * Parse one header.
     * Line must be populated. On return line will be populated
     * with the next header:
     *
     * @param line current header line, not empty.
     */
    int parseHeader(HttpChannel http, BBuffer head,
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
            int first = line.get(0);
            if (first != BBuffer.SP && first != BBuffer.HT) {
                break;
            }
            // continuation line - append it to value
            value.setEnd(line.getEnd());
            line.position(line.limit());
        }

        // We may want to keep the original and use separate buffer ?
        http.normalizeHeader(value);
        return 1;
    }

    private int receiveDone(HttpChannel http, IOBuffer body, boolean frameError) throws IOException {
        // Content-length case, we're done reading
        body.close();

        http.error = frameError;
        if (frameError) {
            closeStreamOnEnd("frame error");
        }

        return DONE;
    }

    /**
     * Called when raw body data is received.
     * Callback should not consume past the end of the body.
     * @param rawReceiveBuffers
     *
     */
    private void rawDataReceived(HttpChannel http, IOBuffer body,
            IOBuffer rawReceiveBuffers) throws IOException {
        // TODO: Make sure we don't process more than we need ( eat next req ).
        // If we read too much: leave it in readBuf, the finalzation code
        // should skip KeepAlive and start processing it.
        // we need to read at least something - to detect -1 ( we could
        // suspend right away, but seems safer
        BodyState bodys = receiveBodyState;

        while (http.inMessage.state == HttpMessage.State.BODY_DATA) {
            if (receiveBodyState.noBody) {
                receiveDone(http, body, false);
                return;
            }
            if (rawReceiveBuffers.isClosedAndEmpty()) {
                if (receiveBodyState.isContentDelimited()) {
                    if (receiveBodyState.contentLength >= 0 && receiveBodyState.remaining == 0) {
                        receiveDone(http, body, false);
                    } else {
                        // End of input - other side closed, no more data
                        //log.info("CLOSE while reading " + this);
                        // they're not supposed to close !
                        receiveDone(http, body, true);
                    }
                } else {
                    receiveDone(http, body, false); // ok
                }
                // input connection closed ?
                closeStreamOnEnd("Closed input");
                return;
            }
            BBucket rawBuf = rawReceiveBuffers.peekFirst();
            if (rawBuf == null) {
                return;  // need more data
            }

            if (!bodys.isContentDelimited()) {
                while (true) {
                    BBucket first = rawReceiveBuffers.popFirst();
                    if (first == null) {
                        break; // will go back to check if done.
                    } else {
                        received(body, first);
                    }
                }
            } else {

                if (bodys.contentLength >= 0 && bodys.remaining == 0) {
                    receiveDone(http, body, false);
                    return;
                }

                if (bodys.chunked && bodys.remaining == 0) {
                    int rc = NEED_MORE;
                    // TODO: simplify, use readLine()
                    while (rc == NEED_MORE) {
                        rc = rchunk.parseChunkHeader(rawReceiveBuffers);
                        if (rc == ERROR) {
                            http.abort("Chunk error");
                            receiveDone(http, body, true);
                            return;
                        } else if (rc == NEED_MORE) {
                            return;
                        }
                    }
                    if (rc == 0) { // last chunk
                        receiveDone(http, body, false);
                        return;
                    } else {
                        bodys.remaining = rc;
                    }
                }

                rawBuf = (BBucket) rawReceiveBuffers.peekFirst();
                if (rawBuf == null) {
                    return;  // need more data
                }


                if (bodys.remaining < rawBuf.remaining()) {
                    // To buffer has more data than we need.
                    int lenToConsume = (int) bodys.remaining;
                    BBucket sb = rawReceiveBuffers.popLen(lenToConsume);
                    received(body, sb);
                    //log.info("Queue received buffer " + this + " " + lenToConsume);
                    bodys.remaining = 0;
                } else {
                    BBucket first = rawReceiveBuffers.popFirst();
                    bodys.remaining -= first.remaining();
                    received(body, first);
                    //log.info("Queue full received buffer " + this + " RAW: " + rawReceiveBuffers);
                }
                if (bodys.contentLength >= 0 && bodys.remaining == 0) {
                    // Content-Length, all done
                    body.close();
                    receiveDone(http, body, false);
                }
            }
        }
    }

    private void received(IOBuffer body, BBucket bb) throws IOException {
        body.queue(bb);
    }


    protected void sendRequest(HttpChannel http)
            throws IOException {
        if (switchedProtocol != null) {
            switchedProtocol.sendRequest(http);
            return;
        }

        // Update transfer fields based on headers.
        processProtocol(http.getRequest().protocol());
        updateKeepAlive(http.getRequest().getMimeHeaders(), true);

        // Update Host header
        if (http.getRequest().getMimeHeaders().getHeader("Host") == null) {
            String target = http.getTarget();
            if (target == null) {
                throw new IOException("Missing host header");
            }
            CBuffer hostH = http.getRequest().getMimeHeaders().addValue("Host");
            if (target.endsWith(":80")) {
                hostH.set(target.substring(0, target.length() - 3));
            } else {
                hostH.set(target);
            }
        }

        processContentDelimitation(sendBodyState,
                http.getRequest());


        CBuffer method = http.getRequest().method();
        if (method.equals("GET") || method.equals("HEAD")) {
            // TODO: add the others
            sendBodyState.noBody = true;
        }

        // 1.0: The presence of an entity body in a request is signaled by
        // the inclusion of a Content-Length header field in the request
        // message headers. HTTP/1.0 requests containing an entity body
        // must include a valid Content-Length header field.
        if (http10 && !sendBodyState.isContentDelimited()) {
            // Will not close connection - just flush and mark the body
            // as sent
            sendBodyState.noBody = true;
        }

        if (sendBodyState.noBody) {
            http.getRequest().getMimeHeaders().remove(HttpChannel.CONTENT_LENGTH);
            http.getRequest().getMimeHeaders().remove(TRANSFERENCODING);
            http.getOut().close();
        } else {
            long contentLength =
                http.getRequest().getContentLength();
            if (contentLength < 0) {
                http.getRequest().getMimeHeaders().addValue("Transfer-Encoding").
                    set(CHUNKED);
            }
        }

        updateCloseOnEnd(sendBodyState, http, http.sendBody);

        try {
            serialize(http.getRequest(), net.getOut());
            if (http.debug) {
                http.trace("S: \n" + net.getOut());
            }

            if (http.outMessage.state == HttpMessage.State.HEAD) {
                http.outMessage.state = HttpMessage.State.BODY_DATA;
            }


            // TODO: add any body and flush. More body can be added later -
            // including 'end'.

            http.startSending();
        } catch (Throwable t) {
            log.log(Level.SEVERE, "Error sending request", t);
            abort(t.getMessage());
        }

    }


    /**
     * Determine if we must drop the connection because of the HTTP status
     * code.  Use the same list of codes as Apache/httpd.
     */
    private boolean statusDropsConnection(int status) {
        return status == 400 /* SC_BAD_REQUEST */ ||
        status == 408 /* SC_REQUEST_TIMEOUT */ ||
        status == 411 /* SC_LENGTH_REQUIRED */ ||
        status == 413 /* SC_REQUEST_ENTITY_TOO_LARGE */ ||
        status == 414 /* SC_REQUEST_URI_TOO_LARGE */ ||
        status == 500 /* SC_INTERNAL_SERVER_ERROR */ ||
        status == 503 /* SC_SERVICE_UNAVAILABLE */ ||
        status == 501 /* SC_NOT_IMPLEMENTED */;
    }

    protected void sendResponseHeaders(HttpChannel http)
            throws IOException {
        if (switchedProtocol != null) {
            switchedProtocol.sendResponseHeaders(http);
            return;
        }

        if (!serverMode) {
            throw new IOException("Only in server mode");
        }
        endSent = false;
        IOBuffer sendBody = http.sendBody;
        HttpResponse res = http.getResponse();
        if (res.isCommitted()) {
            return;
        }
        res.setCommitted(true);

        sendBodyState.noBody = !res.hasBody();

        if (statusDropsConnection(res.getStatus())) {
            closeStreamOnEnd("status drops connection");
        }
        if (http.error) {
            closeStreamOnEnd("error");
        }

        MultiMap headers = res.getMimeHeaders();

        // Add date header
        if (headers.getHeader("Date") == null) {
            headers.setValue("Date").set(FastHttpDateFormat.getCurrentDate());
        }

        // Add server header
        if (http.serverHeader.length() > 0) {
            headers.setValue("Server").set(http.serverHeader);
        }

        // Decide on a transfer encoding for out.
        if (keepAlive()) { // request and user allows keep alive
            int cl = res.getContentLength();

            if (http10) {
                if (cl < 0 && !sendBodyState.noBody &&
                        sendBody.isAppendClosed()) {
                    // We can generate content-lenght
                    cl = sendBody.available();
                    res.setContentLength(cl);
                }
                if (cl < 0 && !sendBodyState.noBody) {
                    closeStreamOnEnd("HTTP/1.0 without content length");
                } else {
                    headers.setValue(CONNECTION).set(KEEPALIVE_S);
                }
            } else { // http11
                if (!sendBodyState.noBody) {
                    if (cl < 0) {
                        res.getMimeHeaders().setValue(TRANSFERENCODING).set(CHUNKED);
                    }
                }
            }
        } else {
            headers.setValue(CONNECTION).set(CLOSE);
            // since we close the connection - don't bother with
            // transfer encoding
            headers.remove(TRANSFERENCODING);
        }

        // Update our internal state based on headers we just set.
        processContentDelimitation(sendBodyState, res);
        updateCloseOnEnd(sendBodyState, http, sendBody);


        if (http.debug) {
            http.trace("Send response headers " + net);
        }
        if (net != null) {
            serialize(res, net.getOut());
        }

        if (http.outMessage.state == HttpMessage.State.HEAD) {
            http.outMessage.state = HttpMessage.State.BODY_DATA;
        }

        if (isDone(sendBodyState, sendBody)) {
            http.getOut().close();
        }

        if (net != null) {
            net.startSending();
        }
    }

    private void abort(String t) throws IOException {
        abort(activeHttp, t);
    }

    private void updateCloseOnEnd(BodyState bodys, HttpChannel http, IOBuffer body) {
        if (!bodys.isContentDelimited() && !bodys.noBody) {
            closeStreamOnEnd("not content delimited");
        }
    }

    /**
     * Disconnect abruptly - client closed, frame errors, etc
     * @param t
     * @throws IOException
     */
    public void abort(HttpChannel http, String t) throws IOException {
        if (switchedProtocol != null) {
            switchedProtocol.abort(http, t);
            return;
        }
        keepAlive = false;
        if (net != null ) {
            if (net.isOpen()) {
                net.close();
                net.startSending();
            }
        }
        if (http != null) {
            http.abort(t);
        }
    }

    /**
     * Update keepAlive based on Connection header and protocol.
     */
    private void updateKeepAlive(MultiMap headers, boolean request) {
        if (http09) {
            closeStreamOnEnd("http 0.9");
            return;
        }

        // TODO: also need to remove headers matching connection
        // ( like 'upgrade')

        CBuffer value = headers.getHeader(CONNECTION);
        // TODO: split it by space
        if (value != null) {
            value.toLower();
            if (value.indexOf(CLOSE) >= 0) {
                // 1.1 ( but we accept it for 1.0 too )
                closeStreamOnEnd("connection close");
            }
            if (http10 && value.indexOf(KEEPALIVE_S) < 0) {
                // Keep-Alive required for http/1.0
                closeStreamOnEnd("connection != keep alive");
            }
            // we have connection: keepalive, good
        } else {
            // no connection header - for 1.1 default is keepAlive,
            // for 10 it's close
            if (http10) {
                closeStreamOnEnd("http1.0 no connection header");
            }
        }
    }

    @Override
    public void startSending() throws IOException {
        if (switchedProtocol != null) {
            switchedProtocol.startSending();
            return;
        }

    }

    @Override
    public void startSending(HttpChannel http) throws IOException {
        if (switchedProtocol != null) {
            switchedProtocol.startSending(http);
            return;
        }
        http.send(); // if needed

        if (net == null) {
            return; // not connected yet.
        }

        if (net.getOut().isAppendClosed()) {
            abort("Net closed");
        } else {
            flushToNext(http.sendBody, net.getOut());
            net.startSending();
        }

    }

    protected void outClosed(HttpChannel http) throws IOException {
        if (switchedProtocol != null) {
            switchedProtocol.outClosed(http);
            return;
        }
        // TODO: move it ?
        if (sendBodyState.isContentDelimited() && !http.error) {
            if (!sendBodyState.chunked &&
                    sendBodyState.remaining - http.getOut().available() > 0) {
                http.abort("CLOSE CALLED WITHOUT FULL LEN");
            }
        }

    }

    @Override
    public void handleFlushed(IOChannel net) throws IOException {
        if (switchedProtocol != null) {
            switchedProtocol.handleFlushed(net);
            return;
        }
        if (activeHttp != null) {
            activeHttp.flushLock.signal(this);
            activeHttp.handleFlushed(this);
            if (activeHttp.sendBody.isClosedAndEmpty()) {
                activeHttp.handleEndSent();
            }
        }
    }


    private void flushToNext(IOBuffer body, IOBuffer out) throws IOException {

        synchronized (this) {
            // TODO: better head support
            if (sendBodyState.noBody) {
                for (int i = 0; i < body.getBufferCount(); i++) {
                    Object bc = body.peekBucket(i);
                    if (bc instanceof BBucket) {
                        ((BBucket) bc).release();
                    }
                }
                body.clear();
                return;
            }

            // TODO: only send < remainingWrite, if buffer
            // keeps changing after startWrite() is called (shouldn't)

            if (sendBodyState.chunked) {
                sendChunked(sendBodyState, body, out);
            } else if (sendBodyState.contentLength >= 0) {
                // content-length based
                sendContentLen(sendBodyState, body, out);
            } else {
                sendCloseDelimited(body, out);
            }
        }
    }

    private void sendCloseDelimited(IOBuffer body, IOBuffer out) throws IOException {
        // Close delimitation
        while (true) {
            Object bc = body.popFirst();
            if (bc == null) {
                break;
            }
            out.queue(bc);
        }
        if (body.isClosedAndEmpty()) {
            out.close(); // no content-delimitation
        }
    }

    /**
     * Convert the request to bytes, ready to send.
     */
    public static void serialize(HttpRequest req, IOBuffer rawSendBuffers2) throws IOException {
        rawSendBuffers2.append(req.method());
        rawSendBuffers2.append(BBuffer.SP);

        // TODO: encode or use decoded
        rawSendBuffers2.append(req.requestURI());
        if (req.queryString().length() > 0) {
            rawSendBuffers2.append("?");
            rawSendBuffers2.append(req.queryString());
        }

        rawSendBuffers2.append(BBuffer.SP);
        rawSendBuffers2.append(req.protocol());
        rawSendBuffers2.append(BBuffer.CRLF_BYTES);

        serializeHeaders(req.getMimeHeaders(), rawSendBuffers2);
    }

    /**
     * Convert the response to bytes, ready to send.
     */
    public static void serialize(HttpResponse res, IOBuffer rawSendBuffers2) throws IOException {

        rawSendBuffers2.append(res.protocol()).append(' ');
        String status = Integer.toString(res.getStatus());
        rawSendBuffers2.append(status).append(' ');
        if (res.getMessageBuffer().length() > 0) {
            rawSendBuffers2.append(res.getMessage());
        } else {
            rawSendBuffers2
                .append(res.getMessage(res.getStatus()));
        }
        rawSendBuffers2.append(BBuffer.CRLF_BYTES);
        // Headers
        serializeHeaders(res.getMimeHeaders(), rawSendBuffers2);
    }

    public static void serializeHeaders(MultiMap mimeHeaders, IOBuffer rawSendBuffers2) throws IOException {
        for (int i = 0; i < mimeHeaders.size(); i++) {
            CBuffer name = mimeHeaders.getName(i);
            CBuffer value = mimeHeaders.getValue(i);
            if (name.length() == 0 || value.length() == 0) {
                continue;
            }
            rawSendBuffers2.append(name);
            rawSendBuffers2.append(Http11Connection.COLON);
            rawSendBuffers2.append(value);
            rawSendBuffers2.append(BBuffer.CRLF_BYTES);
        }
        rawSendBuffers2.append(BBuffer.CRLF_BYTES);
    }


    private boolean sendContentLen(BodyState bodys, IOBuffer body, IOBuffer out) throws IOException {
        while (true) {
            BBucket bucket = body.peekFirst();
            if (bucket == null) {
                break;
            }
            int len = bucket.remaining();
            if (len <= bodys.remaining) {
                bodys.remaining -= len;
                bucket = body.popFirst();
                out.queue(bucket);
            } else {
                // Write over the end of the buffer !
                log.severe("write more than Content-Length");
                len = (int) bodys.remaining;
                // data between position and limit
                bucket = body.popLen((int) bodys.remaining);
                out.queue(bucket);
                while (bucket != null) {
                    bucket = body.popFirst();
                    if (bucket != null) {
                        bucket.release();
                    }
                }

                // forced close
                //close();
                bodys.remaining = 0;
                return true;
            }
        }
        if (body.isClosedAndEmpty()) {
            //http.rawSendBuffers.queue(IOBrigade.MARK);
            if (bodys.remaining > 0) {
                closeStreamOnEnd("sent more than content-length");
                log.severe("Content-Length > body");
            }
            return true;
        }
        return false;
    }

    private boolean sendChunked(BodyState bodys, IOBuffer body, IOBuffer out) throws IOException {
        int len = body.available();

        if (len > 0) {
            ByteBuffer sendChunkBuffer = chunk.prepareChunkHeader(len);
            bodys.remaining = len;
            out.queue(sendChunkBuffer);
            while (bodys.remaining > 0) {
                BBucket bc = body.popFirst();
                bodys.remaining -= bc.remaining();
                out.queue(bc);
            }
        }

        if (body.isClosedAndEmpty()) {
            synchronized(this) {
                if (!endSent) {
                    out.append(chunk.endChunk());
                    endSent = true;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    // used for chunk parsing/end
    ChunkState chunk = new ChunkState();
    ChunkState rchunk = new ChunkState();
    static final int NEED_MORE = -1;
    static final int ERROR = -4;
    static final int DONE = -5;


    static class ChunkState {
        static byte[] END_CHUNK_BYTES = {
            (byte) '\r', (byte) '\n',
            (byte) '0',
            (byte) '\r', (byte) '\n',
            (byte) '\r', (byte) '\n'};


        int partialChunkLen;
        boolean readDigit = false;
        boolean trailer = false;
        protected boolean needChunkCrlf = false;

        // Buffer used for chunk length conversion.
        protected byte[] sendChunkLength = new byte[10];

        /** End chunk marker - will include chunked end or empty */
        protected BBuffer endSendBuffer = BBuffer.wrapper();

        public ChunkState() {
            sendChunkLength[8] = (byte) '\r';
            sendChunkLength[9] = (byte) '\n';
        }

        void recycle() {
            partialChunkLen = 0;
            readDigit = false;
            trailer = false;
            needChunkCrlf = false;
            endSendBuffer.recycle();
        }

        /**
         * Parse the header of a chunk.
         * A chunk header can look like
         * A10CRLF
         * F23;chunk-extension to be ignoredCRLF
         * The letters before CRLF but after the trailer mark, must be valid hex digits,
         * we should not parse F23IAMGONNAMESSTHISUP34CRLF as a valid header
         * according to spec
         */
        int parseChunkHeader(IOBuffer buffer) throws IOException {
            if (buffer.peekFirst() == null) {
                return NEED_MORE;
            }
            if (needChunkCrlf) {
                // TODO: Trailing headers
                int c = buffer.read();
                if (c == BBuffer.CR) {
                    if (buffer.peekFirst() == null) {
                        return NEED_MORE;
                    }
                    c = buffer.read();
                }
                if (c == BBuffer.LF) {
                    needChunkCrlf = false;
                } else {
                    System.err.println("Bad CRLF " + c);
                    return ERROR;
                }
            }

            while (true) {
                if (buffer.peekFirst() == null) {
                    return NEED_MORE;
                }
                int c = buffer.read();

                if (c == BBuffer.CR) {
                    continue;
                } else if (c == BBuffer.LF) {
                    break;
                } else if (c == HttpChannel.SEMI_COLON) {
                    trailer = true;
                } else if (c == BBuffer.SP) {
                    // ignore
                } else if (trailer) {
                    // ignore
                } else {
                    //don't read data after the trailer
                    if (Hex.DEC[c] != -1) {
                        readDigit = true;
                        partialChunkLen *= 16;
                        partialChunkLen += Hex.DEC[c];
                    } else {
                        //we shouldn't allow invalid, non hex characters
                        //in the chunked header
                        log.info("Chunk parsing error1 " + c + " " + buffer);
                        //http.abort("Chunk error");
                        return ERROR;
                    }
                }
            }

            if (!readDigit) {
                log.info("Chunk parsing error2 " + buffer);
                return ERROR;
            }

            needChunkCrlf = true;  // next time I need to parse CRLF
            int result = partialChunkLen;
            partialChunkLen = 0;
            trailer = false;
            readDigit = false;
            return result;
        }


        ByteBuffer prepareChunkHeader(int current) {
            int pos = 7; // 8, 9 are CRLF
            while (current > 0) {
                int digit = current % 16;
                current = current / 16;
                sendChunkLength[pos--] = Hex.HEX[digit];
            }
            if (needChunkCrlf) {
                sendChunkLength[pos--] = (byte) '\n';
                sendChunkLength[pos--] = (byte) '\r';
            } else {
                needChunkCrlf = true;
            }
            // TODO: pool - this may stay in the queue while we flush more
            ByteBuffer chunkBB = ByteBuffer.allocate(16);
            chunkBB.put(sendChunkLength, pos + 1, 9 - pos);
            chunkBB.flip();
            return chunkBB;
        }

        public BBuffer endChunk() {
            if (! needChunkCrlf) {
                endSendBuffer.setBytes(END_CHUNK_BYTES, 2,
                        END_CHUNK_BYTES.length - 2); // CRLF
            } else { // 0
                endSendBuffer.setBytes(END_CHUNK_BYTES, 0,
                        END_CHUNK_BYTES.length);
            }
            return endSendBuffer;
        }
    }

    static class BodyState {
        /** response: HEAD or  1xx, 204, 304 status
         *  req: missing content-length or transfer-encoding
         */
        protected boolean noBody = false;
        protected boolean chunked = false;
        protected long contentLength = -1; // C-L header
        /** Bytes remaining in the current chunk or body ( if CL ) */
        protected long remaining = 0; // both chunked and C-L

        public void recycle() {
            chunked = false;
            remaining = 0;
            contentLength = -1;
            noBody = false;
        }
        public boolean isContentDelimited() {
            return chunked || contentLength >= 0;
        }

    }

    public String toString() {
        if (switchedProtocol != null) {
            return switchedProtocol.toString();
        }

        return (serverMode ? "SR " : "CL ") +
        (keepAlive() ? " KA " : "") +
        (headersReceived ? " HEAD " : "") +
        (bodyReceived ? " BODY " : "")
        ;
    }

    @Override
    public void handleConnected(IOChannel net) throws IOException {
        HttpChannel httpCh = activeHttp;

        if (!net.isOpen()) {
            httpCh.abort(net.lastException());
            return;
        }

        boolean ssl = httpCh.getRequest().isSecure();
        if (ssl) {
            String[] hostPort = httpCh.getTarget().split(":");

            IOChannel ch1 = httpConnector.sslProvider.channel(net,
                    hostPort[0], Integer.parseInt(hostPort[1]));
            //net.setHead(ch1);
            net = ch1;
        }
        if (httpConnector.debugHttp) {
            net = DumpChannel.wrap("Http-Client-", net);
        }

        setSink(net);

        sendRequest(httpCh);
    }

}
