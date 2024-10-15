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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

abstract class ConnectionSettingsBase<T extends Throwable> {

    private final Log log = LogFactory.getLog(ConnectionSettingsBase.class); // must not be static
    private final StringManager sm = StringManager.getManager(ConnectionSettingsBase.class);

    private final String connectionId;

    // Limits
    static final int MAX_WINDOW_SIZE = (1 << 31) - 1;
    static final int MIN_MAX_FRAME_SIZE = 1 << 14;
    static final int MAX_MAX_FRAME_SIZE = (1 << 24) - 1;
    static final long UNLIMITED = ((long) 1 << 32); // Use the maximum possible
    static final int MAX_HEADER_TABLE_SIZE = 1 << 16;

    // Defaults (defined by the specification)
    static final int DEFAULT_HEADER_TABLE_SIZE = Hpack.DEFAULT_TABLE_SIZE;
    static final long DEFAULT_MAX_CONCURRENT_STREAMS = UNLIMITED;
    static final int DEFAULT_INITIAL_WINDOW_SIZE = (1 << 16) - 1;
    static final int DEFAULT_MAX_FRAME_SIZE = MIN_MAX_FRAME_SIZE;
    static final long DEFAULT_MAX_HEADER_LIST_SIZE = 1 << 15;

    // Defaults (defined by Tomcat)
    static final long DEFAULT_NO_RFC7540_PRIORITIES = 1;

    Map<Setting,Long> current = new ConcurrentHashMap<>();
    Map<Setting,Long> pending = new ConcurrentHashMap<>();


    ConnectionSettingsBase(String connectionId) {
        this.connectionId = connectionId;
        // Set up the defaults
        current.put(Setting.HEADER_TABLE_SIZE, Long.valueOf(DEFAULT_HEADER_TABLE_SIZE));
        current.put(Setting.ENABLE_PUSH, Long.valueOf(0));
        current.put(Setting.MAX_CONCURRENT_STREAMS, Long.valueOf(DEFAULT_MAX_CONCURRENT_STREAMS));
        current.put(Setting.INITIAL_WINDOW_SIZE, Long.valueOf(DEFAULT_INITIAL_WINDOW_SIZE));
        current.put(Setting.MAX_FRAME_SIZE, Long.valueOf(DEFAULT_MAX_FRAME_SIZE));
        current.put(Setting.MAX_HEADER_LIST_SIZE, Long.valueOf(DEFAULT_MAX_HEADER_LIST_SIZE));
        current.put(Setting.NO_RFC7540_PRIORITIES, Long.valueOf(DEFAULT_NO_RFC7540_PRIORITIES));
    }


    final void set(Setting setting, long value) throws T {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("connectionSettings.debug", connectionId, getEndpointName(), setting,
                    Long.toString(value)));
        }

        switch (setting) {
            case HEADER_TABLE_SIZE:
                validateHeaderTableSize(value);
                break;
            case ENABLE_PUSH:
                validateEnablePush(value);
                break;
            case MAX_CONCURRENT_STREAMS:
                // No further validation required
                break;
            case INITIAL_WINDOW_SIZE:
                validateInitialWindowSize(value);
                break;
            case MAX_FRAME_SIZE:
                validateMaxFrameSize(value);
                break;
            case MAX_HEADER_LIST_SIZE:
                // No further validation required
                break;
            case NO_RFC7540_PRIORITIES:
                validateNoRfc7540Priorities(value);
                break;
            case UNKNOWN:
                // Unrecognised. Ignore it.
                return;
        }

        set(setting, Long.valueOf(value));
    }


    synchronized void set(Setting setting, Long value) {
        current.put(setting, value);
    }


    final int getHeaderTableSize() {
        return getMinInt(Setting.HEADER_TABLE_SIZE);
    }


    final boolean getEnablePush() {
        long result = getMin(Setting.ENABLE_PUSH);
        return result != 0;
    }


    final long getMaxConcurrentStreams() {
        return getMax(Setting.MAX_CONCURRENT_STREAMS);
    }


    final int getInitialWindowSize() {
        return getMaxInt(Setting.INITIAL_WINDOW_SIZE);
    }


    final int getMaxFrameSize() {
        return getMaxInt(Setting.MAX_FRAME_SIZE);
    }


    final long getMaxHeaderListSize() {
        return getMax(Setting.MAX_HEADER_LIST_SIZE);
    }


    private synchronized long getMin(Setting setting) {
        Long pendingValue = pending.get(setting);
        long currentValue = current.get(setting).longValue();
        if (pendingValue == null) {
            return currentValue;
        } else {
            return Long.min(pendingValue.longValue(), currentValue);
        }
    }


    private synchronized int getMinInt(Setting setting) {
        long result = getMin(setting);
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return (int) result;
        }
    }


    private synchronized long getMax(Setting setting) {
        Long pendingValue = pending.get(setting);
        long currentValue = current.get(setting).longValue();
        if (pendingValue == null) {
            return currentValue;
        } else {
            return Long.max(pendingValue.longValue(), currentValue);
        }
    }


    private synchronized int getMaxInt(Setting setting) {
        long result = getMax(setting);
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return (int) result;
        }
    }


    private void validateHeaderTableSize(long headerTableSize) throws T {
        if (headerTableSize > MAX_HEADER_TABLE_SIZE) {
            String msg = sm.getString("connectionSettings.headerTableSizeLimit", connectionId,
                    Long.toString(headerTableSize));
            throwException(msg, Http2Error.PROTOCOL_ERROR);
        }
    }


    private void validateEnablePush(long enablePush) throws T {
        // Can't be less than zero since the result of the byte->long conversion
        // will never be negative
        if (enablePush > 1) {
            String msg = sm.getString("connectionSettings.enablePushInvalid", connectionId, Long.toString(enablePush));
            throwException(msg, Http2Error.PROTOCOL_ERROR);
        }
    }


    private void validateInitialWindowSize(long initialWindowSize) throws T {
        if (initialWindowSize > MAX_WINDOW_SIZE) {
            String msg = sm.getString("connectionSettings.windowSizeTooBig", connectionId,
                    Long.toString(initialWindowSize), Long.toString(MAX_WINDOW_SIZE));
            throwException(msg, Http2Error.FLOW_CONTROL_ERROR);
        }
    }


    private void validateMaxFrameSize(long maxFrameSize) throws T {
        if (maxFrameSize < MIN_MAX_FRAME_SIZE || maxFrameSize > MAX_MAX_FRAME_SIZE) {
            String msg =
                    sm.getString("connectionSettings.maxFrameSizeInvalid", connectionId, Long.toString(maxFrameSize),
                            Integer.toString(MIN_MAX_FRAME_SIZE), Integer.toString(MAX_MAX_FRAME_SIZE));
            throwException(msg, Http2Error.PROTOCOL_ERROR);
        }
    }


    private void validateNoRfc7540Priorities(long noRfc7540Priorities) throws T {
        if (noRfc7540Priorities < 0 || noRfc7540Priorities > 1) {
            String msg = sm.getString("connectionSettings.noRfc7540PrioritiesInvalid", connectionId,
                    Long.toString(noRfc7540Priorities));
            throwException(msg, Http2Error.PROTOCOL_ERROR);
        }
    }


    abstract void throwException(String msg, Http2Error error) throws T;

    abstract String getEndpointName();
}
