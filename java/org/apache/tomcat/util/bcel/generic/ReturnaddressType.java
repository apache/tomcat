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

import org.apache.tomcat.util.bcel.Constants;

/** 
 * Returnaddress, the type JSR or JSR_W instructions push upon the stack.
 *
 * see vmspec2 �3.3.3
 * @version $Id$
 * @author Enver Haase
 */
public class ReturnaddressType extends Type {

    public static final ReturnaddressType NO_TARGET = new ReturnaddressType();
    private InstructionHandle returnTarget;


    /**
     * A Returnaddress [that doesn't know where to return to].
     */
    private ReturnaddressType() {
        super(Constants.T_ADDRESS, "<return address>");
    }


    /**
     * Creates a ReturnaddressType object with a target.
     */
    public ReturnaddressType(InstructionHandle returnTarget) {
        super(Constants.T_ADDRESS, "<return address targeting " + returnTarget + ">");
        this.returnTarget = returnTarget;
    }


    /** @return a hash code value for the object.
     */
    public int hashCode() {
        if (returnTarget == null) {
            return 0;
        }
        return returnTarget.hashCode();
    }


    /**
     * Returns if the two Returnaddresses refer to the same target.
     */
    public boolean equals( Object rat ) {
        if (!(rat instanceof ReturnaddressType)) {
            return false;
        }
        ReturnaddressType that = (ReturnaddressType) rat;
        if (this.returnTarget == null || that.returnTarget == null) {
            return that.returnTarget == this.returnTarget;
        }
        return that.returnTarget.equals(this.returnTarget);
    }


    
}
