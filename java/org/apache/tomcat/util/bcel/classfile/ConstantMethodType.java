/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.apache.tomcat.util.bcel.Constants;

/**
 * This class is derived from the abstract
 * <A HREF="org.apache.bcel.classfile.Constant.html">Constant</A> class
 * and represents a reference to a method type.
 *
 * @see     Constant
 */
public final class ConstantMethodType extends Constant {

    private static final long serialVersionUID = 6750768220616618881L;
    private int descriptor_index;


    /**
     * Initialize from another object.
     */
    public ConstantMethodType(ConstantMethodType c) {
        this(c.getDescriptorIndex());
    }


    /**
     * Initialize instance from file data.
     *
     * @param file Input stream
     * @throws IOException
     */
    ConstantMethodType(DataInput file) throws IOException {
        this(file.readUnsignedShort());
    }


    public ConstantMethodType(int descriptor_index) {
        super(Constants.CONSTANT_MethodType);
        this.descriptor_index = descriptor_index;
    }


    public int getDescriptorIndex() {
        return descriptor_index;
    }


    public void setDescriptorIndex(int descriptor_index) {
        this.descriptor_index = descriptor_index;
    }


    /**
     * @return String representation
     */
    @Override
    public final String toString() {
        return super.toString() + "(descriptor_index = " + descriptor_index + ")";
    }
}
