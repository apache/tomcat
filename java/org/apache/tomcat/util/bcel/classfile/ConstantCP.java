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
import java.io.IOException;

/**
 * Abstract super class for Fieldref and Methodref constants.
 *
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     ConstantFieldref
 * @see     ConstantMethodref
 * @see     ConstantInterfaceMethodref
 */
public abstract class ConstantCP extends Constant {

    private static final long serialVersionUID = 7282382456501145526L;
    /** References to the constants containing the class and the field signature
     */
    protected int class_index, name_and_type_index;


    /**
     * Initialize instance from file data.
     *
     * @param tag  Constant type tag
     * @param file Input stream
     * @throws IOException
     */
    ConstantCP(byte tag, DataInput file) throws IOException {
        this(tag, file.readUnsignedShort(), file.readUnsignedShort());
    }


    /**
     * @param class_index Reference to the class containing the field
     * @param name_and_type_index and the field signature
     */
    protected ConstantCP(byte tag, int class_index, int name_and_type_index) {
        super(tag);
        this.class_index = class_index;
        this.name_and_type_index = name_and_type_index;
    }


    /**
     * @return Reference (index) to class this field or method belongs to.
     */
    public final int getClassIndex() {
        return class_index;
    }


    /**
     * @return Reference (index) to signature of the field.
     */
    public final int getNameAndTypeIndex() {
        return name_and_type_index;
    }
}
