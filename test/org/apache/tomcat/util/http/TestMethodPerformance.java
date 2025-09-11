/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.http;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import org.apache.tomcat.util.buf.MessageBytes;

public class TestMethodPerformance {

    private static final int LOOPS = 6;
    private static final int ITERATIONS = 100000000;

    private static final String INPUT = "GET /context-path/servlet-path/path-info HTTP/1.1";
    private static final byte[] INPUT_BYTES = INPUT.getBytes(StandardCharsets.UTF_8);

    private static MessageBytes mb = MessageBytes.newInstance();

    @Test
    public void testGetMethodPerformance() throws Exception {

        for (int j = 0; j < LOOPS; j++) {
            long start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                mb.setBytes(INPUT_BYTES, 0, 3);
                mb.toStringType();
            }
            long duration = System.nanoTime() - start;

            if (j > 0) {
                System.out.println("MessageBytes conversion took :" + duration + "ns");
            }
        }

        for (int j = 0; j < LOOPS; j++) {
            long start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                String method = Method.bytesToString(INPUT_BYTES, 0, 3);
                if (method == null) {
                    mb.setBytes(INPUT_BYTES, 0, 5);
                    mb.toStringType();
                }
            }
            long duration = System.nanoTime() - start;

            if (j > 0) {
                System.out.println("Optimized conversion took :" + duration + "ns");
            }
        }
    }
}
