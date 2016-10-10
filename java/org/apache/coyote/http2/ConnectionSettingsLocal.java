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

/**
 * Represents the local connection settings i.e. the settings the client is
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
class ConnectionSettingsLocal extends ConnectionSettingsBase<IllegalArgumentException> {

    private boolean sendInProgress = false;


    ConnectionSettingsLocal(String connectionId) {
        super(connectionId);
    }


    @Override
    final synchronized void set(Setting setting, Long value) {
        checkSend();
        if (current.get(setting).longValue() == value.longValue()) {
            pending.remove(setting);
        } else {
            pending.put(setting, value);
        }
    }


    final synchronized byte[] getSettingsFrameForPending() {
        checkSend();
        int payloadSize = pending.size() * 6;
        byte[] result = new byte[9 + payloadSize];

        ByteUtil.setThreeBytes(result, 0, payloadSize);
        result[3] = FrameType.SETTINGS.getIdByte();
        // No flags
        // Stream is zero
        // Payload
        int pos = 9;
        for (Map.Entry<Setting,Long> setting : pending.entrySet()) {
            ByteUtil.setTwoBytes(result, pos, setting.getKey().getId());
            pos += 2;
            ByteUtil.setFourBytes(result, pos, setting.getValue().longValue());
            pos += 4;
        }
        sendInProgress = true;
        return result;
    }


    final synchronized boolean ack() {
        if (sendInProgress) {
            sendInProgress = false;
            current.putAll(pending);
            pending.clear();
            return true;
        } else {
            return false;
        }
    }


    private void checkSend() {
        if (sendInProgress) {
            // Coding error. No need for i18n
            throw new IllegalStateException();
        }
    }


    @Override
    final void throwException(String msg, Http2Error error) throws IllegalArgumentException {
        throw new IllegalArgumentException(msg);
    }
}
