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
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.tomcat.util.bcel.Constants;

/**
 * This class represents a reference to an unknown (i.e.,
 * application-specific) attribute of a class.  It is instantiated from the
 * <em>Attribute.readAttribute()</em> method.  Applications that need to
 * read in application-specific attributes should create an <a
 * href="./AttributeReader.html">AttributeReader</a> implementation and
 * attach it via <a
 * href="./Attribute.html#addAttributeReader(java.lang.String,
 * org.apache.tomcat.util.bcel.classfile.AttributeReader)">Attribute.addAttributeReader</a>.

 *
 * @version $Id$
 * @see org.apache.tomcat.util.bcel.classfile.Attribute
 * @see org.apache.tomcat.util.bcel.classfile.AttributeReader
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public final class Unknown extends Attribute {

    private static final long serialVersionUID = -4152422704743201314L;
    private byte[] bytes;
    private String name;
    private static final Map<String, Unknown> unknown_attributes =
            new HashMap<String, Unknown>();


    /**
     * Create a non-standard attribute.
     *
     * @param name_index Index in constant pool
     * @param length Content length in bytes
     * @param bytes Attribute contents
     * @param constant_pool Array of constants
     */
    public Unknown(int name_index, int length, byte[] bytes, ConstantPool constant_pool) {
        super(Constants.ATTR_UNKNOWN, name_index, length, constant_pool);
        this.bytes = bytes;
        name = ((ConstantUtf8) constant_pool.getConstant(name_index, Constants.CONSTANT_Utf8))
                .getBytes();
        unknown_attributes.put(name, this);
    }


    /**
     * Construct object from file stream.
     * @param name_index Index in constant pool
     * @param length Content length in bytes
     * @param file Input stream
     * @param constant_pool Array of constants
     * @throws IOException
     */
    Unknown(int name_index, int length, DataInputStream file, ConstantPool constant_pool)
            throws IOException {
        this(name_index, length, (byte[]) null, constant_pool);
        if (length > 0) {
            bytes = new byte[length];
            file.readFully(bytes);
        }
    }


    /**
     * Dump unknown bytes to file stream.
     *
     * @param file Output file stream
     * @throws IOException
     */
    @Override
    public final void dump( DataOutputStream file ) throws IOException {
        super.dump(file);
        if (length > 0) {
            file.write(bytes, 0, length);
        }
    }


    /**
     * @return name of attribute.
     */
    @Override
    public final String getName() {
        return name;
    }


    /**
     * @return String representation.
     */
    @Override
    public final String toString() {
        if (length == 0 || bytes == null) {
            return "(Unknown attribute " + name + ")";
        }
        String hex;
        if (length > 10) {
            byte[] tmp = new byte[10];
            System.arraycopy(bytes, 0, tmp, 0, 10);
            hex = Utility.toHexString(tmp) + "... (truncated)";
        } else {
            hex = Utility.toHexString(bytes);
        }
        return "(Unknown attribute " + name + ": " + hex + ")";
    }


    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy( ConstantPool _constant_pool ) {
        Unknown c = (Unknown) clone();
        if (bytes != null) {
            c.bytes = new byte[bytes.length];
            System.arraycopy(bytes, 0, c.bytes, 0, bytes.length);
        }
        c.constant_pool = _constant_pool;
        return c;
    }
}
