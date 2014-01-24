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
package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.tomcat.util.bcel.Constants;

/**
 * This class is derived from the abstract
 * <A HREF="org.apache.tomcat.util.bcel.classfile.Constant.html">Constant</A> class
 * and represents a reference to a Utf8 encoded string.
 *
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Constant
 */
public final class ConstantUtf8 extends Constant {

    private static final long serialVersionUID = 8119001312020421976L;
    private final String bytes;

    private static final int MAX_CACHE_ENTRIES = 20000;
    private static final int INITIAL_CACHE_CAPACITY = (int)(MAX_CACHE_ENTRIES/0.75);
    private static HashMap<String, ConstantUtf8> cache;

    private static synchronized ConstantUtf8 getCachedInstance(String s) {
        if (s.length() > 200) {
            return  new ConstantUtf8(s);
        }
        if (cache == null)  {
            cache = new LinkedHashMap<String, ConstantUtf8>(INITIAL_CACHE_CAPACITY, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ConstantUtf8> eldest) {
                     return size() > MAX_CACHE_ENTRIES;
                }
            };
        }
        ConstantUtf8 result = cache.get(s);
        if (result != null) {
                return result;
            }
        result = new ConstantUtf8(s);
        cache.put(s, result);
        return result;
    }

    private static ConstantUtf8 getInstance(String s) {
        return getCachedInstance(s);
    }

    static ConstantUtf8 getInstance(DataInputStream file) throws IOException {
        return getInstance(file.readUTF());
    }

    /**
     * Initialize instance from file data.
     *
     * @param file Input stream
     * @throws IOException
     */
    ConstantUtf8(DataInput file) throws IOException {
        super(Constants.CONSTANT_Utf8);
        bytes = file.readUTF();
    }


    /**
     * @param bytes Data
     */
    private ConstantUtf8(String bytes) {
        super(Constants.CONSTANT_Utf8);
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null!");
        }
        this.bytes = bytes;
    }


    /**
     * @return Data converted to string.
     */
    public final String getBytes() {
        return bytes;
    }
}
