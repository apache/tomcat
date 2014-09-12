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
 *
 */
package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;

/**
 * Utility functions that do not really belong to any class in particular.
 *
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
final class Utility {

    private Utility() {
        // Hide default constructor
    }

    /**
     * Shorten long class name <em>str</em>, i.e., chop off the <em>prefix</em>,
     * if the
     * class name starts with this string and the flag <em>chopit</em> is true.
     * Slashes <em>/</em> are converted to dots <em>.</em>.
     *
     * @param str The long class name
     * @return Compacted class name
     */
    static String compactClassName(String str) {
        return str.replace('/', '.'); // Is `/' on all systems, even DOS
    }

    static void skipFully(DataInput file, int length) throws IOException {
        int total = file.skipBytes(length);
        if (total != length) {
            throw new EOFException();
        }
    }

    static void swallowConstantCP(DataInput file) throws IOException {
        // file.readUnsignedShort(); // Unused class index
        // file.readUnsignedShort(); // Unused name and type index
        skipFully(file, 4);
    }

    static void swallowConstantMethodHandle(DataInput file) throws IOException {
        // file.readUnsignedByte();  // Unused reference_kind
        // file.readUnsignedShort(); // Unused reference_index
        skipFully(file, 3);
    }

    static void swallowConstantString(DataInput file) throws IOException {
        // file.readUnsignedShort(); // Unused string index
        skipFully(file, 2);
    }

    static void swallowConstantNameAndType(DataInput file) throws IOException {
        // file.readUnsignedShort(); // Unused name index
        // file.readUnsignedShort(); // Unused signature index
        skipFully(file, 4);
    }

    static void swallowConstantMethodType(DataInput file) throws IOException {
        // file.readUnsignedShort(); // Unused descriptor_index
        skipFully(file, 2);
    }

    static void swallowConstantInvokeDynamic(DataInput file) throws IOException {
        // file.readUnsignedShort(); // Unused bootstrap_method_attr_index
        // file.readUnsignedShort(); // Unused name_and_type_index
        skipFully(file, 4);
    }

    static void swallowFieldOrMethod(DataInput file)
            throws IOException {
        // file.readUnsignedShort(); // Unused access flags
        // file.readUnsignedShort(); // name index
        // file.readUnsignedShort(); // signature index
        skipFully(file, 6);

        int attributes_count = file.readUnsignedShort();
        for (int i = 0; i < attributes_count; i++) {
            swallowAttribute(file);
        }
    }

    static void swallowAttribute(DataInput file)
            throws IOException {
        //file.readUnsignedShort();   // Unused name index
        skipFully(file, 2);
        // Length of data in bytes
        int length = file.readInt();
        skipFully(file, length);
    }

}
