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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class ByteBufferUtils {

    private static final Method cleanerMethod;
    private static final Method cleanMethod;

    static {
        try {
            ByteBuffer tempBuffer = ByteBuffer.allocateDirect(0);
            cleanerMethod = tempBuffer.getClass().getMethod("cleaner");
            cleanerMethod.setAccessible(true);
            Object cleanerObject = cleanerMethod.invoke(tempBuffer);
            cleanMethod = cleanerObject.getClass().getMethod("clean");
            cleanMethod.invoke(cleanerObject);
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ByteBufferUtils() {
        // Hide the default constructor since this is a utility class.
    }


    /**
     * Expands buffer to the given size unless it is already as big or bigger.
     * Buffers are assumed to be in 'write to' mode since there would be no need
     * to expand a buffer while it was in 'read from' mode.
     *
     * @param in        Buffer to expand
     * @param newSize   The size t which the buffer should be expanded
     * @return          The expanded buffer with any data from the input buffer
     *                  copied in to it or the original buffer if there was no
     *                  need for expansion
     */
    public static ByteBuffer expand(ByteBuffer in, int newSize) {
        if (in.capacity() >= newSize) {
            return in;
        }

        ByteBuffer out;
        boolean direct = false;
        if (in.isDirect()) {
            out = ByteBuffer.allocateDirect(newSize);
            direct = true;
        } else {
            out = ByteBuffer.allocate(newSize);
        }

        // Copy data
        in.flip();
        out.put(in);

        if (direct) {
            cleanDirectBuffer(in);
        }

        return out;
    }

    public static void cleanDirectBuffer(ByteBuffer buf) {
        try {
            cleanMethod.invoke(cleanerMethod.invoke(buf));
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | SecurityException e) {
            // Ignore
        }
    }

}
