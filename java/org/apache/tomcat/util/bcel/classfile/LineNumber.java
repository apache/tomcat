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

/**
 * This class represents a (PC offset, line number) pair, i.e., a line number in
 * the source that corresponds to a relative address in the byte code. This
 * is used for debugging purposes.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     LineNumberTable
 */
public final class LineNumber implements Cloneable, Serializable {

    private static final long serialVersionUID = 3393830630264494355L;
    private int start_pc; // Program Counter (PC) corresponds to line
    private int line_number; // number in source file


    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    LineNumber(DataInput file) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort());
    }


    /**
     * @param start_pc Program Counter (PC) corresponds to
     * @param line_number line number in source file
     */
    public LineNumber(int start_pc, int line_number) {
        this.start_pc = start_pc;
        this.line_number = line_number;
    }


    /**
     * Dump line number/pc pair to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    public final void dump( DataOutputStream file ) throws IOException {
        file.writeShort(start_pc);
        file.writeShort(line_number);
    }


    /**
     * @return String representation
     */
    @Override
    public final String toString() {
        return "LineNumber(" + start_pc + ", " + line_number + ")";
    }


    /**
     * @return deep copy of this object
     */
    public LineNumber copy() {
        try {
            return (LineNumber) clone();
        } catch (CloneNotSupportedException e) {
        }
        return null;
    }
}
