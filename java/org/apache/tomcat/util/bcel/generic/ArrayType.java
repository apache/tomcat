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
 * Denotes array type, such as int[][]
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public final class ArrayType extends ReferenceType {

    private int dimensions;
    private Type basic_type;


    


    


    /**
     * Constructor for array of given type
     *
     * @param type type of array (may be an array itself)
     */
    public ArrayType(Type type, int dimensions) {
        super(Constants.T_ARRAY, "<dummy>");
        if ((dimensions < 1) || (dimensions > Constants.MAX_BYTE)) {
            throw new ClassGenException("Invalid number of dimensions: " + dimensions);
        }
        switch (type.getType()) {
            case Constants.T_ARRAY:
                ArrayType array = (ArrayType) type;
                this.dimensions = dimensions + array.dimensions;
                basic_type = array.basic_type;
                break;
            case Constants.T_VOID:
                throw new ClassGenException("Invalid type: void[]");
            default: // Basic type or reference
                this.dimensions = dimensions;
                basic_type = type;
                break;
        }
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < this.dimensions; i++) {
            buf.append('[');
        }
        buf.append(basic_type.getSignature());
        signature = buf.toString();
    }


    


    


    


    /** @return a hash code value for the object.
     */
    public int hashCode() {
        return basic_type.hashCode() ^ dimensions;
    }


    /** @return true if both type objects refer to the same array type.
     */
    public boolean equals( Object _type ) {
        if (_type instanceof ArrayType) {
            ArrayType array = (ArrayType) _type;
            return (array.dimensions == dimensions) && array.basic_type.equals(basic_type);
        }
        return false;
    }
}
