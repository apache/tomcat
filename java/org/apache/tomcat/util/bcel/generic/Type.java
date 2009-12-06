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
import org.apache.tomcat.util.bcel.classfile.ClassFormatException;
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
    /** Predefined constants
     */
    public static final BasicType VOID = new BasicType(Constants.T_VOID);
    public static final BasicType BOOLEAN = new BasicType(Constants.T_BOOLEAN);
    public static final BasicType INT = new BasicType(Constants.T_INT);
    public static final BasicType SHORT = new BasicType(Constants.T_SHORT);
    public static final BasicType BYTE = new BasicType(Constants.T_BYTE);
    public static final BasicType LONG = new BasicType(Constants.T_LONG);
    public static final BasicType DOUBLE = new BasicType(Constants.T_DOUBLE);
    public static final BasicType FLOAT = new BasicType(Constants.T_FLOAT);
    public static final BasicType CHAR = new BasicType(Constants.T_CHAR);
    public static final ObjectType OBJECT = new ObjectType("java.lang.Object");
    public static final ObjectType CLASS = new ObjectType("java.lang.Class");
    public static final ObjectType STRING = new ObjectType("java.lang.String");
    
    
    
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
     * @return type as defined in Constants
     */
    public byte getType() {
        return type;
    }


    /**
     * @return stack size of this type (2 for long and double, 0 for void, 1 otherwise)
     */
    public int getSize() {
        switch (type) {
            case Constants.T_DOUBLE:
            case Constants.T_LONG:
                return 2;
            case Constants.T_VOID:
                return 0;
            default:
                return 1;
        }
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

    private static ThreadLocal consumed_chars = new ThreadLocal() {

        protected Object initialValue() {
            return new Integer(0);
        }
    };//int consumed_chars=0; // Remember position in string, see getArgumentTypes


    private static int unwrap( ThreadLocal tl ) {
        return ((Integer) tl.get()).intValue();
    }


    private static void wrap( ThreadLocal tl, int value ) {
        tl.set(new Integer(value));
    }


    /**
     * Convert signature to a Type object.
     * @param signature signature string such as Ljava/lang/String;
     * @return type object
     */
    public static final Type getType( String signature ) throws StringIndexOutOfBoundsException {
        byte type = Utility.typeOfSignature(signature);
        if (type <= Constants.T_VOID) {
            //corrected concurrent private static field acess
            wrap(consumed_chars, 1);
            return BasicType.getType(type);
        } else if (type == Constants.T_ARRAY) {
            int dim = 0;
            do { // Count dimensions
                dim++;
            } while (signature.charAt(dim) == '[');
            // Recurse, but just once, if the signature is ok
            Type t = getType(signature.substring(dim));
            //corrected concurrent private static field acess
            //  consumed_chars += dim; // update counter - is replaced by
            int _temp = unwrap(consumed_chars) + dim;
            wrap(consumed_chars, _temp);
            return new ArrayType(t, dim);
        } else { // type == T_REFERENCE
            int index = signature.indexOf(';'); // Look for closing `;'
            if (index < 0) {
                throw new ClassFormatException("Invalid signature: " + signature);
            }
            //corrected concurrent private static field acess
            wrap(consumed_chars, index + 1); // "Lblabla;" `L' and `;' are removed
            return new ObjectType(signature.substring(1, index).replace('/', '.'));
        }
    }


    /**
     * Convert return value of a method (signature) to a Type object.
     *
     * @param signature signature string such as (Ljava/lang/String;)V
     * @return return type
     */
    public static Type getReturnType( String signature ) {
        try {
            // Read return type after `)'
            int index = signature.lastIndexOf(')') + 1;
            return getType(signature.substring(index));
        } catch (StringIndexOutOfBoundsException e) { // Should never occur
            throw new ClassFormatException("Invalid method signature: " + signature, e);
        }
    }


    


    


    


    
    
    private static int size(int coded) {
    	return coded & 3;
    }
    
    private static int consumed(int coded) {
    	return coded >> 2;
    }
    
    private static int encode(int size, int consumed) {
    	return consumed << 2 | size;
    }
    
    static int getArgumentTypesSize( String signature ) {
        int res = 0;
        int index;
        try { // Read all declarations between for `(' and `)'
            if (signature.charAt(0) != '(') {
                throw new ClassFormatException("Invalid method signature: " + signature);
            }
            index = 1; // current string position
            while (signature.charAt(index) != ')') {
                int coded = getTypeSize(signature.substring(index));
                res += size(coded);
                index += consumed(coded);
            }
        } catch (StringIndexOutOfBoundsException e) { // Should never occur
            throw new ClassFormatException("Invalid method signature: " + signature, e);
        }
        return res;
    }
    
    static final int getTypeSize( String signature ) throws StringIndexOutOfBoundsException {
        byte type = Utility.typeOfSignature(signature);
        if (type <= Constants.T_VOID) {
            return encode(BasicType.getType(type).getSize(), 1);
        } else if (type == Constants.T_ARRAY) {
            int dim = 0;
            do { // Count dimensions
                dim++;
            } while (signature.charAt(dim) == '[');
            // Recurse, but just once, if the signature is ok
            int consumed = consumed(getTypeSize(signature.substring(dim)));
            return encode(1, dim + consumed);
        } else { // type == T_REFERENCE
            int index = signature.indexOf(';'); // Look for closing `;'
            if (index < 0) {
                throw new ClassFormatException("Invalid signature: " + signature);
            }
            return encode(1, index + 1);
        }
    }


	static int getReturnTypeSize(String signature) {
		int index = signature.lastIndexOf(')') + 1;
        return getTypeSize(signature.substring(index));
	}
}
