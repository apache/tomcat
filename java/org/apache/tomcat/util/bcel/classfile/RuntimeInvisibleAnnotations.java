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
 * represents an annotation that is represented in the class file but is not
 * provided to the JVM.
 *
 * @author <A HREF="mailto:dbrosius@qis.net">D. Brosius</A>
 * @since 6.0
 */
public class RuntimeInvisibleAnnotations extends Annotations
{
    private static final long serialVersionUID = -7962627688723310248L;

    /**
     * @param name_index
     *            Index pointing to the name <em>Code</em>
     * @param length
     *            Content length in bytes
     * @param file
     *            Input stream
     * @param constant_pool
     *            Array of constants
     */
    RuntimeInvisibleAnnotations(int name_index, int length,
            DataInputStream file, ConstantPool constant_pool)
            throws IOException
    {
        super(name_index, length, file, constant_pool);
    }
}
