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

public class OS {


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
     * Sleep for the specified number of micro-seconds.
     * <br /><b>Warning :</b> May sleep for longer than the specified time.
     * @param t desired amount of time to sleep.
     */
    public static native void sleep(long t);

    /**
     * Generate random bytes.
     * @param buf Buffer to fill with random bytes
     * @param len Length of buffer in bytes
     */
    public static native int random(byte [] buf, int len);

}
