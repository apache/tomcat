/*
 * Copyright  2000-2009 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
package org.apache.tomcat.util.bcel.generic;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.classfile.ConstantPool;
import org.apache.tomcat.util.bcel.util.ByteSequence;

/** 
 * Abstract super class for all Java byte codes.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class Instruction implements Cloneable, Serializable {

    protected short length = 1; // Length of instruction in bytes 
    protected short opcode = -1; // Opcode number
    private static InstructionComparator cmp = InstructionComparator.DEFAULT;


    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    Instruction() {
    }


    public Instruction(short opcode, short length) {
        this.length = length;
        this.opcode = opcode;
    }


    /**
     * Dump instruction as byte code to stream out.
     * @param out Output stream
     */
    public void dump( DataOutputStream out ) throws IOException {
        out.writeByte(opcode); // Common for all instructions
    }


    /** @return name of instruction, i.e., opcode name
     */
    public String getName() {
        return Constants.OPCODE_NAMES[opcode];
    }


    /**
     * Long output format:
     *
     * &lt;name of opcode&gt; "["&lt;opcode number&gt;"]" 
     * "("&lt;length of instruction&gt;")"
     *
     * @param verbose long/short format switch
     * @return mnemonic for instruction
     */
    public String toString( boolean verbose ) {
        if (verbose) {
            return getName() + "[" + opcode + "](" + length + ")";
        } else {
            return getName();
        }
    }


    /**
     * @return mnemonic for instruction in verbose format
     */
    public String toString() {
        return toString(true);
    }


    /**
     * @return mnemonic for instruction with sumbolic references resolved
     */
    public String toString( ConstantPool cp ) {
        return toString(false);
    }


    


    /**
     * Read needed data (e.g. index) from file.
     *
     * @param bytes byte sequence to read from
     * @param wide "wide" instruction flag
     */
    protected void initFromFile( ByteSequence bytes, boolean wide ) throws IOException {
    }


    

    /**
     * This method also gives right results for instructions whose
     * effect on the stack depends on the constant pool entry they
     * reference.
     *  @return Number of words consumed from stack by this instruction,
     * or Constants.UNPREDICTABLE, if this can not be computed statically
     */
    public int consumeStack( ConstantPoolGen cpg ) {
        return Constants.CONSUME_STACK[opcode];
    }


    /**
     * This method also gives right results for instructions whose
     * effect on the stack depends on the constant pool entry they
     * reference.
     * @return Number of words produced onto stack by this instruction,
     * or Constants.UNPREDICTABLE, if this can not be computed statically
     */
    public int produceStack( ConstantPoolGen cpg ) {
        return Constants.PRODUCE_STACK[opcode];
    }


    /**
     * @return this instructions opcode
     */
    public short getOpcode() {
        return opcode;
    }


    /**
     * @return length (in bytes) of instruction
     */
    public int getLength() {
        return length;
    }


    /** Some instructions may be reused, so don't do anything by default.
     */
    void dispose() {
    }


    


    


    /** Check for equality, delegated to comparator
     * @return true if that is an Instruction and has the same opcode
     */
    public boolean equals( Object that ) {
        return (that instanceof Instruction) ? cmp.equals(this, (Instruction) that) : false;
    }
}
