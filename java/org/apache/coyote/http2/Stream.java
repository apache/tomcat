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

    private volatile long flowControlWindowSize;


    public Stream(Integer identifier, Http2UpgradeHandler handler) {
        super(identifier);
        this.handler = handler;
        setParentStream(handler);
        flowControlWindowSize = handler.getRemoteSettings().getInitialWindowSize();
        coyoteResponse.setOutputBuffer(new StreamOutputBuffer());
    }


    public void incrementWindowSize(int windowSizeIncrement) {
        flowControlWindowSize += windowSizeIncrement;
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
        // TODO
        handler.addWrite("DATA");
    }


    @Override
    protected final Log getLog() {
        return log;
    }


    @Override
    protected final int getConnectionId() {
        return getParentStream().getConnectionId();
    }


    public Request getCoyoteRequest() {
        return coyoteRequest;
    }


    public Response getCoyoteResponse() {
        return coyoteResponse;
    }


    private class StreamOutputBuffer implements OutputBuffer {

        private volatile long written = 0;

        @Override
        public int doWrite(ByteChunk chunk) throws IOException {
            // TODO Blocking. Write to buffer. flushData() if full.
            log.debug("Write [" + chunk.getLength() + "] bytes");
            written += chunk.getLength();
            return chunk.getLength();
        }

        @Override
        public long getBytesWritten() {
            return written;
        }
    }
}
