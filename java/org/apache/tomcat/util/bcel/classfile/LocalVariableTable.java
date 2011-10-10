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
 * This class represents colection of local variables in a
 * method. This attribute is contained in the <em>Code</em> attribute.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Code
 * @see LocalVariable
 */
public class LocalVariableTable extends Attribute {

    private static final long serialVersionUID = -3904314258294133920L;
    private int local_variable_table_length; // Table of local
    private LocalVariable[] local_variable_table; // variables


    /**
     * @param name_index Index in constant pool to `LocalVariableTable'
     * @param length Content length in bytes
     * @param local_variable_table Table of local variables
     * @param constant_pool Array of constants
     */
    public LocalVariableTable(int name_index, int length, LocalVariable[] local_variable_table,
            ConstantPool constant_pool) {
        super(Constants.ATTR_LOCAL_VARIABLE_TABLE, name_index, length, constant_pool);
        setLocalVariableTable(local_variable_table);
    }


    /**
     * Construct object from file stream.
     * @param name_index Index in constant pool
     * @param length Content length in bytes
     * @param file Input stream
     * @param constant_pool Array of constants
     * @throws IOException
     */
    LocalVariableTable(int name_index, int length, DataInputStream file, ConstantPool constant_pool)
            throws IOException {
        this(name_index, length, (LocalVariable[]) null, constant_pool);
        local_variable_table_length = (file.readUnsignedShort());
        local_variable_table = new LocalVariable[local_variable_table_length];
        for (int i = 0; i < local_variable_table_length; i++) {
            local_variable_table[i] = new LocalVariable(file, constant_pool);
        }
    }


    /**
     * Dump local variable table attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    @Override
    public final void dump( DataOutputStream file ) throws IOException {
        super.dump(file);
        file.writeShort(local_variable_table_length);
        for (int i = 0; i < local_variable_table_length; i++) {
            local_variable_table[i].dump(file);
        }
    }


    /** 
     * 
     * @param index the variable slot
     * 
     * @return the first LocalVariable that matches the slot or null if not found
     * 
     * @deprecated since 5.2 because multiple variables can share the
     *             same slot, use getLocalVariable(int index, int pc) instead.
     */
    @java.lang.Deprecated
    public final LocalVariable getLocalVariable( int index ) {
        for (int i = 0; i < local_variable_table_length; i++) {
            if (local_variable_table[i].getIndex() == index) {
                return local_variable_table[i];
            }
        }
        return null;
    }

    public final void setLocalVariableTable( LocalVariable[] local_variable_table ) {
        this.local_variable_table = local_variable_table;
        local_variable_table_length = (local_variable_table == null)
                ? 0
                : local_variable_table.length;
    }


    /**
     * @return String representation.
     */
    @Override
    public final String toString() {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < local_variable_table_length; i++) {
            buf.append(local_variable_table[i].toString());
            if (i < local_variable_table_length - 1) {
                buf.append('\n');
            }
        }
        return buf.toString();
    }


    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy( ConstantPool _constant_pool ) {
        LocalVariableTable c = (LocalVariableTable) clone();
        c.local_variable_table = new LocalVariable[local_variable_table_length];
        for (int i = 0; i < local_variable_table_length; i++) {
            c.local_variable_table[i] = local_variable_table[i].copy();
        }
        c.constant_pool = _constant_pool;
        return c;
    }
}
