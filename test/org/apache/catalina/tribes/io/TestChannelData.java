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
package org.apache.catalina.tribes.io;

import org.junit.Assert;
import org.junit.Test;

public class TestChannelData {

    @Test
    public void testClone() {
        ChannelData original = new ChannelData();
        byte[] payload = new byte[] { 1, 2, 3, 4 };
        XByteBuffer message = new XByteBuffer(1024, false);
        message.append(payload, 0, payload.length);
        original.setMessage(message);

        ChannelData clone = original.clone();

        Assert.assertFalse(original == clone);
        Assert.assertTrue(original.getClass() == clone.getClass());
        Assert.assertTrue(original.equals(clone));
        Assert.assertNotSame(message, clone.getMessage());
        Assert.assertArrayEquals(payload, clone.getMessage().getBytes());
        Assert.assertEquals(payload.length, clone.getMessage().getCapacity());

        message.getBytesDirect()[0] = 99;
        Assert.assertEquals(1, clone.getMessage().getBytesDirect()[0]);
    }
}
