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

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the local connection settingsL i.e. the settings the client is
 * expected to use when communicating with the server. There will be a delay
 * between calling a setter and the setting taking effect at the client. When a
 * setter is called, the new value is added to the set of pending settings. Once
 * the ACK is received, the new value is moved to the current settings. While
 * waiting for the ACK, the getters will return the most lenient / generous /
 * relaxed of the current setting and the pending setting. This class does not
 * validate the values passed to the setters. If an invalid value is used the
 * client will respond (almost certainly by closing the connection) as defined
 * in the HTTP/2 specification.
 */
public class ConnectionSettingsLocal {

    private static final Integer KEY_HEADER_TABLE_SIZE = Integer.valueOf(1);
    private static final Integer KEY_ENABLE_PUSH = Integer.valueOf(2);
    private static final Integer KEY_MAX_CONCURRENT_STREAMS = Integer.valueOf(3);
    private static final Integer KEY_INITIAL_WINDOW_SIZE = Integer.valueOf(4);
    private static final Integer KEY_MAX_FRAME_SIZE = Integer.valueOf(5);
    private static final Integer KEY_MAX_HEADER_LIST_SIZE = Integer.valueOf(6);

    private static final Long DEFAULT_HEADER_TABLE_SIZE =
            Long.valueOf(ConnectionSettingsRemote.DEFAULT_HEADER_TABLE_SIZE);
    private static final Long DEFAULT_ENABLE_PUSH = Long.valueOf(1);
    private static final Long DEFAULT_MAX_CONCURRENT_STREAMS =
            Long.valueOf(ConnectionSettingsRemote.UNLIMITED);
    private static final Long DEFAULT_INITIAL_WINDOW_SIZE =
            Long.valueOf(ConnectionSettingsRemote.DEFAULT_INITIAL_WINDOW_SIZE);
    private static final Long DEFAULT_MAX_FRAME_SIZE =
            Long.valueOf(ConnectionSettingsRemote.DEFAULT_MAX_FRAME_SIZE);
    private static final Long DEFAULT_MAX_HEADER_LIST_SIZE =
            Long.valueOf(ConnectionSettingsRemote.UNLIMITED);

    boolean sendInProgress = false;

    private Map<Integer,Long> current = new HashMap<>();
    private Map<Integer,Long> pending = new HashMap<>();


    public ConnectionSettingsLocal() {
        // Set up the defaults
        current.put(KEY_HEADER_TABLE_SIZE,      DEFAULT_HEADER_TABLE_SIZE);
        current.put(KEY_ENABLE_PUSH,            DEFAULT_ENABLE_PUSH);
        current.put(KEY_MAX_CONCURRENT_STREAMS, DEFAULT_MAX_CONCURRENT_STREAMS);
        current.put(KEY_INITIAL_WINDOW_SIZE,    DEFAULT_INITIAL_WINDOW_SIZE);
        current.put(KEY_MAX_FRAME_SIZE,         DEFAULT_MAX_FRAME_SIZE);
        current.put(KEY_MAX_HEADER_LIST_SIZE,   DEFAULT_MAX_HEADER_LIST_SIZE);
    }


    private synchronized void set(Integer key, long value) {
        checkSend();
        if (current.get(key).longValue() == value) {
            pending.remove(key);
        } else {
            pending.put(key, Long.valueOf(value));
        }
    }


    private synchronized long getMin(Integer key) {
        Long pendingValue = pending.get(key);
        long currentValue = current.get(key).longValue();
        if (pendingValue == null) {
            return currentValue;
        } else {
            return Long.min(pendingValue.longValue(), currentValue);
        }
    }


    private int getMinInt(Integer key) {
        long result = getMin(key);
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return (int) result;
        }
    }


    private synchronized long getMax(Integer key) {
        Long pendingValue = pending.get(key);
        long currentValue = current.get(key).longValue();
        if (pendingValue == null) {
            return currentValue;
        } else {
            return Long.max(pendingValue.longValue(), currentValue);
        }
    }


    private int getMaxInt(Integer key) {
        long result = getMax(key);
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return (int) result;
        }
    }


    synchronized byte[] getSettingsFrameForPending() {
        checkSend();
        int payloadSize = pending.size() * 6;
        byte[] result = new byte[9 + payloadSize];

        ByteUtil.setThreeBytes(result, 0, payloadSize);
        result[3] = FrameType.SETTINGS.getIdByte();
        // No flags
        // Stream is zero
        // Payload
        int pos = 9;
        for (Map.Entry<Integer,Long> setting : pending.entrySet()) {
            ByteUtil.setTwoBytes(result, pos, setting.getKey().intValue());
            pos += 2;
            ByteUtil.setFourBytes(result, pos, setting.getValue().longValue());
            pos += 4;
        }
        sendInProgress = true;
        return result;
    }


    synchronized void ack() {
        if (sendInProgress) {
            sendInProgress = false;
            current.putAll(pending);
            pending.clear();
        } else {
            // Unexpected ACK. Log it?
            // TODO
        }
    }


    private void checkSend() {
        if (sendInProgress) {
            // Coding error. No need for i18n
            throw new IllegalStateException();
        }
    }


    public int getHeaderTableSize() {
        return getMinInt(KEY_HEADER_TABLE_SIZE);
    }
    public void setHeaderTableSize(long headerTableSize) {
        set(KEY_HEADER_TABLE_SIZE, headerTableSize);
    }


    public boolean getEnablePush() {
        long result = getMin(KEY_ENABLE_PUSH);
        return result != 0;
    }
    public void setEnablePush(long enablePush) {
        set(KEY_ENABLE_PUSH, enablePush);
    }


    public long getMaxConcurrentStreams() {
        return getMax(KEY_MAX_CONCURRENT_STREAMS);
    }
    public void setMaxConcurrentStreams(long maxConcurrentStreams) {
        set(KEY_MAX_CONCURRENT_STREAMS, maxConcurrentStreams);
    }


    public int getInitialWindowSize() {
        return getMaxInt(KEY_INITIAL_WINDOW_SIZE);
    }
    public void setInitialWindowSize(long initialWindowSize) {
        set(KEY_INITIAL_WINDOW_SIZE, initialWindowSize);
    }


    public int getMaxFrameSize() {
        return getMaxInt(KEY_MAX_FRAME_SIZE);
    }
    public void setMaxFrameSize(long maxFrameSize) {
        set(KEY_MAX_FRAME_SIZE, maxFrameSize);
    }


    public long getMaxHeaderListSize() {
        return getMax(KEY_MAX_HEADER_LIST_SIZE);
    }
    public void setMaxHeaderListSize(long maxHeaderListSize) {
        set(KEY_MAX_HEADER_LIST_SIZE, maxHeaderListSize);
    }
}
