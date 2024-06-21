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

import java.io.Serializable;

import org.apache.tomcat.util.res.StringManager;

/**
 * Base class for the *Chunk implementation to reduce duplication.
 */
public abstract class AbstractChunk implements Cloneable, Serializable {

    private static final long serialVersionUID = 1L;
    protected static final StringManager sm = StringManager.getManager(AbstractChunk.class);

    /*
     * JVMs may limit the maximum array size to slightly less than Integer.MAX_VALUE. On markt's desktop the limit is
     * MAX_VALUE - 2. Comments in the JRE source code for ArrayList and other classes indicate that it may be as low as
     * MAX_VALUE - 8 on some systems.
     */
    public static final int ARRAY_MAX_SIZE = Integer.MAX_VALUE - 8;

    private int hashCode = 0;
    protected boolean hasHashCode = false;

    protected boolean isSet;

    private int limit = -1;

    protected int start;
    protected int end;


    /**
     * Maximum amount of data in this buffer. If -1 or not set, the buffer will grow to {{@link #ARRAY_MAX_SIZE}. Can be
     * smaller than the current buffer size ( which will not shrink ). When the limit is reached, the buffer will be
     * flushed (if out is set) or throw exception.
     *
     * @param limit The new limit
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }


    /**
     * @return the maximum amount of data in the buffer, and -1 if it has not been set
     */
    public int getLimit() {
        return limit;
    }


    protected int getLimitInternal() {
        if (limit > 0) {
            return limit;
        } else {
            return ARRAY_MAX_SIZE;
        }
    }


    /**
     * @return the start position of the data in the buffer
     */
    public int getStart() {
        return start;
    }


    /**
     * Set the start position of the data in the buffer.
     * @param start the new start position
     */
    public void setStart(int start) {
        if (end < start) {
            end = start;
        }
        this.start = start;
    }


    /**
     * @return the end position of the data in the buffer
     */
    public int getEnd() {
        return end;
    }


    /**
     * Set the end position of the data in the buffer.
     * @param end the new end position
     */
    public void setEnd(int end) {
        this.end = end;
    }


    /**
     * @return start
     * @deprecated Unused. This method will be removed in Tomcat 12.
     */
    @Deprecated
    public int getOffset() {
        return start;
    }

    /**
     * Set start.
     * @param off the new start
     * @deprecated Unused. This method will be removed in Tomcat 12.
     */
    @Deprecated
    public void setOffset(int off) {
        if (end < off) {
            end = off;
        }
        start = off;
    }


    /**
     * @return the length of the data in the buffer
     */
    public int getLength() {
        return end - start;
    }


    /**
     * @return {@code true} if the buffer contains no data
     */
    public boolean isNull() {
        if (end > 0) {
            return false;
        }
        return !isSet;
    }


    /**
     * Return the index of the first occurrence of the subsequence of
     * the given String, or -1 if it is not found.
     *
     * @param src the String to look for
     * @param srcStart the subsequence start in the String
     * @param srcLen the subsequence length in the String
     * @param myOffset the index on which to start the search in the buffer
     * @return the position of the first character of the first occurrence
     *         of the subsequence in the buffer, or -1 if not found
     */
    public int indexOf(String src, int srcStart, int srcLen, int myOffset) {
        char first = src.charAt(srcStart);

        // Look for first char
        int srcEnd = srcStart + srcLen;

        mainLoop: for (int i = myOffset + start; i <= (end - srcLen); i++) {
            if (getBufferElement(i) != first) {
                continue;
            }
            // found first char, now look for a match
            int myPos = i + 1;
            for (int srcPos = srcStart + 1; srcPos < srcEnd;) {
                if (getBufferElement(myPos++) != src.charAt(srcPos++)) {
                    continue mainLoop;
                }
            }
            return i - start; // found it
        }
        return -1;
    }


    /**
     * Resets the chunk to an uninitialized state.
     */
    public void recycle() {
        hasHashCode = false;
        isSet = false;
        start = 0;
        end = 0;
    }


    @Override
    public int hashCode() {
        if (hasHashCode) {
            return hashCode;
        }
        int code = 0;

        code = hash();
        hashCode = code;
        hasHashCode = true;
        return code;
    }


    /**
     * @return the hash code for this buffer
     */
    public int hash() {
        int code = 0;
        for (int i = start; i < end; i++) {
            code = code * 37 + getBufferElement(i);
        }
        return code;
    }


    /**
     * @param index the element location in the buffer
     * @return the element
     */
    protected abstract int getBufferElement(int index);
}
