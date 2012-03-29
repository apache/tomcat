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
package org.apache.coyote.spdy;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.AsyncContextCallback;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.coyote.http11.upgrade.UpgradeInbound;
import org.apache.tomcat.spdy.SpdyConnection;
import org.apache.tomcat.spdy.SpdyContext;
import org.apache.tomcat.spdy.SpdyFrame;
import org.apache.tomcat.spdy.SpdyStream;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * A spdy stream ( multiplexed over a spdy tcp connection ) processed by a
 * tomcat servlet.
 *
 * Based on the AJP processor.
 *
 */
public class SpdyProcessor extends AbstractProcessor<Object> implements
        Runnable {

    // TODO: handle input
    // TODO: recycle
    // TODO: swallow input ( recycle only after input close )
    // TODO: find a way to inject an OutputBuffer, or interecept close() -
    // so we can send FIN in the last data packet.

    private SpdyConnection spdy;

    // Associated spdy stream
    SpdyStream spdyStream;

    ByteChunk keyBuffer = new ByteChunk();

    boolean error = false;

    private boolean finished;

    SpdyFrame inFrame = null;

    boolean outClosed = false;

    boolean outCommit = false;

    public SpdyProcessor(SpdyConnection spdy, AbstractEndpoint endpoint) {
        super(endpoint);

        this.spdy = spdy;
        request.setInputBuffer(new LiteInputBuffer());
        response.setOutputBuffer(new LiteOutputBuffer());

    }

    class LiteInputBuffer implements InputBuffer {
        @Override
        public int doRead(ByteChunk bchunk, Request request) throws IOException {
            if (inFrame == null) {
                // blocking
                inFrame = spdyStream.getDataFrame(endpoint.getSoTimeout());
            }
            if (inFrame == null) {
                return -1; // timeout
            }
            if (inFrame.remaining() == 0 && inFrame.isHalfClose()) {
                return -1;
            }

            int rd = Math.min(inFrame.remaining(), bchunk.getBytes().length);
            System.arraycopy(inFrame.data, inFrame.off, bchunk.getBytes(),
                    bchunk.getStart(), rd);
            inFrame.advance(rd);
            if (inFrame.remaining() == 0) {
                spdy.getSpdyContext().releaseFrame(inFrame);
                if (!inFrame.isHalfClose()) {
                    inFrame = null;
                }
            }
            bchunk.setEnd(bchunk.getEnd() + rd);
            return rd;
        }
    }

    final class LiteOutputBuffer implements OutputBuffer {
        long byteCount;

        @Override
        public int doWrite(org.apache.tomcat.util.buf.ByteChunk chunk,
                Response response) throws IOException {
            if (!response.isCommitted()) {

                // Send the connector a request for commit. The connector should
                // then validate the headers, send them (using sendHeader) and
                // set the filters accordingly.
                response.action(ActionCode.COMMIT, null);

            }
            spdyStream.sendDataFrame(chunk.getBuffer(), chunk.getStart(),
                    chunk.getLength(), false);
            byteCount += chunk.getLength();
            return chunk.getLength();
        }

        @Override
        public long getBytesWritten() {
            return byteCount;
        }
    }

    void onRequest() {
        Executor exec = spdy.getSpdyContext().getExecutor();
        exec.execute(this);
    }

    /**
     * Execute the request.
     */
    @Override
    public void run() {
        RequestInfo rp = request.getRequestProcessor();
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            adapter.service(request, response);
        } catch (InterruptedIOException e) {
            error = true;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // log.error(sm.getString("ajpprocessor.request.process"), t);
            // 500 - Internal Server Error
            t.printStackTrace();
            response.setStatus(500);
            adapter.log(request, response, 0);
            error = true;
        }

        // TODO: async, etc ( detached mode - use a special light protocol)

        // Finish the response if not done yet
        if (!finished) {
            try {
                finish();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                error = true;
            }
        }

        if (error) {
            response.setStatus(500);
        }

        request.updateCounters();
        rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);
        // TODO: recycle
    }

    private void finish() {
        if (!response.isCommitted()) {
            response.action(ActionCode.COMMIT, response);
        }

        if (finished)
            return;

        finished = true;

        response.finish();
    }

    static final byte[] EMPTY = new byte[0];

    // Processor implementation

    private void maybeCommit() {
        if (outCommit) {
            return;
        }
        if (!response.isCommitted()) {
            // Validate and write response headers
            try {
                sendSynReply();
            } catch (IOException e) {
                e.printStackTrace();
                // Set error flag
                error = true;
                return;
            }
        }
    }

    @Override
    public void action(ActionCode actionCode, Object param) {
        if (SpdyContext.debug) {
            // System.err.println(actionCode);
        }

        // TODO: async

        if (actionCode == ActionCode.COMMIT) {
            maybeCommit();
        } else if (actionCode == ActionCode.CLIENT_FLUSH) {
            maybeCommit();

            // try {
            // flush(true);
            // } catch (IOException e) {
            // // Set error flag
            // error = true;
            // }

        } else if (actionCode == ActionCode.DISABLE_SWALLOW_INPUT) {
            // TODO: Do not swallow request input but
            // make sure we are closing the connection
            error = true;

        } else if (actionCode == ActionCode.CLOSE) {
            if (outClosed) {
                return;
            }
            outClosed = true;
            // Close
            // End the processing of the current request, and stop any further
            // transactions with the client
            maybeCommit();

            try {
                spdyStream.sendDataFrame(EMPTY, 0, 0, true);
            } catch (IOException e) {
                // Set error flag
                e.printStackTrace();
                error = true;
            }

        } else if (actionCode == ActionCode.REQ_SSL_ATTRIBUTE) {

            // if (!certificates.isNull()) {
            // ByteChunk certData = certificates.getByteChunk();
            // X509Certificate jsseCerts[] = null;
            // ByteArrayInputStream bais =
            // new ByteArrayInputStream(certData.getBytes(),
            // certData.getStart(),
            // certData.getLength());
            // // Fill the elements.
            // try {
            // CertificateFactory cf;
            // if (clientCertProvider == null) {
            // cf = CertificateFactory.getInstance("X.509");
            // } else {
            // cf = CertificateFactory.getInstance("X.509",
            // clientCertProvider);
            // }
            // while(bais.available() > 0) {
            // X509Certificate cert = (X509Certificate)
            // cf.generateCertificate(bais);
            // if(jsseCerts == null) {
            // jsseCerts = new X509Certificate[1];
            // jsseCerts[0] = cert;
            // } else {
            // X509Certificate [] temp = new
            // X509Certificate[jsseCerts.length+1];
            // System.arraycopy(jsseCerts,0,temp,0,jsseCerts.length);
            // temp[jsseCerts.length] = cert;
            // jsseCerts = temp;
            // }
            // }
            // } catch (java.security.cert.CertificateException e) {
            // getLog().error(sm.getString("ajpprocessor.certs.fail"), e);
            // return;
            // } catch (NoSuchProviderException e) {
            // getLog().error(sm.getString("ajpprocessor.certs.fail"), e);
            // return;
            // }
            // request.setAttribute(SSLSupport.CERTIFICATE_KEY, jsseCerts);
            // }

        } else if (actionCode == ActionCode.REQ_HOST_ATTRIBUTE) {

            // Get remote host name using a DNS resolution
            if (request.remoteHost().isNull()) {
                try {
                    request.remoteHost().setString(
                            InetAddress.getByName(
                                    request.remoteAddr().toString())
                                    .getHostName());
                } catch (IOException iex) {
                    // Ignore
                }
            }
        } else if (actionCode == ActionCode.REQ_LOCALPORT_ATTRIBUTE) {
            String configured = (String) endpoint.getAttribute("proxyPort");
            int localPort = 0;
            if (configured != null) {
                localPort = Integer.parseInt(configured);
            } else {
                localPort = endpoint.getPort();
            }
            request.setLocalPort(localPort);

        } else if (actionCode == ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE) {
            InetAddress localAddress = endpoint.getAddress();
            String localIp = localAddress == null ? null : localAddress
                    .getHostAddress();
            if (localIp == null) {
                localIp = (String) endpoint.getAttribute("proxyIP");
            }
            if (localIp == null) {
                localIp = "127.0.0.1";
            }
            request.localAddr().setString(localIp);

        } else if (actionCode == ActionCode.REQ_HOST_ADDR_ATTRIBUTE) {
            InetAddress localAddress = endpoint.getAddress();
            String localH = localAddress == null ? null : localAddress
                    .getCanonicalHostName();
            if (localH == null) {
                localH = (String) endpoint.getAttribute("proxyName");
            }
            if (localH == null) {
                localH = "localhost";
            }

            request.localAddr().setString(localH);

        } else if (actionCode == ActionCode.REQ_SET_BODY_REPLAY) {

            // // Set the given bytes as the content
            // ByteChunk bc = (ByteChunk) param;
            // int length = bc.getLength();
            // bodyBytes.setBytes(bc.getBytes(), bc.getStart(), length);
            // request.setContentLength(length);
            // first = false;
            // empty = false;
            // replay = true;

        } else if (actionCode == ActionCode.ASYNC_START) {
            asyncStateMachine.asyncStart((AsyncContextCallback) param);
        } else if (actionCode == ActionCode.ASYNC_DISPATCHED) {
            asyncStateMachine.asyncDispatched();
        } else if (actionCode == ActionCode.ASYNC_TIMEOUT) {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(asyncStateMachine.asyncTimeout());
        } else if (actionCode == ActionCode.ASYNC_RUN) {
            asyncStateMachine.asyncRun((Runnable) param);
        } else if (actionCode == ActionCode.ASYNC_ERROR) {
            asyncStateMachine.asyncError();
        } else if (actionCode == ActionCode.ASYNC_IS_STARTED) {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncStarted());
        } else if (actionCode == ActionCode.ASYNC_IS_DISPATCHING) {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncDispatching());
        } else if (actionCode == ActionCode.ASYNC_IS_ASYNC) {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsync());
        } else if (actionCode == ActionCode.ASYNC_IS_TIMINGOUT) {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncTimingOut());
        } else {
            // TODO:
            // actionInternal(actionCode, param);
        }

    }


    /**
     * When committing the response, we have to validate the set of headers, as
     * well as setup the response filters.
     */
    protected void sendSynReply() throws IOException {

        response.setCommitted(true);

        // Special headers
        MimeHeaders headers = response.getMimeHeaders();
        String contentType = response.getContentType();
        if (contentType != null) {
            headers.setValue("Content-Type").setString(contentType);
        }
        String contentLanguage = response.getContentLanguage();
        if (contentLanguage != null) {
            headers.setValue("Content-Language").setString(contentLanguage);
        }
        long contentLength = response.getContentLengthLong();
        if (contentLength >= 0) {
            headers.setValue("Content-Length").setLong(contentLength);
        }

        sendResponseHead();
    }

    private void sendResponseHead() throws IOException {
        SpdyFrame rframe = spdy.getFrame(SpdyConnection.TYPE_SYN_REPLY);
        rframe.associated = 0;

        MimeHeaders headers = response.getMimeHeaders();
        for (int i = 0; i < headers.size(); i++) {
            MessageBytes mb = headers.getName(i);
            mb.toBytes();
            ByteChunk bc = mb.getByteChunk();
            byte[] bb = bc.getBuffer();
            for (int j = bc.getStart(); j < bc.getEnd(); j++) {
                bb[j] = (byte) Ascii.toLower(bb[j]);
            }
            // TODO: filter headers: Connection, Keep-Alive, Proxy-Connection,
            rframe.headerName(bc.getBuffer(), bc.getStart(), bc.getLength());
            mb = headers.getValue(i);
            mb.toBytes();
            bc = mb.getByteChunk();
            rframe.headerValue(bc.getBuffer(), bc.getStart(), bc.getLength());
        }
        if (response.getStatus() == 0) {
            rframe.addHeader(SpdyFrame.STATUS, SpdyFrame.OK200);
        } else {
            // HTTP header contents
            String message = null;
            if (org.apache.coyote.Constants.USE_CUSTOM_STATUS_MSG_IN_HEADER
                    && HttpMessages.isSafeInHttpHeader(response.getMessage())) {
                message = response.getMessage();
            }
            if (message == null) {
                message = HttpMessages.getMessage(response.getStatus());
            }
            if (message == null) {
                // mod_jk + httpd 2.x fails with a null status message - bug
                // 45026
                message = Integer.toString(response.getStatus());
            }
            // TODO: optimize
            String status = response.getStatus() + " " + message;
            byte[] statusB = status.getBytes();
            rframe.headerName(SpdyFrame.STATUS, 0, SpdyFrame.STATUS.length);
            rframe.headerValue(statusB, 0, statusB.length);
        }
        rframe.addHeader(SpdyFrame.VERSION, SpdyFrame.HTTP11);

        rframe.streamId = spdyStream.reqFrame.streamId;
        spdy.send(rframe, spdyStream);
        // we can't reuse the frame - it'll be queued, the coyote processor
        // may be reused as well.
        outCommit = true;
    }

    @Override
    public boolean isComet() {
        return false;
    }

    @Override
    public SocketState process(SocketWrapper<Object> socket)
            throws IOException {
        throw new IOException("Unimplemented");
    }

    @Override
    public SocketState event(SocketStatus status) throws IOException {
        System.err.println("EVENT: " + status);
        return null;
    }

    @Override
    public SocketState asyncDispatch(SocketStatus status) {
        System.err.println("ASYNC DISPATCH: " + status);
        return null;
    }

    @Override
    public boolean isUpgrade() {
        return false;
    }

    @Override
    public SocketState upgradeDispatch() throws IOException {
        return null;
    }

    public void onSynStream(SpdyStream str) throws IOException {
        this.spdyStream = str;
        SpdyFrame frame = str.reqFrame;
        // We need to make a copy - the frame buffer will be reused.
        // We use the 'wrap' methods of MimeHeaders - which should be
        // lighter on mem in some cases.
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);

        // Request received.
        MimeHeaders mimeHeaders = request.getMimeHeaders();

        for (int i = 0; i < frame.nvCount; i++) {
            int nameLen = frame.read16();
            if (nameLen > frame.remaining()) {
                throw new IOException("Name too long");
            }

            keyBuffer.setBytes(frame.data, frame.off, nameLen);
            if (keyBuffer.equals("method")) {
                frame.advance(nameLen);
                int valueLen = frame.read16();
                if (valueLen > frame.remaining()) {
                    throw new IOException("Name too long");
                }
                request.method().setBytes(frame.data, frame.off, valueLen);
                frame.advance(valueLen);
            } else if (keyBuffer.equals("url")) {
                frame.advance(nameLen);
                int valueLen = frame.read16();
                if (valueLen > frame.remaining()) {
                    throw new IOException("Name too long");
                }
                request.requestURI().setBytes(frame.data, frame.off, valueLen);
                if (SpdyContext.debug) {
                    System.err.println("URL= " + request.requestURI());
                }
                frame.advance(valueLen);
            } else if (keyBuffer.equals("version")) {
                frame.advance(nameLen);
                int valueLen = frame.read16();
                if (valueLen > frame.remaining()) {
                    throw new IOException("Name too long");
                }
                frame.advance(valueLen);
            } else {
                MessageBytes value = mimeHeaders.addValue(frame.data,
                        frame.off, nameLen);
                frame.advance(nameLen);
                int valueLen = frame.read16();
                if (valueLen > frame.remaining()) {
                    throw new IOException("Name too long");
                }
                value.setBytes(frame.data, frame.off, valueLen);
                frame.advance(valueLen);
            }
        }

        onRequest();
    }

    @Override
    public void recycle(boolean socketClosing) {
    }

    @Override
    public void setSslSupport(SSLSupport sslSupport) {
    }

    @Override
    public UpgradeInbound getUpgradeInbound() {
        return null;
    }

}
