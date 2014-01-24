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

/**
 * This class represents colection of local variables in a
 * method. This attribute is contained in the <em>Code</em> attribute.
 *
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Code
 */
public class LocalVariableTable extends Attribute {

    private static final long serialVersionUID = -3904314258294133920L;


    /**
     * Construct object from file stream.
     * @param name_index Index in constant pool
     * @param length Content length in bytes
     * @param file Input stream
     * @param constant_pool Array of constants
     * @throws IOException
     */
    LocalVariableTable(int name_index, int length, DataInputStream file, ConstantPool constant_pool)
            throws IOException {
        super(name_index, length, constant_pool);
        int local_variable_table_length = (file.readUnsignedShort());
        for (int i = 0; i < local_variable_table_length; i++) {
            Utility.swallowLocalVariable(file);
        }
    }
}
