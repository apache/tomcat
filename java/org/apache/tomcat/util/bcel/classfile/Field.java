/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
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
import org.apache.tomcat.util.bcel.util.BCELComparator;

/**
 * This class represents the field info structure, i.e., the representation 
 * for a variable in the class. See JVM specification for details.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public final class Field extends FieldOrMethod {

    private static final long serialVersionUID = 2646214544240375238L;
    private static BCELComparator _cmp = new BCELComparator() {

        @Override
        public boolean equals( Object o1, Object o2 ) {
            Field THIS = (Field) o1;
            Field THAT = (Field) o2;
            return THIS.getName().equals(THAT.getName())
                    && THIS.getSignature().equals(THAT.getSignature());
        }


        @Override
        public int hashCode( Object o ) {
            Field THIS = (Field) o;
            return THIS.getSignature().hashCode() ^ THIS.getName().hashCode();
        }
    };


    /**
     * Construct object from file stream.
     * @param file Input stream
     */
    Field(DataInputStream file, ConstantPool constant_pool) throws IOException,
            ClassFormatException {
        super(file, constant_pool);
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
    @Override
    public final String toString() {
        String name, signature, access; // Short cuts to constant pool
        // Get names from constant pool
        access = Utility.accessToString(access_flags);
        access = access.equals("") ? "" : (access + " ");
        signature = Utility.signatureToString(getSignature());
        name = getName();
        StringBuilder buf = new StringBuilder(64);
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
     * Return value as defined by given BCELComparator strategy.
     * By default two Field objects are said to be equal when
     * their names and signatures are equal.
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals( Object obj ) {
        return _cmp.equals(this, obj);
    }


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default return the hashcode of the field's name XOR signature.
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return _cmp.hashCode(this);
    }
}
