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

/** OS
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

public class OS {

    /* OS Enums */
    private static final int UNIX     = 1;
    private static final int NETWARE  = 2;
    private static final int WIN32    = 3;
    private static final int WIN64    = 4;
    private static final int LINUX    = 5;
    private static final int SOLARIS  = 6;
    private static final int BSD      = 7;

    /**
     * Check for OS type.
     * @param type OS type to test.
     */
    private static native boolean is(int type);

    static {
        IS_UNIX    = is(UNIX);
        IS_NETWARE = is(NETWARE);
        IS_WIN32   = is(WIN32);
        IS_WIN64   = is(WIN64);
        IS_LINUX   = is(LINUX);
        IS_SOLARIS = is(SOLARIS);
        IS_BSD     = is(BSD);
    }

    public static boolean IS_UNIX    = false;
    public static boolean IS_NETWARE = false;
    public static boolean IS_WIN32   = false;
    public static boolean IS_WIN64   = false;
    public static boolean IS_LINUX   = false;
    public static boolean IS_SOLARIS = false;
    public static boolean IS_BSD     = false;

    /**
     * Get the name of the system default characer set.
     * @param pool the pool to allocate the name from, if needed
     */
    public static native String defaultEncoding(long pool);

    /**
     * Get the name of the current locale character set.
     * Defers to apr_os_default_encoding if the current locale's
     * data can't be retreved on this system.
     * @param pool the pool to allocate the name from, if needed
     */
    public static native String localeEncoding(long pool);

    /**
     * Generate random bytes.
     * @param buf Buffer to fill with random bytes
     * @param len Length of buffer in bytes
     */
    public static native int random(byte [] buf, int len);

    /**
     * Gather system info.
     * <PRE>
     * On exit the inf array will be filled with:
     * inf[0]  - Total usable main memory size
     * inf[1]  - Available memory size
     * inf[2]  - Total page file/swap space size
     * inf[3]  - Page file/swap space still available
     * inf[4]  - Amount of shared memory
     * inf[5]  - Memory used by buffers
     * inf[6]  - Memory Load
     *
     * inf[7]  - Idle Time in microseconds
     * inf[9]  - Kernel Time in microseconds
     * inf[9]  - User Time in microseconds
     *
     * inf[10] - Process creation time (apr_time_t)
     * inf[11] - Process Kernel Time in microseconds
     * inf[12] - Process User Time in microseconds
     *
     * inf[13] - Current working set size.
     * inf[14] - Peak working set size.
     * inf[15] - Number of page faults.
     * </PRE>
     * @param inf array that will be filled with system information.
     *            Array length must be at least 16.
     */
    public static native int info(long [] inf);

}
