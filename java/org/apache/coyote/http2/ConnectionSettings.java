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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class ConnectionSettings {

    private final Log log = LogFactory.getLog(ConnectionSettings.class);
    private final StringManager sm = StringManager.getManager(ConnectionSettings.class);

    public static final int DEFAULT_WINDOW_SIZE = (1 << 16) - 1;
    private static final long UNLIMITED = ((long)1 << 32); // Use the maximum possible
    private static final int MAX_WINDOW_SIZE = (1 << 31) - 1;

    private static final int MIN_MAX_FRAME_SIZE = 1 << 14;
    private static final int MAX_MAX_FRAME_SIZE = (1 << 24) - 1;
    static final int DEFAULT_MAX_FRAME_SIZE = MIN_MAX_FRAME_SIZE;

    static final int DEFAULT_HEADER_TABLE_SIZE = 4096;
    private volatile int headerTableSize = DEFAULT_HEADER_TABLE_SIZE;

    private volatile boolean enablePush = true;
    private volatile long maxConcurrentStreams = UNLIMITED;
    private volatile int initialWindowSize = DEFAULT_WINDOW_SIZE;
    private volatile int maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    private volatile long maxHeaderListSize = UNLIMITED;

    public void set(int parameterId, long value) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("connectionSettings.debug",
                    Integer.toString(parameterId), Long.toString(value)));
        }

        switch(parameterId) {
        case 1:
            setHeaderTableSize(value);
            break;
        case 2:
            setEnablePush(value);
            break;
        case 3:
            setMaxConcurrentStreams(value);
            break;
        case 4:
            setInitialWindowSize(value);
            break;
        case 5:
            setMaxFrameSize(value);
            break;
        case 6:
            setMaxHeaderListSize(value);
            break;
        default:
            // Unrecognised. Ignore it.
            log.warn(sm.getString("connectionSettings.unknown",
                    Integer.toString(parameterId), Long.toString(value)));
        }
    }


    public int getHeaderTableSize() {
        return headerTableSize;
    }
    public void setHeaderTableSize(long headerTableSize) throws IOException {
        // Need to put a sensible limit on this. Start with 16k (default is 4k)
        if (headerTableSize > (16 * 1024)) {
            throw new Http2Exception(sm.getString("connectionSettings.headerTableSizeLimit",
                    Long.toString(headerTableSize)), 0, Http2Exception.PROTOCOL_ERROR);
        }
        this.headerTableSize = (int) headerTableSize;
    }


    public boolean getEnablePush() {
        return enablePush;
    }
    public void setEnablePush(long enablePush) throws IOException {
        // Can't be less than zero since the result of the byte->long conversion
        // will never be negative
        if (enablePush > 1) {
            throw new Http2Exception(sm.getString("connectionSettings.enablePushInvalid",
                    Long.toString(enablePush)), 0, Http2Exception.PROTOCOL_ERROR);
        }
        this.enablePush = (enablePush  == 1);
    }


    public long getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }
    public void setMaxConcurrentStreams(long maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }


    public int getInitialWindowSize() {
        return initialWindowSize;
    }
    public void setInitialWindowSize(long initialWindowSize) throws IOException {
        if (initialWindowSize > MAX_WINDOW_SIZE) {
            throw new Http2Exception(sm.getString("connectionSettings.windowSizeTooBig",
                    Long.toString(initialWindowSize), Long.toString(MAX_WINDOW_SIZE)),
                    0, Http2Exception.PROTOCOL_ERROR);
        }
        this.initialWindowSize = (int) initialWindowSize;
    }


    public int getMaxFrameSize() {
        return maxFrameSize;
    }
    public void setMaxFrameSize(long maxFrameSize) throws IOException {
        if (maxFrameSize < MIN_MAX_FRAME_SIZE || maxFrameSize > MAX_MAX_FRAME_SIZE) {
            throw new Http2Exception(sm.getString("connectionSettings.maxFrameSizeInvalid",
                    Long.toString(maxFrameSize), Integer.toString(MIN_MAX_FRAME_SIZE),
                    Integer.toString(MAX_MAX_FRAME_SIZE)), 0, Http2Exception.PROTOCOL_ERROR);
        }
        this.maxFrameSize = (int) maxFrameSize;
    }


    public long getMaxHeaderListSize() {
        return maxHeaderListSize;
    }
    public void setMaxHeaderListSize(long maxHeaderListSize) {
        this.maxHeaderListSize = maxHeaderListSize;
    }
}
