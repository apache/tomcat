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
 * base class for parameter annotations
 *
 * @author  <A HREF="mailto:dbrosius@qis.net">D. Brosius</A>
 * @since 6.0
 */
public abstract class ParameterAnnotations extends Attribute {

    private final ParameterAnnotationEntry[] parameter_annotation_table; // Table of parameter annotations


    /**
     * @param file Input stream
     * @param constant_pool Array of constants
     */
    ParameterAnnotations(DataInputStream file, ConstantPool constant_pool)
            throws IOException {
        int num_parameters = file.readUnsignedByte();
        parameter_annotation_table = new ParameterAnnotationEntry[num_parameters];
        for (int i = 0; i < num_parameters; i++) {
            parameter_annotation_table[i] = new ParameterAnnotationEntry(file, constant_pool);
        }
    }

}
