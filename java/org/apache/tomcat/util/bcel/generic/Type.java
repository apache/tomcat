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
import org.apache.tomcat.util.bcel.classfile.Utility;

/** 
 * Abstract super class for all possible java types, namely basic types
 * such as int, object types like String and array types, e.g. int[]
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class Type implements java.io.Serializable {

    protected byte type;
    protected String signature; // signature for the type
    
    
    
    
    
    
    
    
    
    
    
    
    public static final ReferenceType NULL = new ReferenceType() {
    };
    


    protected Type(byte t, String s) {
        type = t;
        signature = s;
    }


    /**
     * @return hashcode of Type
     */
    public int hashCode() {
    	return type ^ signature.hashCode();
    }
    
    
    /**
     * @return whether the Types are equal
     */
    public boolean equals(Object o) {
  		if (o instanceof Type) {
  			Type t = (Type)o;
  			return (type == t.type) && signature.equals(t.signature);
  		}
  		return false;
    }
    
    
    /**
     * @return signature for given type.
     */
    public String getSignature() {
        return signature;
    }


    


    


    /**
     * @return Type string, e.g. `int[]'
     */
    public String toString() {
        return ((this.equals(Type.NULL) || (type >= Constants.T_UNKNOWN))) ? signature : Utility
                .signatureToString(signature, false);
    }


    /**
     * Convert type to Java method signature, e.g. int[] f(java.lang.String x)
     * becomes (Ljava/lang/String;)[I
     *
     * @param return_type what the method returns
     * @param arg_types what are the argument types
     * @return method signature for given type(s).
     */
    public static String getMethodSignature( Type return_type, Type[] arg_types ) {
        StringBuffer buf = new StringBuffer("(");
        int length = (arg_types == null) ? 0 : arg_types.length;
        for (int i = 0; i < length; i++) {
            buf.append(arg_types[i].getSignature());
        }
        buf.append(')');
        buf.append(return_type.getSignature());
        return buf.toString();
    }

}
