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
package org.apache.catalina.tribes;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TestUniqueId {

    @Test
    public void testNullId() {
        UniqueId uid = new UniqueId();
        Assert.assertNull(uid.getBytes());
        Assert.assertEquals(new UniqueId(), uid);
        Assert.assertEquals(new UniqueId().hashCode(), uid.hashCode());
    }

    @Test
    public void testByteArrayConstructorDoesNotRetainReference() {
        byte[] id = new byte[] { 1, 2, 3, 4 };
        UniqueId uid = new UniqueId(id);
        Assert.assertNotSame(id, uid.getBytes());
        Assert.assertArrayEquals(id, uid.getBytes());
        id[0] = 99;
        // The UniqueId must be unaffected by the mutation above
        Assert.assertEquals(1, uid.getBytes()[0]);
    }

    @Test
    public void testMapKeySurvivesCallerMutation() {
        byte[] id = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
        UniqueId uid = new UniqueId(id);
        Map<UniqueId, String> map = new HashMap<>();
        map.put(uid, "value");
        id[0] = 99;
        Assert.assertEquals("value", map.get(uid));
        UniqueId equalId = new UniqueId(
                new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 });
        Assert.assertTrue(map.containsKey(equalId));
    }

    @Test
    public void testRangeConstructorCopies() {
        byte[] source = new byte[] { 0, 1, 2, 3, 4 };
        UniqueId uid = new UniqueId(source, 1, 3);
        Assert.assertNotNull(uid.getBytes());
        source[1] = 99;
        Assert.assertEquals(1, uid.getBytes()[0]);
    }
}
