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
 * This class represents the table of exceptions that are thrown by a
 * method. This attribute may be used once per method.  The name of
 * this class is <em>ExceptionTable</em> for historical reasons; The
 * Java Virtual Machine Specification, Second Edition defines this
 * attribute using the name <em>Exceptions</em> (which is inconsistent
 * with the other classes).
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Code
 */
public final class ExceptionTable extends Attribute {

    private static final long serialVersionUID = -5109672682663772900L;
    private int number_of_exceptions; // Table of indices into
    private int[] exception_index_table; // constant pool


    /**
     * @param name_index Index in constant pool
     * @param length Content length in bytes
     * @param exception_index_table Table of indices in constant pool
     * @param constant_pool Array of constants
     */
    public ExceptionTable(int name_index, int length, int[] exception_index_table,
            ConstantPool constant_pool) {
        super(Constants.ATTR_EXCEPTIONS, name_index, length, constant_pool);
        setExceptionIndexTable(exception_index_table);
    }


    /**
     * Construct object from file stream.
     * @param name_index Index in constant pool
     * @param length Content length in bytes
     * @param file Input stream
     * @param constant_pool Array of constants
     * @throws IOException
     */
    ExceptionTable(int name_index, int length, DataInputStream file, ConstantPool constant_pool)
            throws IOException {
        this(name_index, length, (int[]) null, constant_pool);
        number_of_exceptions = file.readUnsignedShort();
        exception_index_table = new int[number_of_exceptions];
        for (int i = 0; i < number_of_exceptions; i++) {
            exception_index_table[i] = file.readUnsignedShort();
        }
    }


    /**
     * Dump exceptions attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    @Override
    public final void dump( DataOutputStream file ) throws IOException {
        super.dump(file);
        file.writeShort(number_of_exceptions);
        for (int i = 0; i < number_of_exceptions; i++) {
            file.writeShort(exception_index_table[i]);
        }
    }


    /**
     * @param exception_index_table the list of exception indexes
     * Also redefines number_of_exceptions according to table length.
     */
    public final void setExceptionIndexTable( int[] exception_index_table ) {
        this.exception_index_table = exception_index_table;
        number_of_exceptions = (exception_index_table == null) ? 0 : exception_index_table.length;
    }


    /**
     * @return String representation, i.e., a list of thrown exceptions.
     */
    @Override
    public final String toString() {
        StringBuilder buf = new StringBuilder();
        String str;
        for (int i = 0; i < number_of_exceptions; i++) {
            str = constant_pool.getConstantString(exception_index_table[i],
                    Constants.CONSTANT_Class);
            buf.append(Utility.compactClassName(str, false));
            if (i < number_of_exceptions - 1) {
                buf.append(", ");
            }
        }
        return buf.toString();
    }


    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy( ConstantPool _constant_pool ) {
        ExceptionTable c = (ExceptionTable) clone();
        if (exception_index_table != null) {
            c.exception_index_table = new int[exception_index_table.length];
            System.arraycopy(exception_index_table, 0, c.exception_index_table, 0,
                    exception_index_table.length);
        }
        c.constant_pool = _constant_pool;
        return c;
    }
}
