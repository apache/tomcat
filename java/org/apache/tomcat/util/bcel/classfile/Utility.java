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
import java.io.DataInputStream;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Constants;


/**
 * Utility functions that do not really belong to any class in particular.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class Utility {

    /**
     * Shorten long class name <em>str</em>, i.e., chop off the <em>prefix</em>,
     * if the
     * class name starts with this string and the flag <em>chopit</em> is true.
     * Slashes <em>/</em> are converted to dots <em>.</em>.
     *
     * @param str The long class name
     * @param prefix The prefix the get rid off
     * @param chopit Flag that determines whether chopping is executed or not
     * @return Compacted class name
     */
    public static final String compactClassName( String str, String prefix, boolean chopit ) {
        int len = prefix.length();
        str = str.replace('/', '.'); // Is `/' on all systems, even DOS
        if (chopit) {
            // If string starts with `prefix' and contains no further dots
            if (str.startsWith(prefix) && (str.substring(len).indexOf('.') == -1)) {
                str = str.substring(len);
            }
        }
        return str;
    }


    /**
     * Shorten long class names, <em>java/lang/String</em> becomes
     * <em>java.lang.String</em>,
     * e.g.. If <em>chopit</em> is <em>true</em> the prefix <em>java.lang</em>
     * is also removed.
     *
     * @param str The long class name
     * @param chopit Flag that determines whether chopping is executed or not
     * @return Compacted class name
     */
    public static final String compactClassName( String str, boolean chopit ) {
        return compactClassName(str, "java.lang.", chopit);
    }


    // A-Z, g-z, _, $
    private static final int FREE_CHARS = 48;
    static int[] CHAR_MAP = new int[FREE_CHARS];
    static int[] MAP_CHAR = new int[256]; // Reverse map
    static {
        int j = 0;
        for (int i = 'A'; i <= 'Z'; i++) {
            CHAR_MAP[j] = i;
            MAP_CHAR[i] = j;
            j++;
        }
        for (int i = 'g'; i <= 'z'; i++) {
            CHAR_MAP[j] = i;
            MAP_CHAR[i] = j;
            j++;
        }
        CHAR_MAP[j] = '$';
        MAP_CHAR['$'] = j;
        j++;
        CHAR_MAP[j] = '_';
        MAP_CHAR['_'] = j;
    }

    protected static void swallowCodeException(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused start_pc
        file.readUnsignedShort();   // Unused end_pc
        file.readUnsignedShort();   // Unused handler_pc
        file.readUnsignedShort();   // Unused catch_type
    }

    protected static void swallowInnerClass(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused inner_class_index
        file.readUnsignedShort();   // Unused outer_class_index
        file.readUnsignedShort();   // Unused inner_name_index
        file.readUnsignedShort();   // Unused inner_access_flags
    }

    protected static void swallowLineNumber(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused start_pc
        file.readUnsignedShort();   // Unused line_number
    }

    protected static void swallowLocalVariable(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused start_pc
        file.readUnsignedShort();   // Unused length
        file.readUnsignedShort();   // Unused name_index
        file.readUnsignedShort();   // Unused signature_index
        file.readUnsignedShort();   // Unused index
    }

    protected static void swallowStackMapType(DataInput file) throws IOException {
        byte type = file.readByte();
        if ((type < Constants.ITEM_Bogus) || (type > Constants.ITEM_NewObject)) {
            throw new RuntimeException("Illegal type for StackMapType: " + type);
        }
        // Check to see if type has an index
        if ((type == Constants.ITEM_Object) || (type == Constants.ITEM_NewObject)) {
            file.readShort();   // Unused index
        }
    }

    protected static void swallowStackMapTableEntry(DataInputStream file) throws IOException {
        int frame_type = file.read();

        if (frame_type >= Constants.SAME_FRAME && frame_type <= Constants.SAME_FRAME_MAX) {
            // NO-OP
        } else if (frame_type >= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME &&
                frame_type <= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_MAX) {
            Utility.swallowStackMapType(file);  // Unused single stack item
        } else if (frame_type == Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
            file.readShort(); // Unused byte_code_offset_delta
            Utility.swallowStackMapType(file); // Unused single stack item
        } else if (frame_type >= Constants.CHOP_FRAME &&
                frame_type <= Constants.CHOP_FRAME_MAX) {
            file.readShort(); // Unused byte_code_offset_delta
        } else if (frame_type == Constants.SAME_FRAME_EXTENDED) {
            file.readShort(); // Unused byte_code_offset_delta
        } else if (frame_type >= Constants.APPEND_FRAME &&
                frame_type <= Constants.APPEND_FRAME_MAX) {
            file.readShort(); // Unused byte_code_offset_delta
            int number_of_locals = frame_type - 251;
            for (int i = 0; i < number_of_locals; i++) {
                Utility.swallowStackMapType(file);
            }
        } else if (frame_type == Constants.FULL_FRAME) {
            file.readShort(); // Unused byte_code_offset_delta
            int number_of_locals = file.readShort();
            for (int i = 0; i < number_of_locals; i++) {
                Utility.swallowStackMapType(file);
            }
            int number_of_stack_items = file.readShort();
            for (int i = 0; i < number_of_stack_items; i++) {
                Utility.swallowStackMapType(file);
            }
        } else {
            /* Can't happen */
            throw new ClassFormatException (
                    "Invalid frame type found while parsing stack map table: " + frame_type);
        }
    }
}
