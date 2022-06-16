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
package org.apache.tomcat.jni;

/**
 * Provides access to APR memory pools which are used to manage memory
 * allocations for natively created instances.
 */
public class Pool {

    /**
     * Create a new pool.
     *
     * @param parent The parent pool. If this is 0, the new pool is a root pool.
     *               If it is non-zero, the new pool will inherit all of its
     *               parent pool's attributes, except the apr_pool_t will be a
     *               sub-pool.
     *
     * @return The pool we have just created.
    */
    public static native long create(long parent);

    /**
     * Destroy the pool. This takes similar action as apr_pool_clear() and then
     * frees all the memory. This will actually free the memory.
     *
     * @param pool The pool to destroy
     */
    public static native void destroy(long pool);
}
