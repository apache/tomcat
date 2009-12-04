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
package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInputStream;
import java.io.IOException;
import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.generic.Type;
import org.apache.tomcat.util.bcel.util.BCELComparator;

/**
 * This class represents the field info structure, i.e., the representation 
 * for a variable in the class. See JVM specification for details.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public final class Field extends FieldOrMethod {

    private static BCELComparator _cmp = new BCELComparator() {

        public boolean equals( Object o1, Object o2 ) {
            Field THIS = (Field) o1;
            Field THAT = (Field) o2;
            return THIS.getName().equals(THAT.getName())
                    && THIS.getSignature().equals(THAT.getSignature());
        }


        public int hashCode( Object o ) {
            Field THIS = (Field) o;
            return THIS.getSignature().hashCode() ^ THIS.getName().hashCode();
        }
    };


    /**
     * Initialize from another object. Note that both objects use the same
     * references (shallow copy). Use clone() for a physical copy.
     */
    public Field(Field c) {
        super(c);
    }


    /**
     * Construct object from file stream.
     * @param file Input stream
     */
    Field(DataInputStream file, ConstantPool constant_pool) throws IOException,
            ClassFormatException {
        super(file, constant_pool);
    }


    /**
     * @param access_flags Access rights of field
     * @param name_index Points to field name in constant pool
     * @param signature_index Points to encoded signature
     * @param attributes Collection of attributes
     * @param constant_pool Array of constants
     */
    public Field(int access_flags, int name_index, int signature_index, Attribute[] attributes,
            ConstantPool constant_pool) {
        super(access_flags, name_index, signature_index, attributes, constant_pool);
    }


    /**
     * Called by objects that are traversing the nodes of the tree implicitely
     * defined by the contents of a Java class. I.e., the hierarchy of methods,
     * fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    public void accept( Visitor v ) {
        v.visitField(this);
    }


    /**
     * @return constant value associated with this field (may be null)
     */
    public final ConstantValue getConstantValue() {
        for (int i = 0; i < attributes_count; i++) {
            if (attributes[i].getTag() == Constants.ATTR_CONSTANT_VALUE) {
                return (ConstantValue) attributes[i];
            }
        }
        return null;
    }


    /**
     * Return string representation close to declaration format,
     * `public static final short MAX = 100', e.g..
     *
     * @return String representation of field, including the signature.
     */
    public final String toString() {
        String name, signature, access; // Short cuts to constant pool
        // Get names from constant pool
        access = Utility.accessToString(access_flags);
        access = access.equals("") ? "" : (access + " ");
        signature = Utility.signatureToString(getSignature());
        name = getName();
        StringBuffer buf = new StringBuffer(64);
        buf.append(access).append(signature).append(" ").append(name);
        ConstantValue cv = getConstantValue();
        if (cv != null) {
            buf.append(" = ").append(cv);
        }
        for (int i = 0; i < attributes_count; i++) {
            Attribute a = attributes[i];
            if (!(a instanceof ConstantValue)) {
                buf.append(" [").append(a.toString()).append("]");
            }
        }
        return buf.toString();
    }


    /**
     * @return deep copy of this field
     */
    public final Field copy( ConstantPool _constant_pool ) {
        return (Field) copy_(_constant_pool);
    }


    /**
     * @return type of field
     */
    public Type getType() {
        return Type.getReturnType(getSignature());
    }


    /**
     * @return Comparison strategy object
     */
    public static BCELComparator getComparator() {
        return _cmp;
    }


    /**
     * @param comparator Comparison strategy object
     */
    public static void setComparator( BCELComparator comparator ) {
        _cmp = comparator;
    }


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default two Field objects are said to be equal when
     * their names and signatures are equal.
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals( Object obj ) {
        return _cmp.equals(this, obj);
    }


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default return the hashcode of the field's name XOR signature.
     * 
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return _cmp.hashCode(this);
    }
}
