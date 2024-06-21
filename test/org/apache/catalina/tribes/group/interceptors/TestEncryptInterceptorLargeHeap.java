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
package org.apache.catalina.tribes.group.interceptors;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import org.apache.catalina.tribes.Channel;

/**
 * Tests the EncryptInterceptor using very large inputs.
 *
 * Many of the tests in this class use strings as input and output, even
 * though the interceptor actually operates on byte arrays. This is done
 * for readability for the tests and their outputs.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestEncryptInterceptorLargeHeap extends EncryptionInterceptorBaseTest {

    @Test
    public void testHugePayload() throws Exception {
        src.start(Channel.SND_TX_SEQ);
        dest.start(Channel.SND_TX_SEQ);

        byte[] bytes = new byte[1024*1024*1024];

        Assert.assertArrayEquals("Huge payload roundtrip failed",
                          bytes,
                          roundTrip(bytes, src, dest));
    }
}
