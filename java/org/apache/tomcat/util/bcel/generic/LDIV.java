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
 * LDIV - Divide longs
 * <PRE>Stack: ..., value1.word1, value1.word2, value2.word1, value2.word2 -&gt;</PRE>
 *        ..., result.word1, result.word2
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class LDIV extends ArithmeticInstruction implements ExceptionThrower {

    public LDIV() {
        super(org.apache.tomcat.util.bcel.Constants.LDIV);
    }


    public Class[] getExceptions() {
        return new Class[] {
            org.apache.tomcat.util.bcel.ExceptionConstants.ARITHMETIC_EXCEPTION
        };
    }
}
