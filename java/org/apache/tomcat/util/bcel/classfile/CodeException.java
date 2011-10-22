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
 * This class represents an entry in the exception table of the <em>Code</em>
 * attribute and is used only there. It contains a range in which a
 * particular exception handler is active.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Code
 */
public final class CodeException implements Cloneable, Constants, Serializable {

    private static final long serialVersionUID = -6351674720658890686L;
    private int start_pc; // Range in the code the exception handler is
    private int end_pc; // active. start_pc is inclusive, end_pc exclusive
    private int handler_pc; /* Starting address of exception handler, i.e.,
     * an offset from start of code.
     */
    private int catch_type; /* If this is zero the handler catches any
     * exception, otherwise it points to the
     * exception class which is to be caught.
     */


    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    CodeException(DataInput file) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort(), file.readUnsignedShort(), file
                .readUnsignedShort());
    }


    /**
     * @param start_pc Range in the code the exception handler is active,
     * start_pc is inclusive while
     * @param end_pc is exclusive
     * @param handler_pc Starting address of exception handler, i.e.,
     * an offset from start of code.
     * @param catch_type If zero the handler catches any
     * exception, otherwise it points to the exception class which is
     * to be caught.
     */
    public CodeException(int start_pc, int end_pc, int handler_pc, int catch_type) {
        this.start_pc = start_pc;
        this.end_pc = end_pc;
        this.handler_pc = handler_pc;
        this.catch_type = catch_type;
    }


    /**
     * Dump code exception to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    public final void dump( DataOutputStream file ) throws IOException {
        file.writeShort(start_pc);
        file.writeShort(end_pc);
        file.writeShort(handler_pc);
        file.writeShort(catch_type);
    }


    /**
     * @return String representation.
     */
    @Override
    public final String toString() {
        return "CodeException(start_pc = " + start_pc + ", end_pc = " + end_pc + ", handler_pc = "
                + handler_pc + ", catch_type = " + catch_type + ")";
    }


    /**
     * @return String representation.
     */
    public final String toString( ConstantPool cp, boolean verbose ) {
        String str;
        if (catch_type == 0) {
            str = "<Any exception>(0)";
        } else {
            str = Utility.compactClassName(cp.getConstantString(catch_type, CONSTANT_Class), false)
                    + (verbose ? "(" + catch_type + ")" : "");
        }
        return start_pc + "\t" + end_pc + "\t" + handler_pc + "\t" + str;
    }


    /**
     * @return deep copy of this object
     */
    public CodeException copy() {
        try {
            return (CodeException) clone();
        } catch (CloneNotSupportedException e) {
        }
        return null;
    }
}
