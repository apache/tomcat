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

import org.junit.Test;

import org.apache.catalina.connector.Request;

public class TestApplicationHttpRequestPerformance {

    @Test
    public void testGetAttribute() {
        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        Request request = new Request(null);
        request.setCoyoteRequest(coyoteRequest);
        ApplicationHttpRequest applicationHttpRequest = new ApplicationHttpRequest(request, null ,false);

        // Warm-up
        doTestGetAttribute(applicationHttpRequest);

        long start = System.nanoTime();
        doTestGetAttribute(applicationHttpRequest);
        long duration = System.nanoTime() - start;

        System.out.println(duration + "ns");
    }


    private void doTestGetAttribute(ApplicationHttpRequest request) {
        for (int i = 0; i < 100000000; i++) {
            request.getAttribute("Unknown");
        }
    }
}
