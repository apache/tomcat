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
import org.junit.Test;

/**
 * Unit tests for Section 5.3 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
 *
 * Note: Unit tests for the examples described by each of the figures may be
 * found in {@link TestAbstractStream}.
 */
public class TestHttp2Section_5_3 extends Http2TestBase {

    // Section 5.3.1

    @Test
    public void testStreamDependsOnSelf() throws Exception {
        http2Connect();

        sendPriority(3,  3,  15);

        parser.readFrame(true);

        Assert.assertEquals("3-RST-[1]\n",  output.getTrace());
    }


    // Section 5.3.2

    @Test
    public void testWeighting() throws Exception {

        http2Connect();

        // This test uses small window updates that will trigger the excessive
        // overhead protection so disable it.
        http2Protocol.setOverheadWindowUpdateThreshold(0);
        // May also see (rarely, depends on timing) sequential 1 byte data
        // frames on the same Stream
        http2Protocol.setOverheadDataThreshold(0);


        // Default connection window size is 64k - 1. Initial request will have
        // used 8k (56k -1). Increase it to 57k
        sendWindowUpdate(0, 1 + 1024);

        // Use up 56k of the connection window
        for (int i = 3; i < 17; i += 2) {
            sendSimpleGetRequest(i);
            readSimpleGetResponse();
        }

        // Set the default window size to 1024 bytes
        sendSettings(0, false, new SettingValue(4, 1024));
        // Wait for the ack
        parser.readFrame(true);
        // Debugging Gump failure
        log.info(output.getTrace());
        output.clearTrace();

        // At this point the connection window should be 1k and any new stream
        // should have a window of 1k as well

        // Set up streams A=17, B=19, C=21
        sendPriority(17,  0, 15);
        sendPriority(19, 17,  3);
        sendPriority(21, 17, 11);

        // First, process a request on stream 17. This should consume both
        // stream 17's window and the connection window.
        sendSimpleGetRequest(17);
        // 17-headers, 17-1k-body
        parser.readFrame(true);
        // Debugging Gump failure
        log.info(output.getTrace());
        parser.readFrame(true);
        // Debugging Gump failure
        log.info(output.getTrace());
        output.clearTrace();

        // Send additional requests. Connection window is empty so only headers
        // will be returned.
        sendSimpleGetRequest(19);
        sendSimpleGetRequest(21);

        // Open up the flow control windows for stream 19 & 21 to more than the
        // size of a simple request (8k)
        sendWindowUpdate(19, 16*1024);
        sendWindowUpdate(21, 16*1024);

        // Read some frames
        // 19-headers, 21-headers
        parser.readFrame(true);
        // Debugging Gump failure
        log.info(output.getTrace());
        parser.readFrame(true);
        // Debugging Gump failure
        log.info(output.getTrace());
        output.clearTrace();

        // At this point 17 is blocked because the stream window is zero and
        // 19 & 21 are blocked because the connection window is zero.
        //
        // To test allocation, the connection window size is increased by 1.
        // This should result in an allocation of 1 byte each to streams 19 and
        // 21 but because each stream is processed in a separate thread it is
        // not guaranteed that both streams will be blocked when the connection
        // window size is increased. The test therefore sends 1 byte window
        // updates until a small body has been seen from each stream. Then the
        // tests sends a larger (1024 byte) window update and checks that it is
        // correctly distributed between the streams.
        //
        // The test includes a margin to allow for the potential differences in
        // response caused by timing differences on the server.
        //
        // The loop below handles 0, 1 or 2 stream being blocked
        // - If 0 streams are blocked the connection window will be set to one
        //   and that will be consumed by the first stream to attempt to write.
        //   That body frame will be read by the client. The stream will then be
        //   blocked and the loop will start again.
        // - If 1 stream is blocked, the connection window will be set to one
        //   which will then be consumed by the blocked stream. After writing
        //   the single byte the stream will again be blocked and the loop will
        //   start again.
        // - If 2 streams are blocked the connection window will be set to one
        //   but one byte will be permitted for both streams (due to rounding in
        //   the allocation). The window size should be -1 (see below). Two
        //   frames (one for each stream will be written) one of which will be
        //   consumed by the client. The loop will start again and the Window
        //   size incremented to zero. No data will be written by the streams
        //   but the second data frame written in the last iteration of the loop
        //   will be read. The loop will then exit since frames from both
        //   streams will have been observed.
        boolean seen19 = false;
        boolean seen21 = false;
        while (!seen19 || !seen21) {
            sendWindowUpdate(0, 1);
            parser.readFrame(true);
            // Debugging Gump failure
            log.info(output.getTrace());
            int[] data = parseBodyFrame(output.getTrace());
            if (data[0] == 19) {
                seen19 = true;
            } else if (data[0] == 21) {
                seen21 = true;
            } else {
                // Unexpected stream
                Assert.fail("Unexpected stream: [" + output.getTrace() + "]");
            }
            // A value of more than 1 here is unlikely but possible depending on
            // how threads are scheduled. This has been observed as high as 21
            // on ci.apache.org so allow a margin and use 30.
            if (data[1] > 30) {
                // Larger than expected body size
                Assert.fail("Larger than expected body: [" + output.getTrace() + "]");
            }
            output.clearTrace();
        }

        sendWindowUpdate(0, 1024);
        parser.readFrame(true);

        // Make sure you have read the big comment before the loop above. It is
        // possible that the timing of the server threads is such that there are
        // still small body frames to read.
        int[] data = parseBodyFrame(output.getTrace());
        while (data[1] < 20) {
            // Debugging Gump failure
            log.info(output.getTrace());
            output.clearTrace();
            parser.readFrame(true);
            data = parseBodyFrame(output.getTrace());
        }

        // Should now have two larger body frames. One has already been read.
        seen19 = false;
        seen21 = false;
        while (!seen19 && !seen21) {
            // Debugging Gump failure
            log.info(output.getTrace());
            if (data[0] == 19) {
                seen19 = true;
                // If everything works instantly this should be 256 but allow a
                // fairly large margin for timing differences
                if (data[1] < 216 || data[1] > 296) {
                    Assert.fail("Unexpected body size: [" + output.getTrace() + "]");
                }
            } else if (data[0] == 21) {
                seen21 = true;
                // If everything works instantly this should be 768 but allow a
                // fairly large margin for timing differences
                if (data[1] < 728 || data[1] > 808) {
                    Assert.fail("Unexpected body size: [" + output.getTrace() + "]");
                }
            } else {
                Assert.fail("Unexpected stream: [" + output.getTrace() + "]");
            }
            output.clearTrace();
            parser.readFrame(true);
            data = parseBodyFrame(output.getTrace());
        }
        // Debugging Gump failure
        log.info(output.getTrace());
        output.clearTrace();

        // Release everything and read all the remaining data
        sendWindowUpdate(0, 1024 * 1024);
        sendWindowUpdate(17, 1024 * 1024);

        // Read remaining frames
        // 17-7k-body, 19~8k-body, 21~8k-body
        for (int i = 0; i < 3; i++) {
            parser.readFrame(true);
            // Debugging Gump failure
            log.info(output.getTrace());
        }
    }


    private int[] parseBodyFrame(String output) {
        String[] parts = output.trim().split("-");
        if (parts.length != 3 || !"Body".equals(parts[1])) {
            Assert.fail("Unexpected output: [" + output + "]");
        }

        int[] result = new int[2];

        result[0] = Integer.parseInt(parts[0]);
        result[1] = Integer.parseInt(parts[2]);

        return result;
    }
}
