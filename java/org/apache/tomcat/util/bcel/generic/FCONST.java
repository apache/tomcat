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
 * FCONST - Push 0.0, 1.0 or 2.0, other values cause an exception
 *
 * <PRE>Stack: ... -&gt; ..., </PRE>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class FCONST extends Instruction implements ConstantPushInstruction, TypedInstruction {

    private float value;


    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    FCONST() {
    }


    public FCONST(float f) {
        super(org.apache.tomcat.util.bcel.Constants.FCONST_0, (short) 1);
        if (f == 0.0) {
            opcode = org.apache.tomcat.util.bcel.Constants.FCONST_0;
        } else if (f == 1.0) {
            opcode = org.apache.tomcat.util.bcel.Constants.FCONST_1;
        } else if (f == 2.0) {
            opcode = org.apache.tomcat.util.bcel.Constants.FCONST_2;
        } else {
            throw new ClassGenException("FCONST can be used only for 0.0, 1.0 and 2.0: " + f);
        }
        value = f;
    }


    public Number getValue() {
        return new Float(value);
    }


    /** @return Type.FLOAT
     */
    public Type getType( ConstantPoolGen cp ) {
        return Type.FLOAT;
    }
}
