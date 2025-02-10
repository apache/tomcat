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
package org.apache.tomcat.util;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 * Utilities for handling Buffer.
 */
public class BufferUtil {

    /**
     * Reset buffer pointer, and erase content if clearContent is true.
     *
     * @param bb target buffer
     * @param eraseData erase data or not.
     */
    public static void resetBuff(Buffer bb, boolean eraseData) {
        bb.position(0).limit(0);
        if(eraseData) {
            clearContent(bb);
        }
    }

    /**
     * Reset buffer and erase content implicitly. This is convenient method of:
     * <pre>
     * resetBuff(bb,true);
     * </pre>
     *
     * @param bb target Buffer
     * @see BZ69486
     */
    public static void resetBuff(Buffer bb) {
        resetBuff(bb, true);
    }

    private static void clearContent(Buffer bb) {
        if (bb.hasArray()) {
            if (bb instanceof ByteBuffer) {
                Arrays.fill(((ByteBuffer) bb).array(), (byte) 0);
            } else if (bb instanceof CharBuffer) {
                Arrays.fill(((CharBuffer) bb).array(), (char) 0);
            } else if (bb instanceof DoubleBuffer) {
                Arrays.fill(((DoubleBuffer) bb).array(), 0);
            } else if (bb instanceof FloatBuffer) {
                Arrays.fill(((FloatBuffer) bb).array(), 0);
            } else if (bb instanceof IntBuffer) {
                Arrays.fill(((IntBuffer) bb).array(), 0);
            } else if (bb instanceof IntBuffer) {
                Arrays.fill(((LongBuffer) bb).array(), 0);
            } else if (bb instanceof IntBuffer) {
                Arrays.fill(((ShortBuffer) bb).array(), (short) 0);
            }
        }
    }
}
