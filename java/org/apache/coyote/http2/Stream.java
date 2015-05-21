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
import java.nio.ByteBuffer;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.res.StringManager;

public class Stream extends AbstractStream implements HeaderEmitter {

    private static final Log log = LogFactory.getLog(Stream.class);
    private static final StringManager sm = StringManager.getManager(Stream.class);

    private final Http2UpgradeHandler handler;
    private final Request coyoteRequest = new Request();
    private final Response coyoteResponse = new Response();
    private final StreamOutputBuffer outputBuffer = new StreamOutputBuffer();


    public Stream(Integer identifier, Http2UpgradeHandler handler) {
        super(identifier);
        this.handler = handler;
        setParentStream(handler);
        setWindowSize(handler.getRemoteSettings().getInitialWindowSize());
        coyoteResponse.setOutputBuffer(outputBuffer);
    }


    @Override
    public void incrementWindowSize(int windowSizeIncrement) {
        // If this is zero then any thread that has been trying to write for
        // this stream will be waiting. Notify that thread it can continue. Use
        // notify all even though only one thread is waiting to be on the safe
        // side.
        boolean notify = getWindowSize() == 0;
        super.incrementWindowSize(windowSizeIncrement);
        if (notify) {
            synchronized (this) {
                notifyAll();
            }
        }
    }


    @Override
    public void emitHeader(String name, String value, boolean neverIndex) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.header.debug",
                    Long.toString(getConnectionId()), getIdentifier(), name, value));
        }

        switch(name) {
        case ":method": {
            coyoteRequest.method().setString(value);
            break;
        }
        case ":path": {
            coyoteRequest.requestURI().setString(value);
            // TODO: This is almost certainly wrong and needs to be decoded
            coyoteRequest.decodedURI().setString(value);
            break;
        }
        case ":authority": {
            int i = value.lastIndexOf(':');
            if (i > -1) {
                coyoteRequest.serverName().setString(value.substring(0, i));
                coyoteRequest.setServerPort(Integer.parseInt(value.substring(i + 1)));
            } else {
                coyoteRequest.serverName().setString(value);
                boolean secure = Boolean.parseBoolean(handler.getProperty("secure"));
                if (secure) {
                    coyoteRequest.setServerPort(443);
                } else {
                    coyoteRequest.setServerPort(80);
                }
            }
            break;
        }
        default: {
            // Assume other HTTP header
            coyoteRequest.getMimeHeaders().addValue(name).setString(value);
        }
        }
    }


    void writeHeaders() {
        try {
            handler.writeHeaders(this, coyoteResponse);
        } catch (IOException e) {
            // TODO Handle this
            e.printStackTrace();
        }
    }


    void flushData() {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.write",
                    Long.toString(getConnectionId()), getIdentifier()));
        }
        try {
            outputBuffer.flush();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    @Override
    protected final Log getLog() {
        return log;
    }


    @Override
    protected final int getConnectionId() {
        return getParentStream().getConnectionId();
    }


    Request getCoyoteRequest() {
        return coyoteRequest;
    }


    Response getCoyoteResponse() {
        return coyoteResponse;
    }


    StreamOutputBuffer getOutputBuffer() {
        return outputBuffer;
    }


    class StreamOutputBuffer implements OutputBuffer {

        private volatile ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
        private volatile long written = 0;
        private volatile boolean finished = false;

        @Override
        public int doWrite(ByteChunk chunk) throws IOException {
            if (finished) {
                // TODO i18n
                throw new IllegalStateException();
            }
            int len = chunk.getLength();
            int offset = 0;
            while (len > 0) {
                int thisTime = Math.min(buffer.remaining(), len);
                buffer.put(chunk.getBytes(), chunk.getOffset() + offset, thisTime);
                offset += thisTime;
                len -= thisTime;
                if (!buffer.hasRemaining()) {
                    flush();
                }
            }
            written += offset;
            return offset;
        }

        public void flush() throws IOException {
            if (!coyoteResponse.isCommitted()) {
                coyoteResponse.sendHeaders();
            }
            if (buffer.position() == 0) {
                // Buffer is empty. Nothing to do.
                return;
            }
            buffer.flip();
            int left = buffer.remaining();
            int thisWriteStream;
            while (left > 0) {
                // Flow control for the Stream
                do {
                    thisWriteStream = reserveWindowSize(left);
                    if (thisWriteStream < 1) {
                        // Need to block until a WindowUpdate message is
                        // processed for this stream;
                        synchronized (this) {
                            try {
                                wait();
                            } catch (InterruptedException e) {
                                // TODO. Possible shutdown?
                            }
                        }
                    }
                } while (thisWriteStream < 1);

                // Flow control for the connection
                int thisWrite;
                do {
                    thisWrite = handler.reserveWindowSize(thisWriteStream);
                    if (thisWrite < 1) {
                        // TODO Flow control when connection window is exhausted
                    }
                } while (thisWrite < 1);

                incrementWindowSize(-thisWrite);
                handler.incrementWindowSize(-thisWrite);

                // Do the write
                handler.writeBody(Stream.this, buffer, thisWrite);
                left -= thisWrite;
                buffer.position(buffer.position() + thisWrite);
            }
            buffer.clear();
        }

        @Override
        public long getBytesWritten() {
            return written;
        }

        public void finished() {
            finished = true;
        }

        public boolean isFinished() {
            return finished;
        }

        /**
         * @return <code>true</code> if it is certain that the associated
         *         response has no body.
         */
        public boolean hasNoBody() {
            return ((written == 0) && finished);
        }
    }
}
