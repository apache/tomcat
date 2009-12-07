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

import org.apache.tomcat.util.bcel.classfile.LocalVariable;

/** 
 * This class represents a local variable within a method. It contains its
 * scope, name and type. The generated LocalVariable object can be obtained
 * with getLocalVariable which needs the instruction list and the constant
 * pool as parameters.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     LocalVariable
 */
public class LocalVariableGen implements InstructionTargeter, NamedAndTyped, Cloneable,
        java.io.Serializable {

    private int index;
    private String name;
    private Type type;
    private InstructionHandle start, end;


    


    /**
     * Get LocalVariable object.
     *
     * This relies on that the instruction list has already been dumped to byte code or
     * or that the `setPositions' methods has been called for the instruction list.
     *
     * Note that for local variables whose scope end at the last
     * instruction of the method's code, the JVM specification is ambiguous:
     * both a start_pc+length ending at the last instruction and
     * start_pc+length ending at first index beyond the end of the code are
     * valid.
     *
     * @param cp constant pool
     */
    public LocalVariable getLocalVariable( ConstantPoolGen cp ) {
        int start_pc = start.getPosition();
        int length = end.getPosition() - start_pc;
        if (length > 0) {
            length += end.getInstruction().getLength();
        }
        int name_index = cp.addUtf8(name);
        int signature_index = cp.addUtf8(type.getSignature());
        return new LocalVariable(start_pc, length, name_index, signature_index, index, cp
                .getConstantPool());
    }


    


    public int getIndex() {
        return index;
    }


    


    public String getName() {
        return name;
    }


    


    


    public InstructionHandle getStart() {
        return start;
    }


    public InstructionHandle getEnd() {
        return end;
    }


    public void setStart( InstructionHandle start ) {
        BranchInstruction.notifyTarget(this.start, start, this);
        this.start = start;
    }


    public void setEnd( InstructionHandle end ) {
        BranchInstruction.notifyTarget(this.end, end, this);
        this.end = end;
    }


    


    


    /** @return a hash code value for the object.
     */
    public int hashCode() {
        //If the user changes the name or type, problems with the targeter hashmap will occur
        int hc = index ^ name.hashCode() ^ type.hashCode();
        return hc;
    }


    /**
     * We consider to local variables to be equal, if the use the same index and
     * are valid in the same range.
     */
    public boolean equals( Object o ) {
        if (!(o instanceof LocalVariableGen)) {
            return false;
        }
        LocalVariableGen l = (LocalVariableGen) o;
        return (l.index == index) && (l.start == start) && (l.end == end);
    }


    public String toString() {
        return "LocalVariableGen(" + name + ", " + type + ", " + start + ", " + end + ")";
    }


    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            System.err.println(e);
            return null;
        }
    }
}
