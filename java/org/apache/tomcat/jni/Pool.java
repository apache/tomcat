/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

public class Pool {

    /**
     * Create a new pool.
     * @param parent The parent pool.  If this is 0, the new pool is a root
     * pool.  If it is non-zero, the new pool will inherit all
     * of its parent pool's attributes, except the apr_pool_t will
     * be a sub-pool.
     * @return The pool we have just created.
    */
    public static native long create(long parent);

    /**
     * Clear all memory in the pool and run all the cleanups. This also destroys all
     * subpools.
     * @param pool The pool to clear
     * This does not actually free the memory, it just allows the pool
     *         to re-use this memory for the next allocation.
     */
    public static native void clear(long pool);

    /**
     * Destroy the pool. This takes similar action as apr_pool_clear() and then
     * frees all the memory.
     * This will actually free the memory
     * @param pool The pool to destroy
     */
    public static native void destroy(long pool);

    /**
     * Get the parent pool of the specified pool.
     * @param pool The pool for retrieving the parent pool.
     * @return The parent of the given pool.
     */
    public static native long parentGet(long pool);

    /**
     * Determine if pool a is an ancestor of pool b
     * @param a The pool to search
     * @param b The pool to search for
     * @return True if a is an ancestor of b, NULL is considered an ancestor
     * of all pools.
     */
    public static native boolean isAncestor(long a, long b);


    /*
     * Cleanup
     *
     * Cleanups are performed in the reverse order they were registered.  That is:
     * Last In, First Out.  A cleanup function can safely allocate memory from
     * the pool that is being cleaned up. It can also safely register additional
     * cleanups which will be run LIFO, directly after the current cleanup
     * terminates.  Cleanups have to take caution in calling functions that
     * create subpools. Subpools, created during cleanup will NOT automatically
     * be cleaned up.  In other words, cleanups are to clean up after themselves.
     */

    /**
     * Register a function to be called when a pool is cleared or destroyed
     * @param pool The pool register the cleanup with
     * @param o The object to call when the pool is cleared
     *                      or destroyed
     * @return The cleanup handler.
     */
    public static native long cleanupRegister(long pool, Object o);

    /**
     * Remove a previously registered cleanup function
     * @param pool The pool remove the cleanup from
     * @param data The cleanup handler to remove from cleanup
     */
    public static native void cleanupKill(long pool, long data);

    /**
     * Register a process to be killed when a pool dies.
     * @param a The pool to use to define the processes lifetime 
     * @param proc The process to register
     * @param how How to kill the process, one of:
     * <PRE>
     * APR_KILL_NEVER         -- process is never sent any signals
     * APR_KILL_ALWAYS        -- process is sent SIGKILL on apr_pool_t cleanup
     * APR_KILL_AFTER_TIMEOUT -- SIGTERM, wait 3 seconds, SIGKILL
     * APR_JUST_WAIT          -- wait forever for the process to complete
     * APR_KILL_ONLY_ONCE     -- send SIGTERM and then wait
     * </PRE>
     */
    public static native void noteSubprocess(long a, long proc, int how);

}
