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
import java.util.List;
import org.apache.tomcat.util.bcel.Constants;

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
    private int length = 0; // number of elements in list
    private int[] byte_positions; // byte code offsets corresponding to instructions


    /**
     * Create (empty) instruction list.
     */
    public InstructionList() {
    }


    


    


    


    /**
     * Test for empty list.
     */
    public boolean isEmpty() {
        return start == null;
    } // && end == null


    /**
     * Find the target instruction (handle) that corresponds to the given target
     * position (byte code offset).
     *
     * @param ihs array of instruction handles, i.e. il.getInstructionHandles()
     * @param pos array of positions corresponding to ihs, i.e. il.getInstructionPositions()
     * @param count length of arrays
     * @param target target position to search for
     * @return target position's instruction handle if available
     */
    public static InstructionHandle findHandle( InstructionHandle[] ihs, int[] pos, int count,
            int target ) {
        int l = 0, r = count - 1;
        /* Do a binary search since the pos array is orderd.
         */
        do {
            int i = (l + r) / 2;
            int j = pos[i];
            if (j == target) {
                return ihs[i];
            } else if (target < j) {
                r = i - 1;
            } else {
                l = i + 1;
            }
        } while (l <= r);
        return null;
    }


    


    


    /**
     * Append another list after instruction (handle) ih contained in this list.
     * Consumes argument list, i.e., it becomes empty.
     *
     * @param ih where to append the instruction list 
     * @param il Instruction list to append to this one
     * @return instruction handle pointing to the <B>first</B> appended instruction
     */
    public InstructionHandle append( InstructionHandle ih, InstructionList il ) {
        if (il == null) {
            throw new ClassGenException("Appending null InstructionList");
        }
        if (il.isEmpty()) {
            return ih;
        }
        InstructionHandle next = ih.next, ret = il.start;
        ih.next = il.start;
        il.start.prev = ih;
        il.end.next = next;
        if (next != null) {
            next.prev = il.end;
        } else {
            end = il.end; // Update end ...
        }
        length += il.length; // Update length
        il.clear();
        return ret;
    }


    


    


    /**
     * Append an instruction to the end of this list.
     *
     * @param ih instruction to append
     */
    private void append( InstructionHandle ih ) {
        if (isEmpty()) {
            start = end = ih;
            ih.next = ih.prev = null;
        } else {
            end.next = ih;
            ih.prev = end;
            ih.next = null;
            end = ih;
        }
        length++; // Update length
    }


    /**
     * Append an instruction to the end of this list.
     *
     * @param i instruction to append
     * @return instruction handle of the appended instruction
     */
    public InstructionHandle append( Instruction i ) {
        InstructionHandle ih = InstructionHandle.getInstructionHandle(i);
        append(ih);
        return ih;
    }


    /**
     * Append a branch instruction to the end of this list.
     *
     * @param i branch instruction to append
     * @return branch instruction handle of the appended instruction
     */
    public BranchHandle append( BranchInstruction i ) {
        BranchHandle ih = BranchHandle.getBranchHandle(i);
        append(ih);
        return ih;
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


    public void setPositions() {
        setPositions(false);
    }


    /**
     * Give all instructions their position number (offset in byte stream), i.e.,
     * make the list ready to be dumped.
     *
     * @param check Perform sanity checks, e.g. if all targeted instructions really belong
     * to this list
     */
    public void setPositions( boolean check ) {
        int max_additional_bytes = 0, additional_bytes = 0;
        int index = 0, count = 0;
        int[] pos = new int[length];
        /* Pass 0: Sanity checks
         */
        if (check) {
            for (InstructionHandle ih = start; ih != null; ih = ih.next) {
                Instruction i = ih.instruction;
                if (i instanceof BranchInstruction) { // target instruction within list?
                    Instruction inst = ((BranchInstruction) i).getTarget().instruction;
                    if (!contains(inst)) {
                        throw new ClassGenException("Branch target of "
                                + Constants.OPCODE_NAMES[i.opcode] + ":" + inst
                                + " not in instruction list");
                    }
                    if (i instanceof Select) {
                        InstructionHandle[] targets = ((Select) i).getTargets();
                        for (int j = 0; j < targets.length; j++) {
                            inst = targets[j].instruction;
                            if (!contains(inst)) {
                                throw new ClassGenException("Branch target of "
                                        + Constants.OPCODE_NAMES[i.opcode] + ":" + inst
                                        + " not in instruction list");
                            }
                        }
                    }
                    if (!(ih instanceof BranchHandle)) {
                        throw new ClassGenException("Branch instruction "
                                + Constants.OPCODE_NAMES[i.opcode] + ":" + inst
                                + " not contained in BranchHandle.");
                    }
                }
            }
        }
        /* Pass 1: Set position numbers and sum up the maximum number of bytes an
         * instruction may be shifted.
         */
        for (InstructionHandle ih = start; ih != null; ih = ih.next) {
            Instruction i = ih.instruction;
            ih.setPosition(index);
            pos[count++] = index;
            /* Get an estimate about how many additional bytes may be added, because
             * BranchInstructions may have variable length depending on the target
             * offset (short vs. int) or alignment issues (TABLESWITCH and
             * LOOKUPSWITCH).
             */
            switch (i.getOpcode()) {
                case Constants.JSR:
                case Constants.GOTO:
                    max_additional_bytes += 2;
                    break;
                case Constants.TABLESWITCH:
                case Constants.LOOKUPSWITCH:
                    max_additional_bytes += 3;
                    break;
            }
            index += i.getLength();
        }
        /* Pass 2: Expand the variable-length (Branch)Instructions depending on
         * the target offset (short or int) and ensure that branch targets are
         * within this list.
         */
        for (InstructionHandle ih = start; ih != null; ih = ih.next) {
            additional_bytes += ih.updatePosition(additional_bytes, max_additional_bytes);
        }
        /* Pass 3: Update position numbers (which may have changed due to the
         * preceding expansions), like pass 1.
         */
        index = count = 0;
        for (InstructionHandle ih = start; ih != null; ih = ih.next) {
            Instruction i = ih.instruction;
            ih.setPosition(index);
            pos[count++] = index;
            index += i.getLength();
        }
        byte_positions = new int[count]; // Trim to proper size
        System.arraycopy(pos, 0, byte_positions, 0, count);
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


    


    


    


    


    


    private void clear() {
        start = end = null;
        length = 0;
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


    


    


    


    


    

    private List observers;


    


    


    
}
