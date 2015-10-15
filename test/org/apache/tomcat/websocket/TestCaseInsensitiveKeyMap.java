/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.tomcat.util.collections.CaseInsensitiveKeyMap;
import org.junit.Assert;
import org.junit.Test;

public class TestCaseInsensitiveKeyMap {

    @Test
    public void testPut() {
        Object o1 = new Object();
        Object o2 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);
        Object o = map.put("A", o2);

        Assert.assertEquals(o1,  o);

        Assert.assertEquals(o2, map.get("a"));
        Assert.assertEquals(o2, map.get("A"));
    }


    @Test(expected=NullPointerException.class)
    public void testPutNullKey() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put(null, o1);
    }


    @Test
    public void testGet() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);

        Assert.assertEquals(o1, map.get("a"));
        Assert.assertEquals(o1, map.get("A"));
    }


    @Test
    public void testGetNullKey() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);

        Assert.assertNull(map.get(null));
    }


    @Test
    public void testContainsKey() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);

        Assert.assertTrue(map.containsKey("a"));
        Assert.assertTrue(map.containsKey("A"));
    }


    @Test
    public void testContainsKeyNonString() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);

        Assert.assertFalse(map.containsKey(o1));
    }


    @Test
    public void testContainsKeyNull() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);

        Assert.assertFalse(map.containsKey(null));
    }


    @Test
    public void testContainsValue() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);

        Assert.assertTrue(map.containsValue(o1));
    }


    @Test
    public void testRemove() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);
        Assert.assertFalse(map.isEmpty());
        map.remove("A");
        Assert.assertTrue(map.isEmpty());

        map.put("A", o1);
        Assert.assertFalse(map.isEmpty());
        map.remove("a");
        Assert.assertTrue(map.isEmpty());
    }


    @Test
    public void testClear() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        for (int i = 0; i < 10; i++) {
            map.put(Integer.toString(i), o1);
        }
        Assert.assertEquals(10, map.size());
        map.clear();
        Assert.assertEquals(0, map.size());
    }


    @Test
    public void testPutAll() {
        Object o1 = new Object();
        Object o2 = new Object();

        Map<String,Object> source = new HashMap<>();
        source.put("a", o1);
        source.put("A", o2);

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.putAll(source);

        Assert.assertEquals(1, map.size());
        Assert.assertTrue(map.containsValue(o1) != map.containsValue(o2));
    }


    @Test
    public void testKeySetContains() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);

        Set<String> keys = map.keySet();

        Assert.assertTrue(keys.contains("a"));
        Assert.assertTrue(keys.contains("A"));
    }


    @Test
    public void testKeySetRemove() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);

        Iterator<String> iter = map.keySet().iterator();
        Assert.assertTrue(iter.hasNext());
        iter.next();
        iter.remove();
        Assert.assertTrue(map.isEmpty());
    }


    @Test
    public void testEntrySetRemove() {
        Object o1 = new Object();

        CaseInsensitiveKeyMap<Object> map = new CaseInsensitiveKeyMap<>();
        map.put("a", o1);

        Iterator<Entry<String,Object>> iter = map.entrySet().iterator();
        Assert.assertTrue(iter.hasNext());
        Entry<String,Object> entry = iter.next();
        Assert.assertEquals("a", entry.getKey());
        Assert.assertEquals(o1, entry.getValue());
        iter.remove();
        Assert.assertTrue(map.isEmpty());
    }
}
