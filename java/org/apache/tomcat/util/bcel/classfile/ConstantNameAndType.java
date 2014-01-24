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

import org.apache.tomcat.util.bcel.Constants;

/**
 * This class is derived from the abstract
 * <A HREF="org.apache.tomcat.util.bcel.classfile.Constant.html">Constant</A> class
 * and represents a reference to the name and signature
 * of a field or method.
 *
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Constant
 */
public final class ConstantNameAndType extends Constant {

    private static final long serialVersionUID = 1010506730811368756L;
    private int name_index; // Name of field/method
    private int signature_index; // and its signature.


    /**
     * Initialize instance from file data.
     *
     * @param file Input stream
     * @throws IOException
     */
    ConstantNameAndType(DataInput file) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort());
    }


    /**
     * @param name_index Name of field/method
     * @param signature_index and its signature
     */
    public ConstantNameAndType(int name_index, int signature_index) {
        super(Constants.CONSTANT_NameAndType);
        this.name_index = name_index;
        this.signature_index = signature_index;
    }


    /**
     * @return Name index in constant pool of field/method name.
     */
    public final int getNameIndex() {
        return name_index;
    }


    /**
     * @return Index in constant pool of field/method signature.
     */
    public final int getSignatureIndex() {
        return signature_index;
    }
}
