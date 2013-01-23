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
package org.apache.tomcat.util.buf;

import org.junit.Assert;
import org.junit.Test;

public class TestB2CConverter {

    private static final byte[] UTF16_MESSAGE =
            new byte[] {-2, -1, 0, 65, 0, 66, 0, 67};

    @Test
    public void testSingleMessage() throws Exception {
        testMessages(1);
    }

    @Test
    public void testTwoMessage() throws Exception {
        testMessages(2);
    }

    @Test
    public void testManyMessage() throws Exception {
        testMessages(10);
    }

    private void testMessages(int msgCount) throws Exception {
        B2CConverter conv = new B2CConverter("UTF-16");

        ByteChunk bc = new ByteChunk();
        CharChunk cc = new CharChunk(32);


        for (int i = 0; i < msgCount; i++) {
            bc.append(UTF16_MESSAGE, 0, UTF16_MESSAGE.length);
            conv.convert(bc, cc);
            Assert.assertEquals("ABC", cc.toString());
            bc.recycle();
            cc.recycle();
            conv.recycle();
        }

        System.out.println(cc);
    }
}
