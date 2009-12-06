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

/** 
 * NEWARRAY -  Create new array of basic type (int, short, ...)
 * <PRE>Stack: ..., count -&gt; ..., arrayref</PRE>
 * type must be one of T_INT, T_SHORT, ...
 * 
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class NEWARRAY extends Instruction implements AllocationInstruction, ExceptionThrower,
        StackProducer {

    private byte type;


    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    NEWARRAY() {
    }


    


    


    /**
     * Dump instruction as byte code to stream out.
     * @param out Output stream
     */
    public void dump( DataOutputStream out ) throws IOException {
        out.writeByte(opcode);
        out.writeByte(type);
    }


    /**
     * @return numeric code for basic element type
     */
    public final byte getTypecode() {
        return type;
    }


    


    /**
     * @return mnemonic for instruction
     */
    public String toString( boolean verbose ) {
        return super.toString(verbose) + " " + org.apache.tomcat.util.bcel.Constants.TYPE_NAMES[type];
    }
}
