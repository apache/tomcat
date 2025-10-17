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
package org.apache.catalina.core;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.Test;

import org.apache.catalina.connector.Request;

/*
 * This is an absolute performance test. There is no benefit it running it as part of a standard test run so it is
 * excluded due to the name starting Tester...
 */
public class TesterApplicationHttpRequestPerformance {

    @Test
    public void testGetAttribute() {
        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        Request request = new Request(null, coyoteRequest);
        ApplicationHttpRequest applicationHttpRequest = new ApplicationHttpRequest(request, null, false);

        // Warm-up
        doTestGetAttribute(applicationHttpRequest);

        long start = System.nanoTime();
        doTestGetAttribute(applicationHttpRequest);
        long duration = System.nanoTime() - start;

        System.out.println(duration + "ns");
    }


    private void doTestGetAttribute(HttpServletRequest request) {
        for (int i = 0; i < 100000000; i++) {
            request.getAttribute("Unknown");
        }
    }

    private static HttpServletRequest getRequest(int depth) {
        if (depth <= 0) {
            org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
            Request request = new Request(null, coyoteRequest);
            return request;
        }
        return new ApplicationHttpRequest(getRequest(depth - 1), null, false);
    }


    @Test
    public void testGetAttributeNested() {
        int[] depths = { 0, 1, 4, 7 };
        for (int depth : depths) {
            HttpServletRequest httpRequest = getRequest(depth);

            // Warm-up
            doTestGetAttribute(httpRequest);

            long start = System.nanoTime();
            doTestGetAttribute(httpRequest);
            long duration = System.nanoTime() - start;

            System.out.println("Depth " + depth + ": " + duration + "ns");
        }
    }
}
