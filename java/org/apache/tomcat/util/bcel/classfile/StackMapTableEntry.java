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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.Serializable;

import org.apache.tomcat.util.bcel.Constants;

/**
 * This class represents a stack map entry recording the types of
 * local variables and the the of stack items at a given byte code offset.
 * See CLDC specification &sect;5.3.1.2
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     StackMap
 */
public final class StackMapTableEntry implements Cloneable, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    StackMapTableEntry(DataInputStream file) throws IOException {
        int frame_type = file.read();
        int number_of_locals = -1;
        int number_of_stack_items = -1;

        if (frame_type >= Constants.SAME_FRAME && frame_type <= Constants.SAME_FRAME_MAX) {
            // NO-OP
        } else if (frame_type >= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME && frame_type <= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_MAX) {
            number_of_stack_items = 1;
            Utility.swallowStackMapType(file);
        } else if (frame_type == Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
            file.readShort(); // Unused byte_code_offset_delta
            number_of_stack_items = 1;
            Utility.swallowStackMapType(file);
        } else if (frame_type >= Constants.CHOP_FRAME && frame_type <= Constants.CHOP_FRAME_MAX) {
            file.readShort(); // Unused byte_code_offset_delta
        } else if (frame_type == Constants.SAME_FRAME_EXTENDED) {
            file.readShort(); // Unused byte_code_offset_delta
        } else if (frame_type >= Constants.APPEND_FRAME && frame_type <= Constants.APPEND_FRAME_MAX) {
            file.readShort(); // Unused byte_code_offset_delta
            number_of_locals = frame_type - 251;
            for (int i = 0; i < number_of_locals; i++) {
                Utility.swallowStackMapType(file);
            }
        } else if (frame_type == Constants.FULL_FRAME) {
            file.readShort(); // Unused byte_code_offset_delta
            number_of_locals = file.readShort();
            for (int i = 0; i < number_of_locals; i++) {
                Utility.swallowStackMapType(file);
            }
            number_of_stack_items = file.readShort();
            for (int i = 0; i < number_of_stack_items; i++) {
                Utility.swallowStackMapType(file);
            }
        } else {
            /* Can't happen */
            throw new ClassFormatException ("Invalid frame type found while parsing stack map table: " + frame_type);
        }
    }
}
