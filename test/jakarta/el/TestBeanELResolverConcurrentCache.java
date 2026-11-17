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
package jakarta.el;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for the private cache used by {@link BeanELResolver}. Reflection is used since the class is private.
 */
public class TestBeanELResolverConcurrentCache {

    private static final Class<?> CACHE_CLASS = getCacheClass();

    private static Class<?> getCacheClass() {
        try {
            return Class.forName("jakarta.el.BeanELResolver$ConcurrentCache");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }


    private static Object newCache(int size) throws Exception {
        Constructor<?> constructor = CACHE_CLASS.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(Integer.valueOf(size));
    }


    private static Object get(Object cache, Object key) throws Exception {
        Method method = CACHE_CLASS.getDeclaredMethod("get", Object.class);
        method.setAccessible(true);
        return method.invoke(cache, key);
    }


    private static void put(Object cache, Object key, Object value) throws Exception {
        Method method = CACHE_CLASS.getDeclaredMethod("put", Object.class, Object.class);
        method.setAccessible(true);
        method.invoke(cache, key, value);
    }


    private static Map<?,?> eden(Object cache) throws Exception {
        Field edenField = CACHE_CLASS.getDeclaredField("eden");
        edenField.setAccessible(true);
        return (Map<?,?>) edenField.get(cache);
    }


    @Test
    public void testGetPromotesLongtermHit() throws Exception {
        Object cache = newCache(2);

        put(cache, "k1", "v1");
        put(cache, "k2", "v2");
        // Eden has reached the configured size. This put promotes the previous eden contents to the long-term store.
        put(cache, "k3", "v3");
        Assert.assertEquals(1, eden(cache).size());

        // Promoted back into eden since there is room
        Assert.assertEquals("v1", get(cache, "k1"));
        Assert.assertEquals(2, eden(cache).size());

        // Eden is full. Its content is moved to the long-term store and k2 is promoted into the empty eden.
        Assert.assertEquals("v2", get(cache, "k2"));
        Assert.assertEquals(1, eden(cache).size());
        Assert.assertTrue(eden(cache).containsKey("k2"));

        // Everything is still available
        Assert.assertEquals("v1", get(cache, "k1"));
        Assert.assertEquals("v3", get(cache, "k3"));
        Assert.assertEquals("v2", get(cache, "k2"));
    }


    @Test
    public void testGetWorkingSetMovesToEden() throws Exception {
        Object cache = newCache(3);

        String[] keys = { "a", "b", "c", "d", "e" };
        for (String key : keys) {
            put(cache, key, "v" + key);
        }
        Assert.assertFalse(eden(cache).containsKey("a"));
        Assert.assertFalse(eden(cache).containsKey("b"));

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals("va", get(cache, "a"));
            Assert.assertEquals("vb", get(cache, "b"));
        }

        Assert.assertTrue(eden(cache).containsKey("a"));
        Assert.assertTrue(eden(cache).containsKey("b"));
        Assert.assertTrue(eden(cache).size() <= 3);

        for (String key : keys) {
            Assert.assertEquals("v" + key, get(cache, key));
        }
    }


    @Test
    public void testGetPromotionRespectsEdenSize() throws Exception {
        int size = 10;
        Object cache = newCache(size);

        // Hold strong references to the keys so the long-term store cannot lose entries via GC
        String[] keys = new String[100];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = "k" + i;
        }
        for (int i = 0; i < keys.length; i++) {
            put(cache, keys[i], "v" + i);
        }
        for (int i = 0; i < keys.length; i++) {
            Assert.assertEquals("v" + i, get(cache, keys[i]));
        }

        Assert.assertTrue("Eden size [" + eden(cache).size() + "] should not exceed the configured size [" + size
                + "]", eden(cache).size() <= size);
    }
}
