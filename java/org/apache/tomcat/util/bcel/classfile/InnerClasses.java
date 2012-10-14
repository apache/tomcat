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
 * This class is derived from <em>Attribute</em> and denotes that this class
 * is an Inner class of another.
 * to the source file of this class.
 * It is instantiated from the <em>Attribute.readAttribute()</em> method.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Attribute
 */
public final class InnerClasses extends Attribute {

    private static final long serialVersionUID = 54179484605570305L;
    private InnerClass[] inner_classes;
    private int number_of_classes;


    /**
     * @param name_index Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param inner_classes array of inner classes attributes
     * @param constant_pool Array of constants
     */
    public InnerClasses(int name_index, int length, InnerClass[] inner_classes,
            ConstantPool constant_pool) {
        super(name_index, length, constant_pool);
        setInnerClasses(inner_classes);
    }


    /**
     * Construct object from file stream.
     *
     * @param name_index Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param file Input stream
     * @param constant_pool Array of constants
     * @throws IOException
     */
    InnerClasses(int name_index, int length, DataInputStream file, ConstantPool constant_pool)
            throws IOException {
        this(name_index, length, (InnerClass[]) null, constant_pool);
        number_of_classes = file.readUnsignedShort();
        inner_classes = new InnerClass[number_of_classes];
        for (int i = 0; i < number_of_classes; i++) {
            inner_classes[i] = new InnerClass(file);
        }
    }


    /**
     * @param inner_classes the array of inner classes
     */
    public final void setInnerClasses( InnerClass[] inner_classes ) {
        this.inner_classes = inner_classes;
        number_of_classes = (inner_classes == null) ? 0 : inner_classes.length;
    }
}
