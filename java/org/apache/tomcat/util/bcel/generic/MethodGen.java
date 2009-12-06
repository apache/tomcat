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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.tomcat.util.bcel.classfile.Attribute;
import org.apache.tomcat.util.bcel.classfile.Code;
import org.apache.tomcat.util.bcel.classfile.ExceptionTable;
import org.apache.tomcat.util.bcel.classfile.LocalVariable;
import org.apache.tomcat.util.bcel.classfile.LocalVariableTable;
import org.apache.tomcat.util.bcel.classfile.Method;
import org.apache.tomcat.util.bcel.classfile.Utility;
import org.apache.tomcat.util.bcel.util.BCELComparator;

/** 
 * Template class for building up a method. This is done by defining exception
 * handlers, adding thrown exceptions, local variables and attributes, whereas
 * the `LocalVariableTable' and `LineNumberTable' attributes will be set
 * automatically for the code. Use stripAttributes() if you don't like this.
 *
 * While generating code it may be necessary to insert NOP operations. You can
 * use the `removeNOPs' method to get rid off them.
 * The resulting method object can be obtained via the `getMethod()' method.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @author  <A HREF="http://www.vmeng.com/beard">Patrick C. Beard</A> [setMaxStack()]
 * @see     InstructionList
 * @see     Method
 */
public class MethodGen extends FieldGenOrMethodGen {

    private Type[] arg_types;
    private InstructionList il;
    private List variable_vec = new ArrayList();
    private List throws_vec = new ArrayList();
    
    private static BCELComparator _cmp = new BCELComparator() {

        public boolean equals( Object o1, Object o2 ) {
            MethodGen THIS = (MethodGen) o1;
            MethodGen THAT = (MethodGen) o2;
            return THIS.getName().equals(THAT.getName())
                    && THIS.getSignature().equals(THAT.getSignature());
        }


        public int hashCode( Object o ) {
            MethodGen THIS = (MethodGen) o;
            return THIS.getSignature().hashCode() ^ THIS.getName().hashCode();
        }
    };


    /**
     * Sort local variables by index
     */
    private static final void sort( LocalVariableGen[] vars, int l, int r ) {
        int i = l, j = r;
        int m = vars[(l + r) / 2].getIndex();
        LocalVariableGen h;
        do {
            while (vars[i].getIndex() < m) {
                i++;
            }
            while (m < vars[j].getIndex()) {
                j--;
            }
            if (i <= j) {
                h = vars[i];
                vars[i] = vars[j];
                vars[j] = h; // Swap elements
                i++;
                j--;
            }
        } while (i <= j);
        if (l < j) {
            sort(vars, l, j);
        }
        if (i < r) {
            sort(vars, i, r);
        }
    }


    /*
     * If the range of the variable has not been set yet, it will be set to be valid from
     * the start to the end of the instruction list.
     * 
     * @return array of declared local variables sorted by index
     */
    public LocalVariableGen[] getLocalVariables() {
        int size = variable_vec.size();
        LocalVariableGen[] lg = new LocalVariableGen[size];
        variable_vec.toArray(lg);
        for (int i = 0; i < size; i++) {
            if (lg[i].getStart() == null) {
                lg[i].setStart(il.getStart());
            }
            if (lg[i].getEnd() == null) {
                lg[i].setEnd(il.getEnd());
            }
        }
        if (size > 1) {
            sort(lg, 0, size - 1);
        }
        return lg;
    }


    /**
     * @return `LocalVariableTable' attribute of all the local variables of this method.
     */
    public LocalVariableTable getLocalVariableTable( ConstantPoolGen cp ) {
        LocalVariableGen[] lg = getLocalVariables();
        int size = lg.length;
        LocalVariable[] lv = new LocalVariable[size];
        for (int i = 0; i < size; i++) {
            lv[i] = lg[i].getLocalVariable(cp);
        }
        return new LocalVariableTable(cp.addUtf8("LocalVariableTable"), 2 + lv.length * 10, lv, cp
                .getConstantPool());
    }


    public String getSignature() {
        return Type.getMethodSignature(type, arg_types);
    }


    /**
     * Return string representation close to declaration format,
     * `public static void main(String[]) throws IOException', e.g.
     *
     * @return String representation of the method.
     */
    public final String toString() {
        String access = Utility.accessToString(access_flags);
        String signature = Type.getMethodSignature(type, arg_types);
        signature = Utility.methodSignatureToString(signature, name, access, true,
                getLocalVariableTable(cp));
        StringBuffer buf = new StringBuffer(signature);
        for (int i = 0; i < getAttributes().length; i++) {
            Attribute a = getAttributes()[i];
            if (!((a instanceof Code) || (a instanceof ExceptionTable))) {
                buf.append(" [").append(a.toString()).append("]");
            }
        }
        
        if (throws_vec.size() > 0) {
            for (Iterator e = throws_vec.iterator(); e.hasNext();) {
                buf.append("\n\t\tthrows ").append(e.next());
            }
        }
        return buf.toString();
    }


    
    
    
    
    /**
     * Return value as defined by given BCELComparator strategy.
     * By default two MethodGen objects are said to be equal when
     * their names and signatures are equal.
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals( Object obj ) {
        return _cmp.equals(this, obj);
    }


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default return the hashcode of the method's name XOR signature.
     * 
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return _cmp.hashCode(this);
    }
}
