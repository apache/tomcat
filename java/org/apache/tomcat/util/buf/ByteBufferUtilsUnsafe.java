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
package org.apache.tomcat.util.buf;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.res.StringManager;

/*
 * This functionality is in a separate class so it is only loaded if cleanDirectBuffer() is called. This is because the
 * use of unsafe triggers an unavoidable warning with Java 24.
 */
class ByteBufferUtilsUnsafe {

    private static final StringManager sm = StringManager.getManager(ByteBufferUtilsUnsafe.class);
    private static final Log log = LogFactory.getLog(ByteBufferUtilsUnsafe.class);

    private static final Object unsafe;
    private static final Method cleanerMethod;
    private static final Method cleanMethod;
    private static final Method invokeCleanerMethod;

    static {
        ByteBuffer tempBuffer = ByteBuffer.allocateDirect(0);
        Method cleanerMethodLocal = null;
        Method cleanMethodLocal = null;
        Object unsafeLocal = null;
        Method invokeCleanerMethodLocal = null;
        if (JreCompat.isJre9Available()) {
            try {
                Class<?> clazz = Class.forName("sun.misc.Unsafe");
                Field theUnsafe = clazz.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                unsafeLocal = theUnsafe.get(null);
                invokeCleanerMethodLocal = clazz.getMethod("invokeCleaner", ByteBuffer.class);
                invokeCleanerMethodLocal.invoke(unsafeLocal, tempBuffer);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException |
                    NoSuchMethodException | SecurityException | ClassNotFoundException | NoSuchFieldException e) {
                log.warn(sm.getString("byteBufferUtils.cleaner"), e);
                unsafeLocal = null;
                invokeCleanerMethodLocal = null;
            }
        } else {
            try {
                cleanerMethodLocal = tempBuffer.getClass().getMethod("cleaner");
                cleanerMethodLocal.setAccessible(true);
                Object cleanerObject = cleanerMethodLocal.invoke(tempBuffer);
                cleanMethodLocal = cleanerObject.getClass().getMethod("clean");
                cleanMethodLocal.invoke(cleanerObject);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException |
                    InvocationTargetException e) {
                log.warn(sm.getString("byteBufferUtils.cleaner"), e);
                cleanerMethodLocal = null;
                cleanMethodLocal = null;
            }
        }
        cleanerMethod = cleanerMethodLocal;
        cleanMethod = cleanMethodLocal;
        unsafe = unsafeLocal;
        invokeCleanerMethod = invokeCleanerMethodLocal;
    }

    private ByteBufferUtilsUnsafe() {
        // Hide the default constructor since this is a utility class.
    }


    /**
     * Clean specified direct buffer. This will cause an unavoidable warning on Java 24 and newer.
     *
     * @param buf the buffer to clean
     */
    static void cleanDirectBuffer(ByteBuffer buf) {
        if (cleanMethod != null) {
            try {
                cleanMethod.invoke(cleanerMethod.invoke(buf));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException |
                    SecurityException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("byteBufferUtils.cleaner"), e);
                }
            }
        } else if (invokeCleanerMethod != null) {
            try {
                invokeCleanerMethod.invoke(unsafe, buf);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException |
                    SecurityException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("byteBufferUtils.cleaner"), e);
                }
            }
        }
    }

}
