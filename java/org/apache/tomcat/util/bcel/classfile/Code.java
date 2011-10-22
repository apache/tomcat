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
 * This class represents a chunk of Java byte code contained in a
 * method. It is instantiated by the
 * <em>Attribute.readAttribute()</em> method. A <em>Code</em>
 * attribute contains informations about operand stack, local
 * variables, byte code and the exceptions handled within this
 * method.
 *
 * This attribute has attributes itself, namely <em>LineNumberTable</em> which
 * is used for debugging purposes and <em>LocalVariableTable</em> which
 * contains information about the local variables.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Attribute
 * @see     CodeException
 * @see     LineNumberTable
 * @see LocalVariableTable
 */
public final class Code extends Attribute {

    private static final long serialVersionUID = 8936843273318969602L;
    private int max_stack; // Maximum size of stack used by this method
    private int max_locals; // Number of local variables
    private int code_length; // Length of code in bytes
    private byte[] code; // Actual byte code
    private int exception_table_length;
    private CodeException[] exception_table; // Table of handled exceptions
    private int attributes_count; // Attributes of code: LineNumber
    private Attribute[] attributes; // or LocalVariable


    /**
     * @param name_index Index pointing to the name <em>Code</em>
     * @param length Content length in bytes
     * @param file Input stream
     * @param constant_pool Array of constants
     */
    Code(int name_index, int length, DataInputStream file, ConstantPool constant_pool)
            throws IOException {
        // Initialize with some default values which will be overwritten later
        this(name_index, length, file.readUnsignedShort(), file.readUnsignedShort(), (byte[]) null,
                (CodeException[]) null, (Attribute[]) null, constant_pool);
        code_length = file.readInt();
        code = new byte[code_length]; // Read byte code
        file.readFully(code);
        /* Read exception table that contains all regions where an exception
         * handler is active, i.e., a try { ... } catch() block.
         */
        exception_table_length = file.readUnsignedShort();
        exception_table = new CodeException[exception_table_length];
        for (int i = 0; i < exception_table_length; i++) {
            exception_table[i] = new CodeException(file);
        }
        /* Read all attributes, currently `LineNumberTable' and
         * `LocalVariableTable'
         */
        attributes_count = file.readUnsignedShort();
        attributes = new Attribute[attributes_count];
        for (int i = 0; i < attributes_count; i++) {
            attributes[i] = Attribute.readAttribute(file, constant_pool);
        }
        /* Adjust length, because of setAttributes in this(), s.b.  length
         * is incorrect, because it didn't take the internal attributes
         * into account yet! Very subtle bug, fixed in 3.1.1.
         */
        this.length = length;
    }


    /**
     * @param name_index Index pointing to the name <em>Code</em>
     * @param length Content length in bytes
     * @param max_stack Maximum size of stack
     * @param max_locals Number of local variables
     * @param code Actual byte code
     * @param exception_table Table of handled exceptions
     * @param attributes Attributes of code: LineNumber or LocalVariable
     * @param constant_pool Array of constants
     */
    public Code(int name_index, int length, int max_stack, int max_locals, byte[] code,
            CodeException[] exception_table, Attribute[] attributes, ConstantPool constant_pool) {
        super(Constants.ATTR_CODE, name_index, length, constant_pool);
        this.max_stack = max_stack;
        this.max_locals = max_locals;
        setCode(code);
        setExceptionTable(exception_table);
        setAttributes(attributes); // Overwrites length!
    }


    /**
     * Dump code attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    @Override
    public final void dump( DataOutputStream file ) throws IOException {
        super.dump(file);
        file.writeShort(max_stack);
        file.writeShort(max_locals);
        file.writeInt(code_length);
        file.write(code, 0, code_length);
        file.writeShort(exception_table_length);
        for (int i = 0; i < exception_table_length; i++) {
            exception_table[i].dump(file);
        }
        file.writeShort(attributes_count);
        for (int i = 0; i < attributes_count; i++) {
            attributes[i].dump(file);
        }
    }


    /**
     * @return LocalVariableTable of Code, if it has one
     */
    public LocalVariableTable getLocalVariableTable() {
        for (int i = 0; i < attributes_count; i++) {
            if (attributes[i] instanceof LocalVariableTable) {
                return (LocalVariableTable) attributes[i];
            }
        }
        return null;
    }


    /**
     * @return the internal length of this code attribute (minus the first 6 bytes)
     * and excluding all its attributes
     */
    private final int getInternalLength() {
        return 2 /*max_stack*/+ 2 /*max_locals*/+ 4 /*code length*/
                + code_length /*byte-code*/
                + 2 /*exception-table length*/
                + 8 * exception_table_length /* exception table */
                + 2 /* attributes count */;
    }


    /**
     * @return the full size of this code attribute, minus its first 6 bytes,
     * including the size of all its contained attributes
     */
    private final int calculateLength() {
        int len = 0;
        for (int i = 0; i < attributes_count; i++) {
            len += attributes[i].length + 6 /*attribute header size*/;
        }
        return len + getInternalLength();
    }


    /**
     * @param attributes the attributes to set for this Code
     */
    public final void setAttributes( Attribute[] attributes ) {
        this.attributes = attributes;
        attributes_count = (attributes == null) ? 0 : attributes.length;
        length = calculateLength(); // Adjust length
    }


    /**
     * @param code byte code
     */
    public final void setCode( byte[] code ) {
        this.code = code;
        code_length = (code == null) ? 0 : code.length;
        length = calculateLength(); // Adjust length
    }


    /**
     * @param exception_table exception table
     */
    public final void setExceptionTable( CodeException[] exception_table ) {
        this.exception_table = exception_table;
        exception_table_length = (exception_table == null) ? 0 : exception_table.length;
        length = calculateLength(); // Adjust length
    }


    /**
     * @return String representation of code chunk.
     */
    public final String toString( boolean verbose ) {
        StringBuilder buf = new StringBuilder(100);
        buf.append("Code(max_stack = ").append(max_stack).append(", max_locals = ").append(
                max_locals).append(", code_length = ").append(code_length).append(")\n").append(
                Utility.codeToString(code, constant_pool, 0, -1, verbose));
        if (exception_table_length > 0) {
            buf.append("\nException handler(s) = \n").append("From\tTo\tHandler\tType\n");
            for (int i = 0; i < exception_table_length; i++) {
                buf.append(exception_table[i].toString(constant_pool, verbose)).append("\n");
            }
        }
        if (attributes_count > 0) {
            buf.append("\nAttribute(s) = \n");
            for (int i = 0; i < attributes_count; i++) {
                buf.append(attributes[i].toString()).append("\n");
            }
        }
        return buf.toString();
    }


    /**
     * @return String representation of code chunk.
     */
    @Override
    public final String toString() {
        return toString(true);
    }


    /**
     * @return deep copy of this attribute
     *
     * @param _constant_pool the constant pool to duplicate
     */
    @Override
    public Attribute copy( ConstantPool _constant_pool ) {
        Code c = (Code) clone();
        if (code != null) {
            c.code = new byte[code.length];
            System.arraycopy(code, 0, c.code, 0, code.length);
        }
        c.constant_pool = _constant_pool;
        c.exception_table = new CodeException[exception_table_length];
        for (int i = 0; i < exception_table_length; i++) {
            c.exception_table[i] = exception_table[i].copy();
        }
        c.attributes = new Attribute[attributes_count];
        for (int i = 0; i < attributes_count; i++) {
            c.attributes[i] = attributes[i].copy(_constant_pool);
        }
        return c;
    }
}
