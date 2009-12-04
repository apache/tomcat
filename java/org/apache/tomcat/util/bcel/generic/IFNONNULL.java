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
 * IFNONNULL - Branch if reference is not null
 *
 * <PRE>Stack: ..., reference -&gt; ...</PRE>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class IFNONNULL extends IfInstruction {

    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    IFNONNULL() {
    }


    public IFNONNULL(InstructionHandle target) {
        super(org.apache.tomcat.util.bcel.Constants.IFNONNULL, target);
    }


    /**
     * @return negation of instruction
     */
    public IfInstruction negate() {
        return new IFNULL(target);
    }
}
