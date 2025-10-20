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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class TestExpressionFactoryCache {
    private static class TestClassLoader extends ClassLoader {
    }

    @Test
    public void testCacheWithOneClassLoader() {
        ExpressionFactory factory;
        ClassLoader cl = new TestClassLoader();
        ExpressionFactoryCache cache = new ExpressionFactoryCache();

        factory = cache.getOrCreateExpressionFactory(cl);

        Assert.assertEquals(factory, cache.getOrCreateExpressionFactory(cl));
    }

    @Test
    public void testCacheWithInserts() {
        final int numClassLoaders = 15;
        ClassLoader[] loaders = new ClassLoader[numClassLoaders];
        ExpressionFactory[] factories = new ExpressionFactory[numClassLoaders];

        ExpressionFactoryCache cache = new ExpressionFactoryCache();

        for (int i = 0; i < numClassLoaders; i++) {
            loaders[i] = new TestClassLoader();
            factories[i] = cache.getOrCreateExpressionFactory(loaders[i]);

            // make a second call, ensure the same instance comes back
            Assert.assertEquals(factories[i], cache.getOrCreateExpressionFactory(loaders[i]));
        }

        // use a Set<> to verify each factory is unique
        Set<ExpressionFactory> factorySet = new HashSet<>(Arrays.asList(factories));
        Assert.assertEquals(numClassLoaders, factorySet.size());
    }

}