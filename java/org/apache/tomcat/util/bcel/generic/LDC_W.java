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

import java.io.IOException;
import org.apache.tomcat.util.bcel.util.ByteSequence;

/** 
 * LDC_W - Push item from constant pool (wide index)
 *
 * <PRE>Stack: ... -&gt; ..., item.word1, item.word2</PRE>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class LDC_W extends LDC {

    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    LDC_W() {
    }


    public LDC_W(int index) {
        super(index);
    }


    /**
     * Read needed data (i.e., index) from file.
     */
    protected void initFromFile( ByteSequence bytes, boolean wide ) throws IOException {
        setIndex(bytes.readUnsignedShort());
        // Override just in case it has been changed
        opcode = org.apache.tomcat.util.bcel.Constants.LDC_W;
        length = 3;
    }
}
