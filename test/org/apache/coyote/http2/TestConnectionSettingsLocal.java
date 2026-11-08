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

import org.junit.Test;

public class TestConnectionSettingsLocal {

    @Test(expected = IllegalArgumentException.class)
    public void testInitialWindowSizeNegative() {
        // Use of a negative value is not permitted by the specification and
        // can not be represented in a SETTINGS frame
        ConnectionSettingsLocal settings = new ConnectionSettingsLocal("0");
        settings.set(Setting.INITIAL_WINDOW_SIZE, -1);
    }


    @Test
    public void testInitialWindowSizeZero() {
        // A value of zero is permitted by the specification
        ConnectionSettingsLocal settings = new ConnectionSettingsLocal("0");
        settings.set(Setting.INITIAL_WINDOW_SIZE, 0);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testInitialWindowSizeTooBig() {
        ConnectionSettingsLocal settings = new ConnectionSettingsLocal("0");
        settings.set(Setting.INITIAL_WINDOW_SIZE, ConnectionSettingsBase.MAX_WINDOW_SIZE + 1L);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testMaxConcurrentStreamsNegative() {
        // Use of a negative value is not permitted by the specification and
        // can not be represented in a SETTINGS frame
        ConnectionSettingsLocal settings = new ConnectionSettingsLocal("0");
        settings.set(Setting.MAX_CONCURRENT_STREAMS, -1);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testMaxConcurrentStreamsTooBig() {
        // Values larger than an unsigned 32-bit integer can not be
        // represented in a SETTINGS frame
        ConnectionSettingsLocal settings = new ConnectionSettingsLocal("0");
        settings.set(Setting.MAX_CONCURRENT_STREAMS, ConnectionSettingsBase.MAX_SETTING_VALUE + 1);
    }


    @Test
    public void testMaxConcurrentStreamsMaxValue() {
        // The largest value that can be represented in a SETTINGS frame is
        // permitted
        ConnectionSettingsLocal settings = new ConnectionSettingsLocal("0");
        settings.set(Setting.MAX_CONCURRENT_STREAMS, ConnectionSettingsBase.MAX_SETTING_VALUE);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testMaxHeaderListSizeNegative() {
        ConnectionSettingsLocal settings = new ConnectionSettingsLocal("0");
        settings.set(Setting.MAX_HEADER_LIST_SIZE, -1);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testMaxHeaderListSizeTooBig() {
        ConnectionSettingsLocal settings = new ConnectionSettingsLocal("0");
        settings.set(Setting.MAX_HEADER_LIST_SIZE, ConnectionSettingsBase.MAX_SETTING_VALUE + 1);
    }
}
