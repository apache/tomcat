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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestHttp2Timeouts extends Http2TestBase {

    @Override
    @Before
    public void http2Connect() throws Exception {
        super.http2Connect();
    }


    /*
     * Simple request won't fill buffer so timeout will occur in Tomcat internal
     * code during response completion.
     */
    @Test
    public void testClientWithEmptyWindow() throws Exception {
        sendSettings(0, false, new SettingValue(Setting.INITIAL_WINDOW_SIZE.getId(), 0));
        sendSimpleGetRequest(3);

        // Settings
        parser.readFrame();
        // Headers
        parser.readFrame();

        output.clearTrace();

        parser.readFrame();
        Assert.assertEquals("3-RST-[11]\n", output.getTrace());
    }


    /*
     * Large request will fill buffer so timeout will occur in application code
     * during response write (when Tomcat commits the response and flushes the
     * buffer as a result of the buffer filling).
     */
    @Test
    public void testClientWithEmptyWindowLargeResponse() throws Exception {
        sendSettings(0, false, new SettingValue(Setting.INITIAL_WINDOW_SIZE.getId(), 0));
        sendLargeGetRequest(3);

        // Settings
        parser.readFrame();
        // Headers
        parser.readFrame();

        output.clearTrace();

        parser.readFrame();
        Assert.assertEquals("3-RST-[11]\n", output.getTrace());
    }


    /*
     * Timeout with app reading request body directly.
     */
    @Test
    public void testClientPostsNoBody() throws Exception {
        sendSimplePostRequest(3,  null,  false);

        // Headers
        parser.readFrame();
        output.clearTrace();

        parser.readFrame();

        Assert.assertEquals("3-RST-[11]\n", output.getTrace());
    }


    /*
     * Timeout with app processing parameters.
     */
    @Test
    public void testClientPostsNoParameters() throws Exception {
        sendParameterPostRequest(3, null, null, 10, false);

        // Headers
        parser.readFrame();
        output.clearTrace();

        parser.readFrame();

        Assert.assertEquals("3-RST-[11]\n", output.getTrace());
    }
}
