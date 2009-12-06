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

import java.io.Serializable;

/** 
 * This class is a container for a list of <a
 * href="Instruction.html">Instruction</a> objects. Instructions can
 * be appended, inserted, moved, deleted, etc.. Instructions are being
 * wrapped into <a
 * href="InstructionHandle.html">InstructionHandles</a> objects that
 * are returned upon append/insert operations. They give the user
 * (read only) access to the list structure, such that it can be traversed and
 * manipulated in a controlled way.
 *
 * A list is finally dumped to a byte code array with <a
 * href="#getByteCode()">getByteCode</a>.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Instruction
 * @see     InstructionHandle
 * @see BranchHandle
 */
public class InstructionList implements Serializable {

    private InstructionHandle start = null, end = null;

    /**
     * Create (empty) instruction list.
     */
    public InstructionList() {
    }

    
    


    /**
     * Search for given Instruction reference, start at beginning of list.
     *
     * @param i instruction to search for
     * @return instruction found on success, null otherwise
     */
    private InstructionHandle findInstruction1( Instruction i ) {
        for (InstructionHandle ih = start; ih != null; ih = ih.next) {
            if (ih.instruction == i) {
                return ih;
            }
        }
        return null;
    }


    public boolean contains( Instruction i ) {
        return findInstruction1(i) != null;
    }


    


    public String toString() {
        return toString(true);
    }


    /**
     * @param verbose toggle output format
     * @return String containing all instructions in this list.
     */
    public String toString( boolean verbose ) {
        StringBuffer buf = new StringBuffer();
        for (InstructionHandle ih = start; ih != null; ih = ih.next) {
            buf.append(ih.toString(verbose)).append("\n");
        }
        return buf.toString();
    }


    /**
     * @return start of list
     */
    public InstructionHandle getStart() {
        return start;
    }


    /**
     * @return end of list
     */
    public InstructionHandle getEnd() {
        return end;
    }
}
