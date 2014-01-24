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
 * and represents a reference to a String object.
 *
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Constant
 */
public final class ConstantString extends Constant {

    private static final long serialVersionUID = 2809338612858801341L;
    private int string_index; // Identical to ConstantClass except for this name


    /**
     * Initialize instance from file data.
     *
     * @param file Input stream
     * @throws IOException
     */
    ConstantString(DataInput file) throws IOException {
        this(file.readUnsignedShort());
    }


    /**
     * @param string_index Index of Constant_Utf8 in constant pool
     */
    public ConstantString(int string_index) {
        super(Constants.CONSTANT_String);
        this.string_index = string_index;
    }


    /**
     * @return Index in constant pool of the string (ConstantUtf8).
     */
    public final int getStringIndex() {
        return string_index;
    }
}
