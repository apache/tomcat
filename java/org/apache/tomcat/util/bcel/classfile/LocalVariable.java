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
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

import org.apache.tomcat.util.bcel.Constants;

/**
 * This class represents a local variable within a method. It contains its
 * scope, name, signature and index on the method's frame.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     LocalVariableTable
 */
public final class LocalVariable implements Constants, Cloneable, Serializable {

    private static final long serialVersionUID = -914189896372081589L;
    private int start_pc; // Range in which the variable is valid
    private int length;
    private int name_index; // Index in constant pool of variable name
    private int signature_index; // Index of variable signature
    private int index; /* Variable is `index'th local variable on
     * this method's frame.
     */
    private ConstantPool constant_pool;


    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    LocalVariable(DataInput file, ConstantPool constant_pool) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort(), file.readUnsignedShort(), file
                .readUnsignedShort(), file.readUnsignedShort(), constant_pool);
    }


    /**
     * @param start_pc Range in which the variable
     * @param length ... is valid
     * @param name_index Index in constant pool of variable name
     * @param signature_index Index of variable's signature
     * @param index Variable is `index'th local variable on the method's frame
     * @param constant_pool Array of constants
     */
    public LocalVariable(int start_pc, int length, int name_index, int signature_index, int index,
            ConstantPool constant_pool) {
        this.start_pc = start_pc;
        this.length = length;
        this.name_index = name_index;
        this.signature_index = signature_index;
        this.index = index;
        this.constant_pool = constant_pool;
    }


    /**
     * Dump local variable to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    public final void dump( DataOutputStream file ) throws IOException {
        file.writeShort(start_pc);
        file.writeShort(length);
        file.writeShort(name_index);
        file.writeShort(signature_index);
        file.writeShort(index);
    }


    /**
     * @return Variable name.
     */
    public final String getName() {
        ConstantUtf8 c;
        c = (ConstantUtf8) constant_pool.getConstant(name_index, CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return Signature.
     */
    public final String getSignature() {
        ConstantUtf8 c;
        c = (ConstantUtf8) constant_pool.getConstant(signature_index, CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return index of register where variable is stored
     */
    public final int getIndex() {
        return index;
    }


    /**
     * @return string representation.
     */
    @Override
    public final String toString() {
        String name = getName(), signature = Utility.signatureToString(getSignature());
        return "LocalVariable(start_pc = " + start_pc + ", length = " + length + ", index = "
                + index + ":" + signature + " " + name + ")";
    }


    /**
     * @return deep copy of this object
     */
    public LocalVariable copy() {
        try {
            return (LocalVariable) clone();
        } catch (CloneNotSupportedException e) {
        }
        return null;
    }
}
