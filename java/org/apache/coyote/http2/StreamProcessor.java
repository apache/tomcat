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

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.ContainerThreadMarker;
import org.apache.coyote.ErrorState;
import org.apache.coyote.PushToken;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class StreamProcessor extends AbstractProcessor implements Runnable {

    private static final Log log = LogFactory.getLog(StreamProcessor.class);
    private static final StringManager sm = StringManager.getManager(StreamProcessor.class);

    private final Http2UpgradeHandler handler;
    private final Stream stream;


    public StreamProcessor(Http2UpgradeHandler handler, Stream stream, Adapter adapter, SocketWrapperBase<?> socketWrapper) {
        super(stream.getCoyoteRequest(), stream.getCoyoteResponse());
        this.handler = handler;
        this.stream = stream;
        setAdapter(adapter);
        setSocketWrapper(socketWrapper);
    }


    @Override
    public void run() {
        try {
            // FIXME: the regular processor syncs on socketWrapper, but here this deadlocks
            synchronized (this) {
                // HTTP/2 equivalent of AbstractConnectionHandler#process() without the
                // socket <-> processor mapping
                ContainerThreadMarker.set();
                SocketState state = SocketState.CLOSED;
                try {
                    state = process(socketWrapper, SocketEvent.OPEN_READ);

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
        stream.writeHeaders();
    }


    @Override
    protected final void finishResponse() throws IOException {
        stream.getOutputBuffer().close();
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
        stream.flushData();
    }


    @Override
    protected final int available(boolean doRead) {
        return stream.getInputBuffer().available();
    }


    @Override
    protected final void setRequestBody(ByteChunk body) {
        stream.getInputBuffer().insertReplayedBody(body);
        stream.receivedEndOfStream();
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
    protected final boolean isRequestBodyFullyRead() {
        return stream.getInputBuffer().isRequestBodyFullyRead();
    }


    @Override
    protected final void registerReadInterest() {
        stream.getInputBuffer().registerReadInterest();
    }


    @Override
    protected final boolean isReady() {
        return stream.getOutputBuffer().isReady();
    }


    @Override
    protected final void executeDispatches(SocketWrapperBase<?> wrapper) {
        wrapper.getEndpoint().getExecutor().execute(this);
    }


    @Override
    protected final boolean isPushSupported() {
        return stream.isPushSupported();
    }


    @Override
    protected final void doPush(PushToken pushToken) {
        try {
            pushToken.setResult(stream.push(pushToken.getPushTarget()));
        } catch (IOException ioe) {
            setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
            response.setErrorException(ioe);
        }
    }


    @Override
    public void recycle() {
        // StreamProcessor instances are not re-used.
        // Clear fields that can be cleared to aid GC and trigger NPEs if this
        // is reused
        setSocketWrapper(null);
        setAdapter(null);
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    public void pause() {
        // NO-OP. Handled by the Http2UpgradeHandler
    }


    @Override
    public SocketState service(SocketWrapperBase<?> socket) throws IOException {
        try {
            adapter.service(request, response);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("streamProcessor.service.error"), e);
            }
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
    protected boolean flushBufferedWrite() throws IOException {
        if (stream.getOutputBuffer().flush(false)) {
            // The buffer wasn't fully flushed so re-register the
            // stream for write. Note this does not go via the
            // Response since the write registration state at
            // that level should remain unchanged. Once the buffer
            // has been emptied then the code below will call
            // dispatch() which will enable the
            // Response to respond to this event.
            if (stream.getOutputBuffer().isReady()) {
                // Unexpected
                throw new IllegalStateException();
            }
            return true;
        }
        return false;
    }


    @Override
    protected SocketState dispatchEndRequest() {
        return SocketState.CLOSED;
    }
}
