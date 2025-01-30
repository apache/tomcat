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

public class TestRfc9218 extends Http2TestBase {

    @Test
    public void testPriority() throws Exception {
        http2Connect();

        /*
         * This test uses small window updates and data frames that will trigger the excessive overhead protection so
         * disable it.
         */
        http2Protocol.setOverheadWindowUpdateThreshold(0);
        http2Protocol.setOverheadDataThreshold(0);

        // Default connection window size is 64k - 1. Initial request will have used 8k (56k -1). Increase it to 57k.
        sendWindowUpdate(0, 1 + 1024);

        // Consume 56k of the connection window
        for (int i = 3; i < 17; i += 2) {
            sendSimpleGetRequest(i);
            readSimpleGetResponse();
        }

        String trace = output.getTrace();
        System.out.println(trace);
        output.clearTrace();

        // At this point the connection window should be 1k

        // Process a request on stream 17. This should consume the connection window.
        sendSimpleGetRequest(17);
        // 17-headers, 17-1k-body
        parser.readFrame();
        parser.readFrame();
        trace = output.getTrace();
        System.out.println(trace);
        output.clearTrace();

        // Send additional requests. Connection window is empty so only headers will be returned.
        sendSimpleGetRequest(19);
        sendSimpleGetRequest(21);

        // 19-headers, 21-headers
        parser.readFrame();
        parser.readFrame();
        trace = output.getTrace();
        System.out.println(trace);
        output.clearTrace();

        // At this point 17, 19 and 21 are all blocked because the connection window is zero.
        // 17 - 7k body left
        // 19 - 8k body left
        // 21 - 8k body left

        // Add 1k to the connection window. Should be used for stream 17.
        sendWindowUpdate(0, 1024);
        parser.readFrame();
        Assert.assertEquals("17-Body-1024\n", output.getTrace());
        output.clearTrace();

        // 17 - 6k body left
        // 19 - 8k body left
        // 21 - 8k body left

        // Re-order the priorities
        sendPriorityUpdate(19, 2, false);
        sendPriorityUpdate(21, 1, false);

        // Add 1k to the connection window. Should be used for stream 21.
        sendWindowUpdate(0, 1024);
        parser.readFrame();

        Assert.assertEquals("21-Body-1024\n", output.getTrace());
        output.clearTrace();

        // 17 - 6k body left
        // 19 - 8k body left
        // 21 - 7k body left

        // Re-order the priorities
        sendPriorityUpdate(17, 3, true);
        sendPriorityUpdate(19, 3, true);
        sendPriorityUpdate(21, 3, true);

        // Add 3k to the connection window. Should be split between 17, 19 and 21.
        sendWindowUpdate(0, 1024 * 3);
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();

        trace = output.getTrace();
        Assert.assertTrue(trace.contains("17-Body-877\n"));
        trace = trace.replace("17-Body-877\n", "");
        Assert.assertTrue(trace.contains("19-Body-1170\n"));
        trace = trace.replace("19-Body-1170\n", "");
        Assert.assertTrue(trace.contains("21-Body-1024\n"));
        trace = trace.replace("21-Body-1024\n", "");
        Assert.assertEquals(0, trace.length());
        output.clearTrace();

        // 1 byte unallocated in connection window
        // 17 - 5267 body left
        // 19 - 7022 body left
        // 21 - 6144 body left

        // Add 1 byte to the connection window. Due to rounding up, each stream should get 1 byte.
        sendWindowUpdate(0, 1);
        parser.readFrame();
        parser.readFrame();
        parser.readFrame();

        trace = output.getTrace();
        Assert.assertTrue(trace.contains("17-Body-1\n"));
        trace = trace.replace("17-Body-1\n", "");
        Assert.assertTrue(trace.contains("19-Body-1\n"));
        trace = trace.replace("19-Body-1\n", "");
        Assert.assertTrue(trace.contains("21-Body-1\n"));
        trace = trace.replace("21-Body-1\n", "");
        Assert.assertEquals(0, trace.length());
        output.clearTrace();

        // 1 byte over allocated in connection window
        // 17 - 5266 body left
        // 19 - 7021 body left
        // 21 - 6143 body left

        // Re-order the priorities
        sendPriorityUpdate(17, 2, true);

        /*
         * Add 8k to the connection window. Should clear the connection window over allocation and fully allocate 17
         * with the remainder split proportionally between 19 and 21.
         */
        sendWindowUpdate(0, 1024 * 8);
        // Use try/catch as third read has been failing on some tests runs
        try {
            parser.readFrame();
            parser.readFrame();
            parser.readFrame();
        } catch (IOException ioe) {
            // Dump for debugging purposes
            ioe.printStackTrace();
            // Continue - we'll get trace dumped to stdout below
        }

        trace = output.getTrace();
        System.out.println(trace);
        Assert.assertTrue(trace.contains("17-Body-5266\n"));
        trace = trace.replace("17-Body-5266\n", "");
        Assert.assertTrue(trace.contains("17-EndOfStream\n"));
        trace = trace.replace("17-EndOfStream\n", "");
        Assert.assertTrue(trace.contains("19-Body-1560\n"));
        trace = trace.replace("19-Body-1560\n", "");
        Assert.assertTrue(trace.contains("21-Body-1365\n"));
        trace = trace.replace("21-Body-1365\n", "");
        Assert.assertEquals(0, trace.length());

        // 19 - 5641 body left
        // 21 - 4778 body left

        // Add 16k to the connection window. Should fully allocate 19 and 21.
        sendWindowUpdate(0, 1024 * 16);

        try {
            parser.readFrame();
            parser.readFrame();
        } catch (IOException ioe) {
            // Dump for debugging purposes
            ioe.printStackTrace();
        }
    }
}
