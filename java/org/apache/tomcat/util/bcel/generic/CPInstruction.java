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
import org.apache.tomcat.util.bcel.classfile.Constant;
import org.apache.tomcat.util.bcel.classfile.ConstantClass;
import org.apache.tomcat.util.bcel.classfile.ConstantPool;
import org.apache.tomcat.util.bcel.util.ByteSequence;

/** 
 * Abstract super class for instructions that use an index into the 
 * constant pool such as LDC, INVOKEVIRTUAL, etc.
 *
 * @see ConstantPoolGen
 * @see LDC
 * @see INVOKEVIRTUAL
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class CPInstruction extends Instruction implements TypedInstruction,
        IndexedInstruction {

    protected int index; // index to constant pool


    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    CPInstruction() {
    }


    


    /**
     * Dump instruction as byte code to stream out.
     * @param out Output stream
     */
    public void dump( DataOutputStream out ) throws IOException {
        out.writeByte(opcode);
        out.writeShort(index);
    }


    /**
     * Long output format:
     *
     * &lt;name of opcode&gt; "["&lt;opcode number&gt;"]" 
     * "("&lt;length of instruction&gt;")" "&lt;"&lt; constant pool index&gt;"&gt;"
     *
     * @param verbose long/short format switch
     * @return mnemonic for instruction
     */
    public String toString( boolean verbose ) {
        return super.toString(verbose) + " " + index;
    }


    /**
     * @return mnemonic for instruction with symbolic references resolved
     */
    public String toString( ConstantPool cp ) {
        Constant c = cp.getConstant(index);
        String str = cp.constantToString(c);
        if (c instanceof ConstantClass) {
            str = str.replace('.', '/');
        }
        return org.apache.tomcat.util.bcel.Constants.OPCODE_NAMES[opcode] + " " + str;
    }


    /**
     * Read needed data (i.e., index) from file.
     * @param bytes input stream
     * @param wide wide prefix?
     */
    protected void initFromFile( ByteSequence bytes, boolean wide ) throws IOException {
        setIndex(bytes.readUnsignedShort());
        length = 3;
    }


    /**
     * @return index in constant pool referred by this instruction.
     */
    public final int getIndex() {
        return index;
    }


    /**
     * Set the index to constant pool.
     * @param index in  constant pool.
     */
    public void setIndex( int index ) {
        if (index < 0) {
            throw new ClassGenException("Negative index value: " + index);
        }
        this.index = index;
    }


    /** @return type related with this instruction.
     */
    public Type getType( ConstantPoolGen cpg ) {
        ConstantPool cp = cpg.getConstantPool();
        String name = cp.getConstantString(index, org.apache.tomcat.util.bcel.Constants.CONSTANT_Class);
        if (!name.startsWith("[")) {
            name = "L" + name + ";";
        }
        return Type.getType(name);
    }
}
