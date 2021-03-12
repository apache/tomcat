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

import java.io.IOException;
import java.util.Iterator;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.ContainerThreadMarker;
import org.apache.coyote.ContinueResponseTiming;
import org.apache.coyote.ErrorState;
import org.apache.coyote.Request;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.Response;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

class StreamProcessor extends AbstractProcessor {

    private static final Log log = LogFactory.getLog(StreamProcessor.class);
    private static final StringManager sm = StringManager.getManager(StreamProcessor.class);

    private final Http2UpgradeHandler handler;
    private final Stream stream;


    StreamProcessor(Http2UpgradeHandler handler, Stream stream, Adapter adapter,
            SocketWrapperBase<?> socketWrapper) {
        super(socketWrapper.getEndpoint(), stream.getCoyoteRequest(), stream.getCoyoteResponse());
        this.handler = handler;
        this.stream = stream;
        setAdapter(adapter);
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

                    if (state == SocketState.LONG) {
                        handler.getProtocol().getHttp11Protocol().addWaitingProcessor(this);
                    } else if (state == SocketState.CLOSED) {
                        handler.getProtocol().getHttp11Protocol().removeWaitingProcessor(this);
                        if (!stream.isInputFinished() && getErrorState().isIoAllowed()) {
                            // The request has been processed but the request body has not been
                            // fully read. This typically occurs when Tomcat rejects an upload
                            // of some form (e.g. PUT or POST). Need to tell the client not to
                            // send any more data on this stream (reset).
                            StreamException se = new StreamException(
                                    sm.getString("streamProcessor.cancel", stream.getConnectionId(),
                                            stream.getIdAsString()), Http2Error.CANCEL, stream.getIdAsInt());
                            stream.close(se);
                        } else if (!getErrorState().isConnectionIoAllowed()) {
                            ConnectionException ce = new ConnectionException(sm.getString(
                                    "streamProcessor.error.connection", stream.getConnectionId(),
                                    stream.getIdAsString()), Http2Error.INTERNAL_ERROR);
                            stream.close(ce);
                        } else if (!getErrorState().isIoAllowed()) {
                            StreamException se = stream.getResetException();
                            if (se == null) {
                                se = new StreamException(sm.getString(
                                        "streamProcessor.error.stream", stream.getConnectionId(),
                                        stream.getIdAsString()), Http2Error.INTERNAL_ERROR,
                                        stream.getIdAsInt());
                            }
                            stream.close(se);
                        } else {
                            if (!stream.isActive()) {
                                // stream.close() will call recycle so only need it here
                                stream.recycle();
                            }
                        }
                    }
                } catch (Exception e) {
                    String msg = sm.getString("streamProcessor.error.connection",
                            stream.getConnectionId(), stream.getIdAsString());
                    if (log.isDebugEnabled()) {
                        log.debug(msg, e);
                    }
                    ConnectionException ce = new ConnectionException(msg, Http2Error.INTERNAL_ERROR, e);
                    stream.close(ce);
                    state = SocketState.CLOSED;
                } finally {
                    if (state == SocketState.CLOSED) {
                        recycle();
                    }
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
        prepareHeaders(request, response, handler.getProtocol(), stream);
        stream.writeHeaders();
    }


    // Static so it can be used by Stream to build the MimeHeaders required for
    // an ACK. For that use case coyoteRequest, protocol and stream will be null.
    static void prepareHeaders(Request coyoteRequest, Response coyoteResponse,
            Http2Protocol protocol, Stream stream) {
        MimeHeaders headers = coyoteResponse.getMimeHeaders();
        int statusCode = coyoteResponse.getStatus();

        // Add the pseudo header for status
        headers.addValue(":status").setString(Integer.toString(statusCode));


        // Compression can't be used with sendfile
        // Need to check for compression (and set headers appropriately) before
        // adding headers below
        if (protocol != null && protocol.useCompression(coyoteRequest, coyoteResponse)) {
            // Enable compression. Headers will have been set. Need to configure
            // output filter at this point.
            stream.addOutputFilter(new GzipOutputFilter());
        }

        // Check to see if a response body is present
        if (!(statusCode < 200 || statusCode == 204 || statusCode == 205 || statusCode == 304)) {
            String contentType = coyoteResponse.getContentType();
            if (contentType != null) {
                headers.setValue("content-type").setString(contentType);
            }
            String contentLanguage = coyoteResponse.getContentLanguage();
            if (contentLanguage != null) {
                headers.setValue("content-language").setString(contentLanguage);
            }
            // Add a content-length header if a content length has been set unless
            // the application has already added one
            long contentLength = coyoteResponse.getContentLengthLong();
            if (contentLength != -1 && headers.getValue("content-length") == null) {
                headers.addValue("content-length").setLong(contentLength);
            }
        } else {
            if (statusCode == 205) {
                // RFC 7231 requires the server to explicitly signal an empty
                // response in this case
                coyoteResponse.setContentLength(0);
            } else {
                coyoteResponse.setContentLength(-1);
            }
        }

        // Add date header unless it is an informational response or the
        // application has already set one
        if (statusCode >= 200 && headers.getValue("date") == null) {
            headers.addValue("date").setString(FastHttpDateFormat.getCurrentDate());
        }
    }


    @Override
    protected final void finishResponse() throws IOException {
        stream.getOutputBuffer().end();
    }


    @Override
    protected final void ack(ContinueResponseTiming continueResponseTiming) {
        // Only try and send the ACK for ALWAYS or if the timing of the request
        // to send the ACK matches the current configuration.
        if (continueResponseTiming == ContinueResponseTiming.ALWAYS ||
                continueResponseTiming == handler.getProtocol().getContinueResponseTimingInternal()) {
            if (!response.isCommitted() && request.hasExpectation()) {
                try {
                    stream.writeAck();
                } catch (IOException ioe) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
                }
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
    protected final boolean isReadyForRead() {
        return stream.getInputBuffer().isReadyForRead();
    }


    @Override
    protected final boolean isRequestBodyFullyRead() {
        return stream.getInputBuffer().isRequestBodyFullyRead();
    }


    @Override
    protected final void registerReadInterest() {
        // Should never be called for StreamProcessor as isReadyForRead() is
        // overridden
        throw new UnsupportedOperationException();
    }


    @Override
    protected final boolean isReadyForWrite() {
        return stream.isReadyForWrite();
    }


    @Override
    protected final void executeDispatches() {
        Iterator<DispatchType> dispatches = getIteratorAndClearDispatches();
        /*
         * Compare with superclass that uses SocketWrapper
         * A sync is not necessary here as the window sizes are updated with
         * syncs before the dispatches are executed and it is the window size
         * updates that need to be complete before the dispatch executes.
         */
        while (dispatches != null && dispatches.hasNext()) {
            DispatchType dispatchType = dispatches.next();
            /*
             * Dispatch on new thread.
             * Firstly, this avoids a deadlock on the SocketWrapper as Streams
             * being processed by container threads lock the SocketProcessor
             * before they lock the SocketWrapper which is the opposite order to
             * container threads processing via Http2UpgrageHandler.
             * Secondly, this code executes after a Window update has released
             * one or more Streams. By dispatching each Stream to a dedicated
             * thread, those Streams may progress concurrently.
             */
            processSocketEvent(dispatchType.getSocketStatus(), true);
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
    protected Object getConnectionID() {
        return stream.getConnectionId();
    }


    @Override
    protected Object getStreamID() {
        return stream.getIdAsString().toString();
    }


    @Override
    public final void recycle() {
        // StreamProcessor instances are not re-used.

        // Calling removeRequestProcessor even though the RequestProcesser was
        // never added will add the values from the RequestProcessor to the
        // running total for the GlobalRequestProcessor
        RequestGroupInfo global = handler.getProtocol().getGlobal();
        if (global != null) {
            global.removeRequestProcessor(request.getRequestProcessor());
        }

        // Clear fields that can be cleared to aid GC and trigger NPEs if this
        // is reused
        setSocketWrapper(null);
        setAdapter(null);
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

        if (getErrorState().isError()) {
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
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("streamProcessor.flushBufferedWrite.entry",
                    stream.getConnectionId(), stream.getIdAsString()));
        }
        if (stream.flush(false)) {
            // The buffer wasn't fully flushed so re-register the
            // stream for write. Note this does not go via the
            // Response since the write registration state at
            // that level should remain unchanged. Once the buffer
            // has been emptied then the code below will call
            // dispatch() which will enable the
            // Response to respond to this event.
            if (stream.isReadyForWrite()) {
                // Unexpected
                throw new IllegalStateException();
            }
            return true;
        }
        return false;
    }


    @Override
    protected final SocketState dispatchEndRequest() throws IOException {
        return SocketState.CLOSED;
    }
}
