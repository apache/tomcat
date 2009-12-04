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
 * SIPUSH - Push short
 *
 * <PRE>Stack: ... -&gt; ..., value</PRE>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class SIPUSH extends Instruction implements ConstantPushInstruction {

    private short b;


    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    SIPUSH() {
    }


    public SIPUSH(short b) {
        super(org.apache.tomcat.util.bcel.Constants.SIPUSH, (short) 3);
        this.b = b;
    }


    /**
     * Dump instruction as short code to stream out.
     */
    public void dump( DataOutputStream out ) throws IOException {
        super.dump(out);
        out.writeShort(b);
    }


    /**
     * @return mnemonic for instruction
     */
    public String toString( boolean verbose ) {
        return super.toString(verbose) + " " + b;
    }


    /**
     * Read needed data (e.g. index) from file.
     */
    protected void initFromFile( ByteSequence bytes, boolean wide ) throws IOException {
        length = 3;
        b = bytes.readShort();
    }


    public Number getValue() {
        return new Integer(b);
    }


    /** @return Type.SHORT
     */
    public Type getType( ConstantPoolGen cp ) {
        return Type.SHORT;
    }
}
