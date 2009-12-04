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
 * ARRAYLENGTH -  Get length of array
 * <PRE>Stack: ..., arrayref -&gt; ..., length</PRE>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class ARRAYLENGTH extends Instruction implements ExceptionThrower, StackProducer, StackConsumer {

    /** Get length of array
     */
    public ARRAYLENGTH() {
        super(org.apache.tomcat.util.bcel.Constants.ARRAYLENGTH, (short) 1);
    }


    /** @return exceptions this instruction may cause
     */
    public Class[] getExceptions() {
        return new Class[] {
            org.apache.tomcat.util.bcel.ExceptionConstants.NULL_POINTER_EXCEPTION
        };
    }
}
