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

import junit.framework.TestCase;

public class TestXByteBuffer extends TestCase {

    public void testEmptyArray() throws Exception {
        // TODO
    }

    public void testSerializationString() throws Exception {
        String test = "This is as test.";
        byte[] msg = XByteBuffer.serialize(test);
        Object obj = XByteBuffer.deserialize(msg);
        assertTrue(obj instanceof String);
        assertEquals(test, obj);
    }

    public static void main(String[] args) throws Exception {
        //XByteBuffer.deserialize(new byte[0]);
        XByteBuffer.deserialize(new byte[] {-84, -19, 0, 5, 115, 114, 0, 17, 106});
    }

}
