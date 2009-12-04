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

/** 
 * DLOAD - Load double from local variable
 * <PRE>Stack ... -&gt; ..., result.word1, result.word2</PRE>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class DLOAD extends LoadInstruction {

    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    DLOAD() {
        super(org.apache.tomcat.util.bcel.Constants.DLOAD, org.apache.tomcat.util.bcel.Constants.DLOAD_0);
    }


    /** Load double from local variable
     * @param n index of local variable
     */
    public DLOAD(int n) {
        super(org.apache.tomcat.util.bcel.Constants.DLOAD, org.apache.tomcat.util.bcel.Constants.DLOAD_0, n);
    }


    /**
     * Call corresponding visitor method(s). The order is:
     * Call visitor methods of implemented interfaces first, then
     * call methods according to the class hierarchy in descending order,
     * i.e., the most specific visitXXX() call comes last.
     *
     * @param v Visitor object
     */
    public void accept( Visitor v ) {
        super.accept(v);
        v.visitDLOAD(this);
    }
}
