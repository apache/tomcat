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
    // TODO: The maximum allowed in a settings frame as 2^32 (unsigned)
    private static final int UNLIMITED = (1 << 31) -1; // Use the maximum possible
    private static final int MAX_WINDOW_SIZE = (1 << 31) - 1;
    private static final int MIN_MAX_FRAME_SIZE = 1 << 14;
    private static final int MAX_MAX_FRAME_SIZE = (1 << 24) - 1;

    private volatile int headerTableSize = 4096;
    private volatile int enablePush = 1;
    private volatile int maxConcurrentStreams = UNLIMITED;
    private volatile int initialWindowSize = DEFAULT_WINDOW_SIZE;
    private volatile int maxFrameSize = MIN_MAX_FRAME_SIZE;
    private volatile int maxHeaderListSize = UNLIMITED;

    public void set(int parameterId, int value) throws IOException {
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
    public void setHeaderTableSize(int headerTableSize) {
        this.headerTableSize = headerTableSize;
    }


    public int getEnablePush() {
        return enablePush;
    }
    public void setEnablePush(int enablePush) throws IOException {
        // Can't be less than zero since the result of the byte->long conversion
        // will never be negative
        if (enablePush > 1) {
            throw new Http2Exception(sm.getString("connectionSettings.enablePushInvalid",
                    Long.toString(enablePush)), 0, Http2Exception.PROTOCOL_ERROR);
        }
        this.enablePush = enablePush;
    }


    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }
    public void setMaxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }


    public int getInitialWindowSize() {
        return initialWindowSize;
    }
    public void setInitialWindowSize(int initialWindowSize) throws IOException {
        if (initialWindowSize > MAX_WINDOW_SIZE) {
            throw new Http2Exception(sm.getString("connectionSettings.windowSizeTooBig",
                    Long.toString(initialWindowSize), Long.toString(MAX_WINDOW_SIZE)),
                    0, Http2Exception.PROTOCOL_ERROR);
        }
        this.initialWindowSize = initialWindowSize;
    }


    public int getMaxFrameSize() {
        return maxFrameSize;
    }
    public void setMaxFrameSize(int maxFrameSize) throws IOException {
        if (maxFrameSize < MIN_MAX_FRAME_SIZE || maxFrameSize > MAX_MAX_FRAME_SIZE) {
            throw new Http2Exception(sm.getString("connectionSettings.maxFrameSizeInvalid",
                    Long.toString(maxFrameSize), Long.toString(MIN_MAX_FRAME_SIZE),
                    Long.toString(MAX_MAX_FRAME_SIZE)), 0, Http2Exception.PROTOCOL_ERROR);
        }
        this.maxFrameSize = maxFrameSize;
    }


    public int getMaxHeaderListSize() {
        return maxHeaderListSize;
    }
    public void setMaxHeaderListSize(int maxHeaderListSize) {
        this.maxHeaderListSize = maxHeaderListSize;
    }
}
