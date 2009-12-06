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

import java.util.HashSet;
import java.util.Set;
import org.apache.tomcat.util.bcel.classfile.Utility;

/**
 * Instances of this class give users a handle to the instructions contained in
 * an InstructionList. Instruction objects may be used more than once within a
 * list, this is useful because it saves memory and may be much faster.
 *
 * Within an InstructionList an InstructionHandle object is wrapped
 * around all instructions, i.e., it implements a cell in a
 * doubly-linked list. From the outside only the next and the
 * previous instruction (handle) are accessible. One
 * can traverse the list via an Enumeration returned by
 * InstructionList.elements().
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see Instruction
 * @see BranchHandle
 * @see InstructionList 
 */
public class InstructionHandle implements java.io.Serializable {

    InstructionHandle next; // Will be set from the outside
    Instruction instruction;
    protected int i_position = -1; // byte code offset of instruction
    private Set targeters;


    


    


    public final Instruction getInstruction() {
        return instruction;
    }


    /**
     * Replace current instruction contained in this handle.
     * Old instruction is disposed using Instruction.dispose().
     */
    public void setInstruction( Instruction i ) { // Overridden in BranchHandle
        if (i == null) {
            throw new ClassGenException("Assigning null to handle");
        }
        if ((this.getClass() != BranchHandle.class) && (i instanceof BranchInstruction)) {
            throw new ClassGenException("Assigning branch instruction " + i + " to plain handle");
        }
        if (instruction != null) {
            instruction.dispose();
        }
        instruction = i;
    }


    


    /*private*/protected InstructionHandle(Instruction i) {
        setInstruction(i);
    }

    private static InstructionHandle ih_list = null; // List of reusable handles

    /** @return the position, i.e., the byte code offset of the contained
     * instruction. This is accurate only after
     * InstructionList.setPositions() has been called.
     */
    public int getPosition() {
        return i_position;
    }


    /** Set the position, i.e., the byte code offset of the contained
     * instruction.
     */
    void setPosition( int pos ) {
        i_position = pos;
    }


    /** Overridden in BranchHandle
     */
    protected void addHandle() {
        next = ih_list;
        ih_list = this;
    }


    


    


    /**
     * Denote this handle isn't referenced anymore by t.
     */
    public void removeTargeter( InstructionTargeter t ) {
        if (targeters != null) {
            targeters.remove(t);
        }
    }


    /**
     * Denote this handle is being referenced by t.
     */
    public void addTargeter( InstructionTargeter t ) {
        if (targeters == null) {
            targeters = new HashSet();
        }
        //if(!targeters.contains(t))
        targeters.add(t);
    }


    


    


    /** @return a (verbose) string representation of the contained instruction. 
     */
    public String toString( boolean verbose ) {
        return Utility.format(i_position, 4, false, ' ') + ": " + instruction.toString(verbose);
    }


    /** @return a string representation of the contained instruction. 
     */
    public String toString() {
        return toString(true);
    }


    


    


    


    
}
