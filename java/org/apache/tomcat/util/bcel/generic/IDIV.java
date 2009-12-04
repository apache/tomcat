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
 * IDIV - Divide ints
 * <PRE>Stack: ..., value1, value2 -&gt; result</PRE>
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class IDIV extends ArithmeticInstruction implements ExceptionThrower {

    /** Divide ints
     */
    public IDIV() {
        super(org.apache.tomcat.util.bcel.Constants.IDIV);
    }


    /** @return exceptions this instruction may cause
     */
    public Class[] getExceptions() {
        return new Class[] {
            org.apache.tomcat.util.bcel.ExceptionConstants.ARITHMETIC_EXCEPTION
        };
    }
}
