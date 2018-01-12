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
package org.apache.coyote.http2;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.ContainerThreadMarker;
import org.apache.coyote.ErrorState;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SendfileState;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

class StreamProcessor extends AbstractProcessor {

    private static final Log log = LogFactory.getLog(StreamProcessor.class);
    private static final StringManager sm = StringManager.getManager(StreamProcessor.class);

    private final Http2UpgradeHandler handler;
    private final Stream stream;
    private SendfileData sendfileData = null;
    private SendfileState sendfileState = null;


    StreamProcessor(Http2UpgradeHandler handler, Stream stream, Adapter adapter,
            SocketWrapperBase<?> socketWrapper) {
        super(adapter, stream.getCoyoteRequest(), stream.getCoyoteResponse());
        this.handler = handler;
        this.stream = stream;
        setSocketWrapper(socketWrapper);
    }


    final void process(SocketEvent event) {
        try {
            // FIXME: the regular processor syncs on socketWrapper, but here this deadlocks
            synchronized (this) {
                // HTTP/2 equivalent of AbstractConnectionHandler#process() without the
                // socket <-> processor mapping
                ContainerThreadMarker.set();
                SocketState state = SocketState.CLOSED;
                try {
                    state = process(socketWrapper, event);

                    if (state == SocketState.CLOSED) {
                        if (!getErrorState().isConnectionIoAllowed()) {
                            ConnectionException ce = new ConnectionException(sm.getString(
                                    "streamProcessor.error.connection", stream.getConnectionId(),
                                    stream.getIdentifier()), Http2Error.INTERNAL_ERROR);
                            stream.close(ce);
                        } else if (!getErrorState().isIoAllowed()) {
                            StreamException se = new StreamException(sm.getString(
                                    "streamProcessor.error.stream", stream.getConnectionId(),
                                    stream.getIdentifier()), Http2Error.INTERNAL_ERROR,
                                    stream.getIdentifier().intValue());
                            stream.close(se);
                        }
                    }
                } catch (Exception e) {
                    ConnectionException ce = new ConnectionException(sm.getString(
                            "streamProcessor.error.connection", stream.getConnectionId(),
                            stream.getIdentifier()), Http2Error.INTERNAL_ERROR);
                    ce.initCause(e);
                    stream.close(ce);
                } finally {
                    ContainerThreadMarker.clear();
                }
            }
        } finally {
            handler.executeQueuedStream();
        }
    }


    @Override
    protected final void prepareResponse() throws IOException {
        response.setCommitted(true);
        if (handler.hasAsyncIO() && handler.getProtocol().getUseSendfile()) {
            prepareSendfile();
        }
        prepareHeaders(request, response, sendfileData == null, handler.getProtocol(), stream);
        stream.writeHeaders();
    }


    private void prepareSendfile() {
        String fileName = (String) stream.getCoyoteRequest().getAttribute(
                org.apache.coyote.Constants.SENDFILE_FILENAME_ATTR);
        if (fileName != null) {
            sendfileData = new SendfileData();
            sendfileData.path = new File(fileName).toPath();
            sendfileData.pos = ((Long) stream.getCoyoteRequest().getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_START_ATTR)).longValue();
            sendfileData.end = ((Long) stream.getCoyoteRequest().getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_END_ATTR)).longValue();
            sendfileData.left = sendfileData.end - sendfileData.pos;
            sendfileData.stream = stream;
        }
    }


    // Static so it can be used by Stream to build the MimeHeaders required for
    // an ACK. For that use case coyoteRequest, protocol and stream will be null.
    static void prepareHeaders(Request coyoteRequest, Response coyoteResponse, boolean noSendfile,
            Http2Protocol protocol, Stream stream) {
        MimeHeaders headers = coyoteResponse.getMimeHeaders();
        int statusCode = coyoteResponse.getStatus();

        // Add the pseudo header for status
        headers.addValue(":status").setString(Integer.toString(statusCode));

        // Check to see if a response body is present
        if (!(statusCode < 200 || statusCode == 205 || statusCode == 304)) {
            String contentType = coyoteResponse.getContentType();
            if (contentType != null) {
                headers.setValue("content-type").setString(contentType);
            }
            String contentLanguage = coyoteResponse.getContentLanguage();
            if (contentLanguage != null) {
                headers.setValue("content-language").setString(contentLanguage);
            }
        }

        // Add date header unless it is an informational response or the
        // application has already set one
        if (statusCode >= 200 && headers.getValue("date") == null) {
            headers.addValue("date").setString(FastHttpDateFormat.getCurrentDate());
        }

        // Compression can't be used with sendfile
        if (noSendfile && protocol != null &&
                protocol.useCompression(coyoteRequest, coyoteResponse)) {
            // Enable compression. Headers will have been set. Need to configure
            // output filter at this point.
            stream.addOutputFilter(new GzipOutputFilter());
        }
    }


    @Override
    protected final void finishResponse() throws IOException {
        sendfileState = handler.processSendfile(sendfileData);
        if (!(sendfileState == SendfileState.PENDING)) {
            stream.getOutputBuffer().end();
        }
    }


    @Override
    protected final void ack() {
        if (!response.isCommitted() && request.hasExpectation()) {
            try {
                stream.writeAck();
            } catch (IOException ioe) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
            }
        }
    }


    @Override
    protected final void flush() throws IOException {
        stream.getOutputBuffer().flush();
    }


    @Override
    protected final int available(boolean doRead) {
        return stream.getInputBuffer().available();
    }


    @Override
    protected final void setRequestBody(ByteChunk body) {
        stream.getInputBuffer().insertReplayedBody(body);
        try {
            stream.receivedEndOfStream();
        } catch (ConnectionException e) {
            // Exception will not be thrown in this case
        }
    }


    @Override
    protected final void setSwallowResponse() {
        // NO-OP
    }


    @Override
    protected final void disableSwallowRequest() {
        // NO-OP
        // HTTP/2 has to swallow any input received to ensure that the flow
        // control windows are correctly tracked.
    }


    @Override
    protected void processSocketEvent(SocketEvent event, boolean dispatch) {
        if (dispatch) {
            handler.processStreamOnContainerThread(this, event);
        } else {
            this.process(event);
        }
    }


    @Override
    protected final boolean isRequestBodyFullyRead() {
        return stream.getInputBuffer().isRequestBodyFullyRead();
    }


    @Override
    protected final void registerReadInterest() {
        stream.getInputBuffer().registerReadInterest();
    }


    @Override
    protected final boolean isReady() {
        return stream.isReady();
    }


    @Override
    protected final void executeDispatches() {
        Iterator<DispatchType> dispatches = getIteratorAndClearDispatches();
        synchronized (this) {
            /*
             * TODO Check if this sync is necessary.
             *      Compare with superclass that uses SocketWrapper
             */
            while (dispatches != null && dispatches.hasNext()) {
                DispatchType dispatchType = dispatches.next();
                processSocketEvent(dispatchType.getSocketStatus(), false);
            }
        }
    }


    @Override
    protected final boolean isPushSupported() {
        return stream.isPushSupported();
    }


    @Override
    protected final void doPush(Request pushTarget) {
        try {
            stream.push(pushTarget);
        } catch (IOException ioe) {
            setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
            response.setErrorException(ioe);
        }
    }


    @Override
    protected boolean isTrailerFieldsReady() {
        return stream.isTrailerFieldsReady();
    }


    @Override
    protected boolean isTrailerFieldsSupported() {
        return stream.isTrailerFieldsSupported();
    }


    @Override
    public final void recycle() {
        // StreamProcessor instances are not re-used.
        // Clear fields that can be cleared to aid GC and trigger NPEs if this
        // is reused
        setSocketWrapper(null);
    }


    @Override
    protected final Log getLog() {
        return log;
    }


    @Override
    public final void pause() {
        // NO-OP. Handled by the Http2UpgradeHandler
    }


    @Override
    public final SocketState service(SocketWrapperBase<?> socket) throws IOException {
        try {
            adapter.service(request, response);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("streamProcessor.service.error"), e);
            }
            response.setStatus(500);
            setErrorState(ErrorState.CLOSE_NOW, e);
        }

        if (sendfileState == SendfileState.PENDING) {
            return SocketState.SENDFILE;
        } else if (getErrorState().isError()) {
            action(ActionCode.CLOSE, null);
            request.updateCounters();
            return SocketState.CLOSED;
        } else if (isAsync()) {
            return SocketState.LONG;
        } else {
            action(ActionCode.CLOSE, null);
            request.updateCounters();
            return SocketState.CLOSED;
        }
    }


    @Override
    protected final boolean flushBufferedWrite() throws IOException {
        if (stream.flush(false)) {
            // The buffer wasn't fully flushed so re-register the
            // stream for write. Note this does not go via the
            // Response since the write registration state at
            // that level should remain unchanged. Once the buffer
            // has been emptied then the code below will call
            // dispatch() which will enable the
            // Response to respond to this event.
            if (stream.isReady()) {
                // Unexpected
                throw new IllegalStateException();
            }
            return true;
        }
        return false;
    }


    @Override
    protected final SocketState dispatchEndRequest() {
        return SocketState.CLOSED;
    }
}
