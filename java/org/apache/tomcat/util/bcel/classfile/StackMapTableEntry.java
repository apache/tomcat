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
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Constants;

/**
 * This class represents a stack map entry recording the types of
 * local variables and the the of stack items at a given byte code offset.
 * See CLDC specification &sect;5.3.1.2
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     StackMap
 * @see     StackMapType
 */
public final class StackMapTableEntry implements Cloneable {

    private int frame_type;
    private int byte_code_offset_delta;
    private int number_of_locals;
    private StackMapType[] types_of_locals;
    private int number_of_stack_items;
    private StackMapType[] types_of_stack_items;


    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    StackMapTableEntry(DataInputStream file, ConstantPool constant_pool) throws IOException {
        this(file.read(), -1, -1, null, -1, null);
        
        if (frame_type >= Constants.SAME_FRAME && frame_type <= Constants.SAME_FRAME_MAX) {
            byte_code_offset_delta = frame_type - Constants.SAME_FRAME;
        } else if (frame_type >= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME && frame_type <= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_MAX) {
            byte_code_offset_delta = frame_type - Constants.SAME_LOCALS_1_STACK_ITEM_FRAME;
            number_of_stack_items = 1;
            types_of_stack_items = new StackMapType[1];
            types_of_stack_items[0] = new StackMapType(file, constant_pool);
        } else if (frame_type == Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
            byte_code_offset_delta = file.readShort();
            number_of_stack_items = 1;
            types_of_stack_items = new StackMapType[1];
            types_of_stack_items[0] = new StackMapType(file, constant_pool);
        } else if (frame_type >= Constants.CHOP_FRAME && frame_type <= Constants.CHOP_FRAME_MAX) {
            byte_code_offset_delta = file.readShort();
        } else if (frame_type == Constants.SAME_FRAME_EXTENDED) {
            byte_code_offset_delta = file.readShort();
        } else if (frame_type >= Constants.APPEND_FRAME && frame_type <= Constants.APPEND_FRAME_MAX) {
            byte_code_offset_delta = file.readShort();
            number_of_locals = frame_type - 251;
            types_of_locals = new StackMapType[number_of_locals];
            for (int i = 0; i < number_of_locals; i++) {
                types_of_locals[i] = new StackMapType(file, constant_pool);
            }            
        } else if (frame_type == Constants.FULL_FRAME) {        
            byte_code_offset_delta = file.readShort();
            number_of_locals = file.readShort();
            types_of_locals = new StackMapType[number_of_locals];
            for (int i = 0; i < number_of_locals; i++) {
                types_of_locals[i] = new StackMapType(file, constant_pool);
            }
            number_of_stack_items = file.readShort();
            types_of_stack_items = new StackMapType[number_of_stack_items];
            for (int i = 0; i < number_of_stack_items; i++) {
                types_of_stack_items[i] = new StackMapType(file, constant_pool);
            }
        } else {
            /* Can't happen */
            throw new ClassFormatException ("Invalid frame type found while parsing stack map table: " + frame_type);
        }
    }


    public StackMapTableEntry(int tag, int byte_code_offset_delta, int number_of_locals,
            StackMapType[] types_of_locals, int number_of_stack_items,
            StackMapType[] types_of_stack_items) {
        this.frame_type = tag;
        this.byte_code_offset_delta = byte_code_offset_delta;
        this.number_of_locals = number_of_locals;
        this.types_of_locals = types_of_locals;
        this.number_of_stack_items = number_of_stack_items;
        this.types_of_stack_items = types_of_stack_items;
    }


    /**
     * Dump stack map entry
     *
     * @param file Output file stream
     * @throws IOException
     */
    public final void dump( DataOutputStream file ) throws IOException {
        file.write(frame_type);
        if (frame_type >= Constants.SAME_FRAME && frame_type <= Constants.SAME_FRAME_MAX) {
            // nothing to be done
        } else if (frame_type >= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME && frame_type <= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_MAX) {
            types_of_stack_items[0].dump(file);
        } else if (frame_type == Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
            file.writeShort(byte_code_offset_delta);
            types_of_stack_items[0].dump(file);
        } else if (frame_type >= Constants.CHOP_FRAME && frame_type <= Constants.CHOP_FRAME_MAX) {
            file.writeShort(byte_code_offset_delta);
        } else if (frame_type == Constants.SAME_FRAME_EXTENDED) {
            file.writeShort(byte_code_offset_delta);
        } else if (frame_type >= Constants.APPEND_FRAME && frame_type <= Constants.APPEND_FRAME_MAX) {
            file.writeShort(byte_code_offset_delta);
            for (int i = 0; i < number_of_locals; i++) {
                types_of_locals[i].dump(file);
            }
        } else if (frame_type == Constants.FULL_FRAME) {        
            file.writeShort(byte_code_offset_delta);
            file.writeShort(number_of_locals);
            for (int i = 0; i < number_of_locals; i++) {
                types_of_locals[i].dump(file);
            }
            file.writeShort(number_of_stack_items);
            for (int i = 0; i < number_of_stack_items; i++) {
                types_of_stack_items[i].dump(file);
            }
        } else {
            /* Can't happen */
            throw new ClassFormatException ("Invalid Stack map table tag: " + frame_type);
        }
    }


    /**
     * @return String representation.
     */
    @Override
    public final String toString() {
        StringBuffer buf = new StringBuffer(64);
        buf.append("(");
        if (frame_type >= Constants.SAME_FRAME && frame_type <= Constants.SAME_FRAME_MAX) {
            buf.append("SAME");
        } else if (frame_type >= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME && frame_type <= Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_MAX) {
            buf.append("SAME_LOCALS_1_STACK");
        } else if (frame_type == Constants.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
            buf.append("SAME_LOCALS_1_STACK_EXTENDED");
        } else if (frame_type >= Constants.CHOP_FRAME && frame_type <= Constants.CHOP_FRAME_MAX) {
            buf.append("CHOP "+(251-frame_type));
        } else if (frame_type == Constants.SAME_FRAME_EXTENDED) {
            buf.append("SAME_EXTENDED");
        } else if (frame_type >= Constants.APPEND_FRAME && frame_type <= Constants.APPEND_FRAME_MAX) {
            buf.append("APPEND "+(frame_type-251));
        } else if (frame_type == Constants.FULL_FRAME) {        
            buf.append("FULL");
        } else {
            buf.append("UNKNOWN");
        }
        buf.append(", offset delta=").append(byte_code_offset_delta);
        if (number_of_locals > 0) {
            buf.append(", locals={");
            for (int i = 0; i < number_of_locals; i++) {
                buf.append(types_of_locals[i]);
                if (i < number_of_locals - 1) {
                    buf.append(", ");
                }
            }
            buf.append("}");
        }
        if (number_of_stack_items > 0) {
            buf.append(", stack items={");
            for (int i = 0; i < number_of_stack_items; i++) {
                buf.append(types_of_stack_items[i]);
                if (i < number_of_stack_items - 1) {
                    buf.append(", ");
                }
            }
            buf.append("}");
        }
        buf.append(")");
        return buf.toString();
    }


    /**
     * @return deep copy of this object
     */
    public StackMapTableEntry copy() {
        try {
            return (StackMapTableEntry) clone();
        } catch (CloneNotSupportedException e) {
        }
        return null;
    }


}
