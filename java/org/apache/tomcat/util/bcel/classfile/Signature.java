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

import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Constants;

/**
 * This class is derived from <em>Attribute</em> and represents a reference
 * to a GJ attribute.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Attribute
 */
public final class Signature extends Attribute {

    private static final long serialVersionUID = 7493781777025829964L;
    private int signature_index;


    /**
     * Construct object from file stream.
     * @param name_index Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param file Input stream
     * @param constant_pool Array of constants
     * @throws IOException
     */
    Signature(int name_index, int length, DataInput file, ConstantPool constant_pool)
            throws IOException {
        this(name_index, length, file.readUnsignedShort(), constant_pool);
    }


    /**
     * @param name_index Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param signature_index Index in constant pool to CONSTANT_Utf8
     * @param constant_pool Array of constants
     */
    public Signature(int name_index, int length, int signature_index, ConstantPool constant_pool) {
        super(Constants.ATTR_SIGNATURE, name_index, length, constant_pool);
        this.signature_index = signature_index;
    }


    /**
     * Dump source file attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    @Override
    public final void dump( DataOutputStream file ) throws IOException {
        super.dump(file);
        file.writeShort(signature_index);
    }


    /**
     * @return GJ signature.
     */
    public final String getSignature() {
        ConstantUtf8 c = (ConstantUtf8) constant_pool.getConstant(signature_index,
                Constants.CONSTANT_Utf8);
        return c.getBytes();
    }

    /**
     * @return String representation
     */
    @Override
    public final String toString() {
        String s = getSignature();
        return "Signature(" + s + ")";
    }


    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy( ConstantPool _constant_pool ) {
        return (Signature) clone();
    }
}
