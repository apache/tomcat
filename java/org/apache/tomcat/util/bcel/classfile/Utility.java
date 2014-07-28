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

    static void swallowCodeException(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused start_pc
        file.readUnsignedShort();   // Unused end_pc
        file.readUnsignedShort();   // Unused handler_pc
        file.readUnsignedShort();   // Unused catch_type
    }

    static void swallowInnerClass(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused inner_class_index
        file.readUnsignedShort();   // Unused outer_class_index
        file.readUnsignedShort();   // Unused inner_name_index
        file.readUnsignedShort();   // Unused inner_access_flags
    }

    static void swallowLineNumber(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused start_pc
        file.readUnsignedShort();   // Unused line_number
    }

    static void swallowLocalVariable(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused start_pc
        file.readUnsignedShort();   // Unused length
        file.readUnsignedShort();   // Unused name_index
        file.readUnsignedShort();   // Unused signature_index
        file.readUnsignedShort();   // Unused index
    }

    static void swallowStackMap(DataInput file) throws IOException {
        int map_length = file.readUnsignedShort();
        for (int i = 0; i < map_length; i++) {
            Utility.swallowStackMapEntry(file);
        }
    }

    static void swallowStackMapTable(DataInputStream file) throws IOException {
        int map_length = file.readUnsignedShort();
        for (int i = 0; i < map_length; i++) {
            Utility.swallowStackMapTableEntry(file);
        }
    }

    static void swallowStackMapType(DataInput file) throws IOException {
        byte type = file.readByte();
        if ((type < Constants.ITEM_Bogus) || (type > Constants.ITEM_NewObject)) {
            throw new ClassFormatException("Illegal type for StackMapType: " + type);
        }
        // Check to see if type has an index
        if ((type == Constants.ITEM_Object) || (type == Constants.ITEM_NewObject)) {
            file.readShort();   // Unused index
        }
    }

    static void swallowStackMapEntry(DataInput file) throws IOException {
        file.readShort();   // Unused byte_code_offset
        int number_of_locals = file.readShort();
        for (int i = 0; i < number_of_locals; i++) {
            Utility.swallowStackMapType(file);
        }
        int number_of_stack_items = file.readShort();
        for (int i = 0; i < number_of_stack_items; i++) {
            Utility.swallowStackMapType(file);
        }
    }

    static void swallowStackMapTableEntry(DataInputStream file) throws IOException {
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

    static void swallowUnknownAttribute(DataInput file, int length) throws IOException {
        if (length > 0) {
            byte[] bytes = new byte[length];
            file.readFully(bytes);
        }
    }

    static void swallowSignature(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused signature_index
    }

    static void swallowSynthetic(DataInput file, int length) throws IOException {
        if (length > 0) {
            byte[] bytes = new byte[length];
            file.readFully(bytes);
            throw new ClassFormatException("Synthetic attribute with length > 0");
        }
    }

    static void swallowBootstrapMethods(DataInput file) throws IOException {
        int num_bootstrap_methods = file.readUnsignedShort();
        for (int i = 0; i < num_bootstrap_methods; i++) {
            file.readUnsignedShort();   // Unused bootstrap_method_ref
            int num_bootstrap_args = file.readUnsignedShort();
            for (int j = 0; j < num_bootstrap_args; j++) {
                file.readUnsignedShort(); // Unused bootstrap method argument
            }
        }
    }

    static void swallowMethodParameters(DataInput file) throws IOException {
        int parameters_count = file.readUnsignedByte();
        for (int i = 0; i < parameters_count; i++) {
            file.readUnsignedShort();   // Unused name_index
            file.readUnsignedShort();   // Unused access_flags
        }
    }
}
