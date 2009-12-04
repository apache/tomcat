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
 * FCMPL - Compare floats: value1 < value2
 * <PRE>Stack: ..., value1, value2 -&gt; ..., result</PRE>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class FCMPL extends Instruction implements TypedInstruction, StackProducer, StackConsumer {

    public FCMPL() {
        super(org.apache.tomcat.util.bcel.Constants.FCMPL, (short) 1);
    }


    /** @return Type.FLOAT
     */
    public Type getType( ConstantPoolGen cp ) {
        return Type.FLOAT;
    }
}
