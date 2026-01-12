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

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

class ExpressionFactoryCache {

    private final AtomicReference<WeakHashMap<ClassLoader,WeakReference<ExpressionFactory>>> factoryCache;

    ExpressionFactoryCache() {
        factoryCache = new AtomicReference<>(new WeakHashMap<>());
    }

    /**
     * Retrieves the currently cached ExpressionFactory, or creates a new one if required. Reads from an immutable
     * WeakHashMap (which is threadsafe); in the rare cases that mutation is required, a new copy is created and
     * modified, then swapped in via AtomicReference. Key performance characteristics:
     * <ol>
     * <li>Reads are uncontended on an immutable object. (threadsafe)</li>
     * <li>Writes are performed by copying an immutable object, then inserting. (threadsafe)</li>
     * <li>The new object is swapped in via AtomicReference, or re-attempted if the data has since changed.
     * (threadsafe)</li>
     * <li>A single call will create 0 or 1 instances of ExpressionFactory. Simultaneous initialization by multiple
     * threads may create 2+ instances of ExpressionFactory, but excess instances are short-lived and harmless.
     * (memorysafe)</li>
     * <li>ClassLoaders are weakly held (memorysafe)</li>
     * <li>ExpressionFactorys are weakly held (memorysafe)</li>
     * <li>No objects are allocated on cache hits (the common case)</li>
     * </ol>
     *
     * @param cl The classloader for which the cached {@code ExpressionFactory} is to be created or retrieved
     *
     * @return The cached {@code ExpressionFactory} for the given {@code ClassLoader}
     */
    ExpressionFactory getOrCreateExpressionFactory(ClassLoader cl) {
        WeakHashMap<ClassLoader,WeakReference<ExpressionFactory>> cache;
        WeakHashMap<ClassLoader,WeakReference<ExpressionFactory>> newCache;
        ExpressionFactory factory = null;
        WeakReference<ExpressionFactory> factoryRef;
        do {
            // cache cannot be null
            cache = factoryCache.get();

            factoryRef = cache.get(cl);
            // factoryRef can be null (could be uninitialized, or the GC cleaned the weak ref)
            if (factoryRef != null) {
                factory = factoryRef.get();
                // factory can be null (GC may have cleaned the ref)
                if (factory != null) {
                    return factory;
                }
            }

            // something somewhere was uninitialized or GCd
            if (factory == null) {
                // only create an instance on the first iteration of the loop
                factory = ExpressionFactory.newInstance();
            }
            factoryRef = new WeakReference<>(factory);
            newCache = new WeakHashMap<>(cache);

            newCache.put(cl, factoryRef);
        } while (!factoryCache.compareAndSet(cache, newCache));

        return factory;
    }
}
