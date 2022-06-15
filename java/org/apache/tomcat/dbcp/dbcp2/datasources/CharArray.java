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

package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.util.Arrays;

import org.apache.tomcat.dbcp.dbcp2.Utils;

/**
 * A {@code char} array wrapper that does not reveal its contents inadvertently through toString(). In contrast to, for
 * example, AtomicReference which toString()'s its contents.
 *
 * May contain null.
 *
 * @since 2.9.0
 */
final class CharArray {

    static final CharArray NULL = new CharArray((char[]) null);

    private final char[] chars;

    CharArray(final char[] chars) {
        this.chars = Utils.clone(chars);
    }

    CharArray(final String string) {
        this.chars = Utils.toCharArray(string);
    }

    /**
     * Converts the value of char array as a String.
     *
     * @return value as a string, may be null.
     */
    String asString() {
        return Utils.toString(chars);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CharArray)) {
            return false;
        }
        final CharArray other = (CharArray) obj;
        return Arrays.equals(chars, other.chars);
    }

    /**
     * Gets the value of char array.
     *
     * @return value, may be null.
     */
    char[] get() {
        return chars == null ? null : chars.clone();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(chars);
    }

    /**
     * Calls {@code super.toString()} and does not reveal its contents inadvertently.
     */
    @Override
    public String toString() {
        return super.toString();
    }
}
