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
 * JSR - Jump to subroutine
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class JSR extends JsrInstruction implements VariableLengthInstruction {

    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    JSR() {
    }


    public JSR(InstructionHandle target) {
        super(org.apache.tomcat.util.bcel.Constants.JSR, target);
    }


    /**
     * Dump instruction as byte code to stream out.
     * @param out Output stream
     */
    public void dump( DataOutputStream out ) throws IOException {
        index = getTargetOffset();
        if (opcode == org.apache.tomcat.util.bcel.Constants.JSR) {
            super.dump(out);
        } else { // JSR_W
            index = getTargetOffset();
            out.writeByte(opcode);
            out.writeInt(index);
        }
    }


    protected int updatePosition( int offset, int max_offset ) {
        int i = getTargetOffset(); // Depending on old position value
        position += offset; // Position may be shifted by preceding expansions
        if (Math.abs(i) >= (32767 - max_offset)) { // to large for short (estimate)
            opcode = org.apache.tomcat.util.bcel.Constants.JSR_W;
            length = 5;
            return 2; // 5 - 3
        }
        return 0;
    }
}
