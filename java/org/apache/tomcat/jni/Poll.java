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

/** Poll
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

public class Poll {

    /**
     * Poll options
     */
    public static final int APR_POLLIN   = 0x001; /** Can read without blocking */
    public static final int APR_POLLPRI  = 0x002; /** Priority data available */
    public static final int APR_POLLOUT  = 0x004; /** Can write without blocking */
    public static final int APR_POLLERR  = 0x010; /** Pending error */
    public static final int APR_POLLHUP  = 0x020; /** Hangup occurred */
    public static final int APR_POLLNVAL = 0x040; /** Descriptior invalid */

    /**
     * Pollset Flags
     */
    /** Adding or Removing a Descriptor is thread safe */
    public static final int APR_POLLSET_THREADSAFE = 0x001;


    /** Used in apr_pollfd_t to determine what the apr_descriptor is
     * apr_datatype_e enum
     */
    public static final int APR_NO_DESC       = 0; /** nothing here */
    public static final int APR_POLL_SOCKET   = 1; /** descriptor refers to a socket */
    public static final int APR_POLL_FILE     = 2; /** descriptor refers to a file */
    public static final int APR_POLL_LASTDESC = 3; /** descriptor is the last one in the list */

    /**
     * Setup a pollset object.
     * If flags equals APR_POLLSET_THREADSAFE, then a pollset is
     * created on which it is safe to make concurrent calls to
     * apr_pollset_add(), apr_pollset_remove() and apr_pollset_poll() from
     * separate threads.  This feature is only supported on some
     * platforms; the apr_pollset_create() call will fail with
     * APR_ENOTIMPL on platforms where it is not supported.
     * @param size The maximum number of descriptors that this pollset can hold
     * @param p The pool from which to allocate the pollset
     * @param flags Optional flags to modify the operation of the pollset.
     * @return  The pointer in which to return the newly created object
     */
    public static native long create(int size, long p, int flags)
        throws Error;
    /**
     * Destroy a pollset object
     * @param pollset The pollset to destroy
     */
    public static native int destroy(long pollset);

    /**
     * Add a socket or to a pollset
     * If you set client_data in the descriptor, that value
     * will be returned in the client_data field whenever this
     * descriptor is signalled in apr_pollset_poll().
     * @param pollset The pollset to which to add the descriptor
     * @param sock The sockets to add
     * @param data Client data to add
     * @param reqevents requested events
     * @param rtnevents returned events
     */
    public static native int add(long pollset, long sock, long data,
                                 int reqevents, int rtnevents);

    /**
     * Remove a descriptor from a pollset
     * @param pollset The pollset from which to remove the descriptor
     * @param sock The socket to remove
     */
    public static native int remove(long pollset, long sock);

    /**
     * Block for activity on the descriptor(s) in a pollset
     * @param pollset The pollset to use
     * @param timeout Timeout in microseconds
     * @param descriptors Array of signalled descriptors (output parameter)
     * @return Number of signalled descriptors (output parameter)
     */
    public static native int poll(long pollset, long timeout,
                                  long [] descriptors)
        throws Error;


}
