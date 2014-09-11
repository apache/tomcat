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
            swallowStackMapEntry(file);
        }
    }

    static void swallowStackMapTable(DataInputStream file) throws IOException {
        int map_length = file.readUnsignedShort();
        for (int i = 0; i < map_length; i++) {
            swallowStackMapTableEntry(file);
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
            swallowStackMapType(file);
        }
        int number_of_stack_items = file.readShort();
        for (int i = 0; i < number_of_stack_items; i++) {
            swallowStackMapType(file);
        }
    }

    static void swallowStackMapTableEntry(DataInputStream file) throws IOException {
        int frame_type = file.read();

        if (frame_type >= Constants.SAME_FRAME && frame_type <= Constants.SAME_FRAME_MAX) {
            // NO-OP
        } else if (frame_type >= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME &&
                frame_type <= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_MAX) {
            swallowStackMapType(file);  // Unused single stack item
        } else if (frame_type == Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
            file.readShort(); // Unused byte_code_offset_delta
            swallowStackMapType(file); // Unused single stack item
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
                swallowStackMapType(file);
            }
        } else if (frame_type == Constants.FULL_FRAME) {
            file.readShort(); // Unused byte_code_offset_delta
            int number_of_locals = file.readShort();
            for (int i = 0; i < number_of_locals; i++) {
                swallowStackMapType(file);
            }
            int number_of_stack_items = file.readShort();
            for (int i = 0; i < number_of_stack_items; i++) {
                swallowStackMapType(file);
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

    static void swallowSynthetic(int length) {
        if (length > 0) {
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

    static void swallowSourceFile(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused sourcefile_index
    }

    static void swallowConstantValue(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused constantvalue_index
    }

    static void swallowCode(DataInputStream file, ConstantPool constant_pool) throws IOException {
        file.readUnsignedShort();   // Unused max_stack
        file.readUnsignedShort();   // Unused max_locals
        int code_length = file.readInt();
        byte[] code = new byte[code_length]; // Read byte code
        file.readFully(code);
        /* Read exception table that contains all regions where an exception
         * handler is active, i.e., a try { ... } catch() block.
         */
        int exception_table_length = file.readUnsignedShort();
        for (int i = 0; i < exception_table_length; i++) {
            swallowCodeException(file);
        }
        /* Read all attributes, currently `LineNumberTable' and
         * `LocalVariableTable'
         */
        int attributes_count = file.readUnsignedShort();
        for (int i = 0; i < attributes_count; i++) {
            Attribute.readAttribute(file, constant_pool);
        }
    }

    static void swallowExceptionTable(DataInput file) throws IOException {
        int number_of_exceptions = file.readUnsignedShort();
        for (int i = 0; i < number_of_exceptions; i++) {
            file.readUnsignedShort(); // Unused exception index
        }
    }

    static void swallowLineNumberTable(DataInput file) throws IOException {
        int line_number_table_length = (file.readUnsignedShort());
        for (int i = 0; i < line_number_table_length; i++) {
            swallowLineNumber(file);
        }
    }

    static void swallowLocalVariableTable(DataInput file) throws IOException {
        int local_variable_table_length = (file.readUnsignedShort());
        for (int i = 0; i < local_variable_table_length; i++) {
            swallowLocalVariable(file);
        }
    }

    static void swallowLocalVariableTypeTable(DataInput file) throws IOException {
        int local_variable_type_table_length = (file.readUnsignedShort());
        for(int i=0; i < local_variable_type_table_length; i++) {
            swallowLocalVariable(file);
        }
    }

    static void swallowInnerClasses(DataInput file) throws IOException {
        int number_of_classes = file.readUnsignedShort();
        for (int i = 0; i < number_of_classes; i++) {
            swallowInnerClass(file);
        }
    }

    static void swallowDeprecated(int length) {
        if (length > 0) {
            throw new ClassFormatException("Deprecated attribute with length > 0");
        }
    }

    static void swallowPMCClass(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused pmg_index
        file.readUnsignedShort();   // Unused pmg_class_index
    }

    static void swallowEnclosingMethod(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused class index
        file.readUnsignedShort();   // Unused method index
    }

    static void swallowConstantCP(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused class index
        file.readUnsignedShort();   // Unused name and type index
    }

    static void swallowConstantMethodHandle(DataInput file) throws IOException {
        file.readUnsignedByte();    // Unused reference_kind
        file.readUnsignedShort();   // Unused reference_index
    }

    static void swallowConstantString(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused string index
    }

    static void swallowConstantNameAndType(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused name index
        file.readUnsignedShort();   // Unused signature index
    }

    static void swallowConstantMethodType(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused descriptor_index
    }

    static void swallowConstantInvokeDynamic(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused bootstrap_method_attr_index
        file.readUnsignedShort();   // Unused name_and_type_index
    }

    static void swallowAnnotations(DataInput file)
            throws IOException {
        final int annotation_table_length = (file.readUnsignedShort());
        for (int i = 0; i < annotation_table_length; i++) {
            swallowAnnotationEntry(file);
        }
    }

    static void swallowAnnotationEntry(DataInput file)
            throws IOException {
        file.readUnsignedShort();   // Unused type index
        final int num_element_value_pairs = (file.readUnsignedShort());
        for (int i = 0; i < num_element_value_pairs; i++) {
            file.readUnsignedShort();   // Unused name index
            swallowElementValue(file);
        }
    }

    static void swallowElementValue(DataInput file) throws IOException {

        byte type = file.readByte();
        switch (type) {
        case 'B': // byte
        case 'C': // char
        case 'D': // double
        case 'F': // float
        case 'I': // int
        case 'J': // long
        case 'S': // short
        case 'Z': // boolean
        case 's': // String
        case 'c': // Class
            file.readUnsignedShort();   // Unused value index
            break;
        case 'e': // Enum constant
            file.readUnsignedShort();   // Unused type_index
            file.readUnsignedShort();   // Unused value index
            break;
        case '@': // Annotation
            swallowAnnotationEntry(file);
            break;
        case '[': // Array
            int numArrayVals = file.readUnsignedShort();
            for (int j = 0; j < numArrayVals; j++)
            {
                swallowElementValue(file);
            }
            break;
        default:
            throw new RuntimeException(
                    "Unexpected element value kind in annotation: " + type);
        }
    }

    static void swallowFieldOrMethod(DataInputStream file, ConstantPool constant_pool)
            throws IOException {
        file.readUnsignedShort();   // Unused access flags
        file.readUnsignedShort();   // name index
        file.readUnsignedShort();   // signature index

        int attributes_count = file.readUnsignedShort();
        for (int i = 0; i < attributes_count; i++) {
            swallowAttribute(file, constant_pool);
        }
    }

    static void swallowAttribute(DataInputStream file, ConstantPool constant_pool)
            throws IOException {
        byte tag = Constants.ATTR_UNKNOWN;  // Unknown attribute
        // Get class name from constant pool via `name_index' indirection
        int name_index = file.readUnsignedShort();
        ConstantUtf8 c =
                (ConstantUtf8) constant_pool.getConstant(name_index, Constants.CONSTANT_Utf8);
        String name = c.getBytes();
        // Length of data in bytes
        int length = file.readInt();
        // Compare strings to find known attribute
        for (byte i = 0; i < Constants.KNOWN_ATTRIBUTES; i++) {
            if (name.equals(Constants.ATTRIBUTE_NAMES[i])) {
                tag = i; // found!
                break;
            }
        }
        // Call proper constructor, depending on `tag'
        switch (tag)
        {
        case Constants.ATTR_UNKNOWN:
            swallowUnknownAttribute(file, length);
            break;
        case Constants.ATTR_CONSTANT_VALUE:
            swallowConstantValue(file);
            break;
        case Constants.ATTR_SOURCE_FILE:
            swallowSourceFile(file);
            break;
        case Constants.ATTR_CODE:
            swallowCode(file, constant_pool);
            break;
        case Constants.ATTR_EXCEPTIONS:
            swallowExceptionTable(file);
            break;
        case Constants.ATTR_LINE_NUMBER_TABLE:
            swallowLineNumberTable(file);
            break;
        case Constants.ATTR_LOCAL_VARIABLE_TABLE:
            swallowLocalVariableTable(file);
            break;
        case Constants.ATTR_INNER_CLASSES:
            swallowInnerClasses(file);
            break;
        case Constants.ATTR_SYNTHETIC:
            swallowSynthetic(length);
            break;
        case Constants.ATTR_DEPRECATED:
            swallowDeprecated(length);
            break;
        case Constants.ATTR_PMG:
            swallowPMCClass(file);
            break;
        case Constants.ATTR_SIGNATURE:
            swallowSignature(file);
            break;
        case Constants.ATTR_STACK_MAP:
            swallowStackMap(file);
            break;
        case Constants.ATTR_RUNTIME_VISIBLE_ANNOTATIONS:
        case Constants.ATTR_RUNTIME_INVISIBLE_ANNOTATIONS:
        case Constants.ATTR_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS:
        case Constants.ATTR_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS:
            swallowAnnotations(file);
            break;
        case Constants.ATTR_ANNOTATION_DEFAULT:
            swallowAnnotationDefault(file);
            break;
        case Constants.ATTR_LOCAL_VARIABLE_TYPE_TABLE:
            swallowLocalVariableTypeTable(file);
            break;
        case Constants.ATTR_ENCLOSING_METHOD:
            swallowEnclosingMethod(file);
            break;
        case Constants.ATTR_STACK_MAP_TABLE:
            swallowStackMapTable(file);
            break;
        case Constants.ATTR_BOOTSTRAP_METHODS:
            swallowBootstrapMethods(file);
            break;
        case Constants.ATTR_METHOD_PARAMETERS:
            swallowMethodParameters(file);
            break;
        default: // Never reached
            throw new IllegalStateException("Unrecognized attribute type tag parsed: " + tag);
        }
    }

    static void swallowAnnotationDefault(DataInput file) throws IOException {
        swallowElementValue(file);
    }
}
