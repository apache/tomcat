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
 * LOOKUPSWITCH - Switch with unordered set of values
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see SWITCH
 */
public class LOOKUPSWITCH extends Select {

    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    LOOKUPSWITCH() {
    }


    


    /**
     * Dump instruction as byte code to stream out.
     * @param out Output stream
     */
    public void dump( DataOutputStream out ) throws IOException {
        super.dump(out);
        out.writeInt(match_length); // npairs
        for (int i = 0; i < match_length; i++) {
            out.writeInt(match[i]); // match-offset pairs
            out.writeInt(indices[i] = getTargetOffset(targets[i]));
        }
    }


    /**
     * Read needed data (e.g. index) from file.
     */
    protected void initFromFile( ByteSequence bytes, boolean wide ) throws IOException {
        super.initFromFile(bytes, wide); // reads padding
        match_length = bytes.readInt();
        fixed_length = (short) (9 + match_length * 8);
        length = (short) (fixed_length + padding);
        match = new int[match_length];
        indices = new int[match_length];
        targets = new InstructionHandle[match_length];
        for (int i = 0; i < match_length; i++) {
            match[i] = bytes.readInt();
            indices[i] = bytes.readInt();
        }
    }
}
