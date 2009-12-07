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
 * Abstract super class for branching instructions like GOTO, IFEQ, etc..
 * Branch instructions may have a variable length, namely GOTO, JSR, 
 * LOOKUPSWITCH and TABLESWITCH.
 *
 * @see InstructionList
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class BranchInstruction extends Instruction implements InstructionTargeter {

    protected int index; // Branch target relative to this instruction
    protected InstructionHandle target; // Target object in instruction list
    protected int position; // Byte code offset


    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    BranchInstruction() {
    }


    


    /**
     * Dump instruction as byte code to stream out.
     * @param out Output stream
     */
    public void dump( DataOutputStream out ) throws IOException {
        out.writeByte(opcode);
        index = getTargetOffset();
        if (Math.abs(index) >= 32767) {
            throw new ClassGenException("Branch target offset too large for short: " + index);
        }
        out.writeShort(index); // May be negative, i.e., point backwards
    }


    /**
     * @param _target branch target
     * @return the offset to  `target' relative to this instruction
     */
    protected int getTargetOffset( InstructionHandle _target ) {
        if (_target == null) {
            throw new ClassGenException("Target of " + super.toString(true)
                    + " is invalid null handle");
        }
        int t = _target.getPosition();
        if (t < 0) {
            throw new ClassGenException("Invalid branch target position offset for "
                    + super.toString(true) + ":" + t + ":" + _target);
        }
        return t - position;
    }


    /**
     * @return the offset to this instruction's target
     */
    protected int getTargetOffset() {
        return getTargetOffset(target);
    }


    /**
     * Long output format:
     *
     * &lt;position in byte code&gt;
     * &lt;name of opcode&gt; "["&lt;opcode number&gt;"]" 
     * "("&lt;length of instruction&gt;")"
     * "&lt;"&lt;target instruction&gt;"&gt;" "@"&lt;branch target offset&gt;
     *
     * @param verbose long/short format switch
     * @return mnemonic for instruction
     */
    public String toString( boolean verbose ) {
        String s = super.toString(verbose);
        String t = "null";
        if (verbose) {
            if (target != null) {
                if (target.getInstruction() == this) {
                    t = "<points to itself>";
                } else if (target.getInstruction() == null) {
                    t = "<null instruction!!!?>";
                } else {
                    t = target.getInstruction().toString(false); // Avoid circles
                }
            }
        } else {
            if (target != null) {
                index = getTargetOffset();
                t = "" + (index + position);
            }
        }
        return s + " -> " + t;
    }


    /**
     * Set branch target
     * @param target branch target
     */
    public void setTarget( InstructionHandle target ) {
        notifyTarget(this.target, target, this);
        this.target = target;
    }


    /**
     * Used by BranchInstruction, LocalVariableGen, CodeExceptionGen
     */
    static final void notifyTarget( InstructionHandle old_ih, InstructionHandle new_ih,
            InstructionTargeter t ) {
        if (old_ih != null) {
            old_ih.removeTargeter(t);
        }
        if (new_ih != null) {
            new_ih.addTargeter(t);
        }
    }


    /**
     * @param old_ih old target
     * @param new_ih new target
     */
    public void updateTarget( InstructionHandle old_ih, InstructionHandle new_ih ) {
        if (target == old_ih) {
            setTarget(new_ih);
        } else {
            throw new ClassGenException("Not targeting " + old_ih + ", but " + target);
        }
    }


    /**
     * @return true, if ih is target of this instruction
     */
    public boolean containsTarget( InstructionHandle ih ) {
        return (target == ih);
    }


    /**
     * Inform target that it's not targeted anymore.
     */
    void dispose() {
        setTarget(null);
        index = -1;
        position = -1;
    }
}
