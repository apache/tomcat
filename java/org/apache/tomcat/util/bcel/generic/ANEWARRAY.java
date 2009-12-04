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

import org.apache.tomcat.util.bcel.ExceptionConstants;

/** 
 * ANEWARRAY -  Create new array of references
 * <PRE>Stack: ..., count -&gt; ..., arrayref</PRE>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class ANEWARRAY extends CPInstruction implements LoadClass, AllocationInstruction,
        ExceptionThrower, StackConsumer, StackProducer {

    /**
     * Empty constructor needed for the Class.newInstance() statement in
     * Instruction.readInstruction(). Not to be used otherwise.
     */
    ANEWARRAY() {
    }


    public ANEWARRAY(int index) {
        super(org.apache.tomcat.util.bcel.Constants.ANEWARRAY, index);
    }


    public Class[] getExceptions() {
        Class[] cs = new Class[1 + ExceptionConstants.EXCS_CLASS_AND_INTERFACE_RESOLUTION.length];
        System.arraycopy(ExceptionConstants.EXCS_CLASS_AND_INTERFACE_RESOLUTION, 0, cs, 0,
                ExceptionConstants.EXCS_CLASS_AND_INTERFACE_RESOLUTION.length);
        cs[ExceptionConstants.EXCS_CLASS_AND_INTERFACE_RESOLUTION.length] = ExceptionConstants.NEGATIVE_ARRAY_SIZE_EXCEPTION;
        return cs;
    }


    public ObjectType getLoadClassType( ConstantPoolGen cpg ) {
        Type t = getType(cpg);
        if (t instanceof ArrayType) {
            t = ((ArrayType) t).getBasicType();
        }
        return (t instanceof ObjectType) ? (ObjectType) t : null;
    }
}
