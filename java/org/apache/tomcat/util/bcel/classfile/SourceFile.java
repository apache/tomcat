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
 * to the source file of this class.  At most one SourceFile attribute
 * should appear per classfile.  The intention of this class is that it is
 * instantiated from the <em>Attribute.readAttribute()</em> method.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Attribute
 */
public final class SourceFile extends Attribute {

    private static final long serialVersionUID = 332346699609443704L;
    private int sourcefile_index;


    /**
     * Construct object from file stream.
     * @param name_index Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param file Input stream
     * @param constant_pool Array of constants
     * @throws IOException
     */
    SourceFile(int name_index, int length, DataInput file, ConstantPool constant_pool)
            throws IOException {
        this(name_index, length, file.readUnsignedShort(), constant_pool);
    }


    /**
     * @param name_index Index in constant pool to CONSTANT_Utf8, which
     * should represent the string "SourceFile".
     * @param length Content length in bytes, the value should be 2.
     * @param constant_pool The constant pool that this attribute is
     * associated with.
     * @param sourcefile_index Index in constant pool to CONSTANT_Utf8.  This
     * string will be interpreted as the name of the file from which this
     * class was compiled.  It will not be interpreted as indicating the name
     * of the directory contqining the file or an absolute path; this
     * information has to be supplied the consumer of this attribute - in
     * many cases, the JVM.
     */
    public SourceFile(int name_index, int length, int sourcefile_index, ConstantPool constant_pool) {
        super(Constants.ATTR_SOURCE_FILE, name_index, length, constant_pool);
        this.sourcefile_index = sourcefile_index;
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
        file.writeShort(sourcefile_index);
    }


    /**
     * @return Source file name.
     */
    public final String getSourceFileName() {
        ConstantUtf8 c = (ConstantUtf8) constant_pool.getConstant(sourcefile_index,
                Constants.CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return String representation
     */
    @Override
    public final String toString() {
        return "SourceFile(" + getSourceFileName() + ")";
    }


    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy( ConstantPool _constant_pool ) {
        return (SourceFile) clone();
    }
}
