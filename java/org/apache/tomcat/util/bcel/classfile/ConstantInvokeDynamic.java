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
 * and represents a reference to a invoke dynamic.
 *
 * @see     Constant
 */
public final class ConstantInvokeDynamic extends Constant {

    private static final long serialVersionUID = 4310367359017396174L;


    /**
     * Initialize instance from file data.
     *
     * @param file Input stream
     * @throws IOException
     */
    ConstantInvokeDynamic(DataInput file) throws IOException {
        super(Constants.CONSTANT_InvokeDynamic);
        file.readUnsignedShort();   // Unused bootstrap_method_attr_index
        file.readUnsignedShort();   // Unused name_and_type_index
    }
}
