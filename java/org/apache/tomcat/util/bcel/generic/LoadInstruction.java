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
 * Denotes an unparameterized instruction to load a value from a local
 * variable, e.g. ILOAD.
 *
 * @version $Id$
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class LoadInstruction extends LocalVariableInstruction implements PushInstruction {

    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     * tag and length are defined in readInstruction and initFromFile, respectively.
     */
    LoadInstruction(short canon_tag, short c_tag) {
        super(canon_tag, c_tag);
    }


    /**
     * @param opcode Instruction opcode
     * @param c_tag Instruction number for compact version, ALOAD_0, e.g.
     * @param n local variable index (unsigned short)
     */
    protected LoadInstruction(short opcode, short c_tag, int n) {
        super(opcode, c_tag, n);
    }
}
