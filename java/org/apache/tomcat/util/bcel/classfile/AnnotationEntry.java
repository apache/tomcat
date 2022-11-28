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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents one annotation in the annotation table
 */
public class AnnotationEntry {

    static final AnnotationEntry[] EMPTY_ARRAY = {};

    private final int typeIndex;

    private final ConstantPool constantPool;

    private final List<ElementValuePair> elementValuePairs;

    /*
     * Creates an AnnotationEntry from a DataInputStream
     *
     * @param input
     * @param constantPool
     * @throws IOException
     */
    AnnotationEntry(final DataInput input, final ConstantPool constantPool) throws IOException {

        this.constantPool = constantPool;

        typeIndex = input.readUnsignedShort();
        final int numElementValuePairs = input.readUnsignedShort();

        elementValuePairs = new ArrayList<>(numElementValuePairs);
        for (int i = 0; i < numElementValuePairs; i++) {
            elementValuePairs.add(new ElementValuePair(input, constantPool));
        }
    }

    /**
     * @return the annotation type name
     */
    public String getAnnotationType() {
        return constantPool.getConstantUtf8(typeIndex).getBytes();
    }

    /**
     * @return the element value pairs in this annotation entry
     */
    public List<ElementValuePair> getElementValuePairs() {
        return elementValuePairs;
    }
}
