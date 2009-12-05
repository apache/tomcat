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

import org.apache.tomcat.util.bcel.classfile.ConstantPool;

/**
 * Super class for the GET/PUTxxx family of instructions.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class FieldInstruction extends FieldOrMethod implements TypedInstruction {

    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    FieldInstruction() {
    }


    


    /**
     * @return mnemonic for instruction with symbolic references resolved
     */
    public String toString( ConstantPool cp ) {
        return org.apache.tomcat.util.bcel.Constants.OPCODE_NAMES[opcode] + " "
                + cp.constantToString(index, org.apache.tomcat.util.bcel.Constants.CONSTANT_Fieldref);
    }


    /** @return size of field (1 or 2)
     */
    protected int getFieldSize( ConstantPoolGen cpg ) {
    	return Type.getTypeSize(getSignature(cpg));
    }


    /** @return return type of referenced field
     */
    public Type getType( ConstantPoolGen cpg ) {
        return getFieldType(cpg);
    }


    /** @return type of field
     */
    public Type getFieldType( ConstantPoolGen cpg ) {
        return Type.getType(getSignature(cpg));
    }


    
}
