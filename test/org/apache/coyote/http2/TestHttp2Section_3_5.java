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

import org.junit.Assert;
import org.junit.Test;

public class TestHttp2Section_3_5 extends Http2TestBase {

    @Test
    public void testNoConnectionPreface() throws Exception {
        enableHttp2();
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade();

        // Server settings
        parser.readFrame(true);
        Assert.assertEquals("0-Settings-[3]-[200]\n" +
                "0-Settings-End\n"
                , output.getTrace());
        output.clearTrace();

        // Should send client preface here. This will trigger an error.
        // Send two pings (2*(9+8)=34 bytes) as server looks for entire preface
        // of 24 bytes.
        sendPing();
        sendPing();

        // If the client preface had been valid, this would be an
        // acknowledgement. Of the settings. As the preface was invalid, it
        // should be a GOAWAY frame.
        try {
            parser.readFrame(true);
            Assert.assertTrue(output.getTrace(), output.getTrace().startsWith("0-Goaway-[1]-[1]-["));
        } catch (IOException ioe) {
            // Ignore
            // This is expected on some platforms and depends on timing. Once
            // the server closes the connection the client may see an exception
            // when trying to read the GOAWAY frame.
        }
    }
}
