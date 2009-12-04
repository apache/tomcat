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

/** 
 * LCONST - Push 0 or 1, other values cause an exception
 *
 * <PRE>Stack: ... -&gt; ..., </PRE>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class LCONST extends Instruction implements ConstantPushInstruction, TypedInstruction {

    private long value;


    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    LCONST() {
    }


    public LCONST(long l) {
        super(org.apache.tomcat.util.bcel.Constants.LCONST_0, (short) 1);
        if (l == 0) {
            opcode = org.apache.tomcat.util.bcel.Constants.LCONST_0;
        } else if (l == 1) {
            opcode = org.apache.tomcat.util.bcel.Constants.LCONST_1;
        } else {
            throw new ClassGenException("LCONST can be used only for 0 and 1: " + l);
        }
        value = l;
    }


    public Number getValue() {
        return new Long(value);
    }


    /** @return Type.LONG
     */
    public Type getType( ConstantPoolGen cp ) {
        return Type.LONG;
    }
}
