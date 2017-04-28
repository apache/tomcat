/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class WsFrameClient extends WsFrameBase {

    private final Log log = LogFactory.getLog(WsFrameClient.class);
    private static final StringManager sm = StringManager.getManager(WsFrameClient.class);

    private final AsyncChannelWrapper channel;
    private final CompletionHandler<Integer, Void> handler;
    // Not final as it may need to be re-sized
    private volatile ByteBuffer response;

    public WsFrameClient(ByteBuffer response, AsyncChannelWrapper channel, WsSession wsSession,
            Transformation transformation) {
        super(wsSession, transformation);
        this.response = response;
        this.channel = channel;
        this.handler = new WsFrameClientCompletionHandler();
    }


    void startInputProcessing() {
        try {
            processSocketRead();
        } catch (IOException e) {
            close(e);
        }
    }


    private void processSocketRead() throws IOException {
        while (true) {
            switch (getReadState()) {
            case WAITING:
                if (!changeReadState(ReadState.WAITING, ReadState.PROCESSING)) {
                    continue;
                }
                while (response.hasRemaining()) {
                    if (isSuspended()) {
                        if (!changeReadState(ReadState.SUSPENDING_PROCESS, ReadState.SUSPENDED)) {
                            continue;
                        }
                        // There is still data available in the response buffer
                        // Return here so that the response buffer will not be
                        // cleared and there will be no data read from the
                        // socket. Thus when the read operation is resumed first
                        // the data left in the response buffer will be consumed
                        // and then a new socket read will be performed
                        return;
                    }
                    inputBuffer.mark();
                    inputBuffer.position(inputBuffer.limit()).limit(inputBuffer.capacity());

                    int toCopy = Math.min(response.remaining(), inputBuffer.remaining());

                    // Copy remaining bytes read in HTTP phase to input buffer used by
                    // frame processing

                    int orgLimit = response.limit();
                    response.limit(response.position() + toCopy);
                    inputBuffer.put(response);
                    response.limit(orgLimit);

                    inputBuffer.limit(inputBuffer.position()).reset();

                    // Process the data we have
                    processInputBuffer();
                }
                response.clear();

                // Get some more data
                if (isOpen()) {
                    channel.read(response, null, handler);
                } else {
                    changeReadState(ReadState.CLOSING);
                }
                return;
            case SUSPENDING_WAIT:
                if (!changeReadState(ReadState.SUSPENDING_WAIT, ReadState.SUSPENDED)) {
                    continue;
                }
                return;
            default:
                throw new IllegalStateException(
                        sm.getString("wsFrameServer.illegalReadState", getReadState()));
            }
        }
    }


    private final void close(Throwable t) {
        changeReadState(ReadState.CLOSING);
        CloseReason cr;
        if (t instanceof WsIOException) {
            cr = ((WsIOException) t).getCloseReason();
        } else {
            cr = new CloseReason(CloseCodes.CLOSED_ABNORMALLY, t.getMessage());
        }

        try {
            wsSession.close(cr);
        } catch (IOException ignore) {
            // Ignore
        }
    }


    @Override
    protected boolean isMasked() {
        // Data is from the server so it is not masked
        return false;
    }


    @Override
    protected Log getLog() {
        return log;
    }

    private class WsFrameClientCompletionHandler implements CompletionHandler<Integer, Void> {

        @Override
        public void completed(Integer result, Void attachment) {
            if (result.intValue() == -1) {
                // BZ 57762. A dropped connection will get reported as EOF
                // rather than as an error so handle it here.
                if (isOpen()) {
                    // No close frame was received
                    close(new EOFException());
                }
                // No data to process
                return;
            }
            response.flip();
            doResumeProcessing(true);
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            if (exc instanceof ReadBufferOverflowException) {
                // response will be empty if this exception is thrown
                response = ByteBuffer
                        .allocate(((ReadBufferOverflowException) exc).getMinBufferSize());
                response.flip();
                doResumeProcessing(false);
            } else {
                close(exc);
            }
        }

        private void doResumeProcessing(boolean checkOpenOnError) {
            while (true) {
                switch (getReadState()) {
                case PROCESSING:
                    if (!changeReadState(ReadState.PROCESSING, ReadState.WAITING)) {
                        continue;
                    }
                    resumeProcessing(checkOpenOnError);
                    return;
                case SUSPENDING_PROCESS:
                    if (!changeReadState(ReadState.SUSPENDING_PROCESS, ReadState.SUSPENDED)) {
                        continue;
                    }
                    return;
                default:
                    throw new IllegalStateException(
                            sm.getString("wsFrame.illegalReadState", getReadState()));
                }
            }
        }
    }


    @Override
    protected void resumeProcessing() {
        resumeProcessing(true);
    }

    private void resumeProcessing(boolean checkOpenOnError) {
        try {
            processSocketRead();
        } catch (IOException e) {
            if (checkOpenOnError) {
                // Only send a close message on an IOException if the client
                // has not yet received a close control message from the server
                // as the IOException may be in response to the client
                // continuing to send a message after the server sent a close
                // control message.
                if (isOpen()) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("wsFrameClient.ioe"), e);
                    }
                    close(e);
                }
            } else {
                close(e);
            }
        }
    }
}
