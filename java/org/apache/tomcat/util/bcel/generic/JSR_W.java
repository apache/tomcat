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
 * JSR_W - Jump to subroutine
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class JSR_W extends JsrInstruction {

    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    JSR_W() {
    }


    public JSR_W(InstructionHandle target) {
        super(org.apache.tomcat.util.bcel.Constants.JSR_W, target);
        length = 5;
    }


    /**
     * Dump instruction as byte code to stream out.
     * @param out Output stream
     */
    public void dump( DataOutputStream out ) throws IOException {
        index = getTargetOffset();
        out.writeByte(opcode);
        out.writeInt(index);
    }


    /**
     * Read needed data (e.g. index) from file.
     */
    protected void initFromFile( ByteSequence bytes, boolean wide ) throws IOException {
        index = bytes.readInt();
        length = 5;
    }
}
