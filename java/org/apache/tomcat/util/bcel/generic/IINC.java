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
import org.apache.tomcat.util.bcel.util.ByteSequence;

/**
 * IINC - Increment local variable by constant
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class IINC extends LocalVariableInstruction {

    private boolean wide;
    private int c;


    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    IINC() {
    }


    /**
     * @param n index of local variable
     * @param c increment factor
     */
    public IINC(int n, int c) {
        super(); // Default behaviour of LocalVariableInstruction causes error
        this.opcode = org.apache.tomcat.util.bcel.Constants.IINC;
        this.length = (short) 3;
        setIndex(n); // May set wide as side effect
        setIncrement(c);
    }


    /**
     * Dump instruction as byte code to stream out.
     * @param out Output stream
     */
    public void dump( DataOutputStream out ) throws IOException {
        if (wide) {
            out.writeByte(org.apache.tomcat.util.bcel.Constants.WIDE);
        }
        out.writeByte(opcode);
        if (wide) {
            out.writeShort(n);
            out.writeShort(c);
        } else {
            out.writeByte(n);
            out.writeByte(c);
        }
    }


    private final void setWide() {
        wide = (n > org.apache.tomcat.util.bcel.Constants.MAX_BYTE) || (Math.abs(c) > Byte.MAX_VALUE);
        if (wide) {
            length = 6; // wide byte included  
        } else {
            length = 3;
        }
    }


    /**
     * Read needed data (e.g. index) from file.
     */
    protected void initFromFile( ByteSequence bytes, boolean wide ) throws IOException {
        this.wide = wide;
        if (wide) {
            length = 6;
            n = bytes.readUnsignedShort();
            c = bytes.readShort();
        } else {
            length = 3;
            n = bytes.readUnsignedByte();
            c = bytes.readByte();
        }
    }


    /**
     * @return mnemonic for instruction
     */
    public String toString( boolean verbose ) {
        return super.toString(verbose) + " " + c;
    }


    /**
     * Set index of local variable.
     */
    public final void setIndex( int n ) {
        if (n < 0) {
            throw new ClassGenException("Negative index value: " + n);
        }
        this.n = n;
        setWide();
    }


    /**
     * @return increment factor
     */
    public final int getIncrement() {
        return c;
    }


    /**
     * Set increment factor.
     */
    public final void setIncrement( int c ) {
        this.c = c;
        setWide();
    }


    /** @return int type
     */
    public Type getType( ConstantPoolGen cp ) {
        return Type.INT;
    }
}
