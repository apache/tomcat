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

import org.apache.tomcat.util.bcel.Const;

/**
 * This class represents the constant pool, i.e., a table of constants, of a parsed classfile. It may contain null references, due to the JVM specification that
 * skips an entry after an 8-byte constant (double, long) entry. Those interested in generating constant pools programmatically should see
 * <a href="../generic/ConstantPoolGen.html"> ConstantPoolGen</a>.
 *
 * @see Constant
 */
public class ConstantPool {

    private final Constant[] constantPool;

    /**
     * Reads constants from given input stream.
     *
     * @param input Input stream
     * @throws IOException if an I/O error occurs reading the the InputStream
     * @throws  ClassFormatException If the .class file is not valid
     */
    ConstantPool(final DataInput input) throws IOException, ClassFormatException {
        final int constantPoolCount = input.readUnsignedShort();
        constantPool = new Constant[constantPoolCount];
        /*
         * constantPool[0] is unused by the compiler and may be used freely by the implementation.
         * constantPool[0] is currently unused by the implementation.
         */
        for (int i = 1; i < constantPoolCount; i++) {
            constantPool[i] = Constant.readConstant(input);
            /*
             * Quote from the JVM specification: "All eight byte constants take up two spots in the constant pool. If this is the n'th byte in the constant
             * pool, then the next item will be numbered n+2"
             *
             * Thus we have to increment the index counter.
             */
            if (constantPool[i] != null) {
                byte tag = constantPool[i].getTag();
                if (tag == Const.CONSTANT_Double || tag == Const.CONSTANT_Long) {
                    i++;
                }
            }
        }
    }

    /**
     * Gets constant from constant pool.
     *
     * @param <T> A {@link Constant} subclass
     * @param index Index in constant pool
     * @return Constant value
     * @see Constant
     * @throws ClassFormatException if index is invalid
     */
    @SuppressWarnings("unchecked")
    public <T extends Constant> T getConstant(final int index) throws ClassFormatException {
        return (T) getConstant(index, Constant.class);
    }

    /**
     * Gets constant from constant pool and check whether it has the expected type.
     *
     * @param <T> A {@link Constant} subclass
     * @param index Index in constant pool
     * @param tag   Tag of expected constant, i.e., its type
     * @return Constant value
     * @see Constant
     * @throws ClassFormatException if constant type does not match tag
     */
    public <T extends Constant> T getConstant(final int index, final byte tag) throws ClassFormatException {
        final T c = getConstant(index);
        if (c.getTag() != tag) {
            throw new ClassFormatException("Expected class '" + Const.getConstantName(tag) + "' at index " + index + " and got " + c);
        }
        return c;
    }

    /**
     * Gets constant from constant pool.
     *
     * @param <T> A {@link Constant} subclass
     * @param index Index in constant pool
     * @param castTo The {@link Constant} subclass to cast to.
     * @return Constant value
     * @see Constant
     * @throws ClassFormatException if index is invalid
     */
    public <T extends Constant> T getConstant(final int index, final Class<T> castTo) throws ClassFormatException {
        if (index >= constantPool.length || index < 1) {
            throw new ClassFormatException("Invalid constant pool reference using index: " + index + ". Constant pool size is: " + constantPool.length);
        }
        if (constantPool[index] != null && !castTo.isAssignableFrom(constantPool[index].getClass())) {
            throw new ClassFormatException("Invalid constant pool reference at index: " + index +
                    ". Expected " + castTo + " but was " + constantPool[index].getClass());
        }
        if (index > 1) {
            final Constant prev = constantPool[index - 1];
            if (prev != null && (prev.getTag() == Const.CONSTANT_Double || prev.getTag() == Const.CONSTANT_Long)) {
                throw new ClassFormatException("Constant pool at index " + index + " is invalid. The index is unused due to the preceeding "
                        + Const.getConstantName(prev.getTag()) + ".");
            }
        }
        // Previous check ensures this won't throw a ClassCastException
        final T c = castTo.cast(constantPool[index]);
        if (c == null) {
            throw new ClassFormatException("Constant pool at index " + index + " is null.");
        }
        return c;
    }

    /**
     * Gets constant from constant pool and check whether it has the expected type.
     *
     * @param index Index in constant pool
     * @return ConstantInteger value
     * @see ConstantInteger
     * @throws ClassFormatException if constant type does not match tag
     */
    public ConstantInteger getConstantInteger(final int index) {
        return getConstant(index, Const.CONSTANT_Integer);
    }

    /**
     * Gets constant from constant pool and check whether it has the expected type.
     *
     * @param index Index in constant pool
     * @return ConstantUtf8 value
     * @see ConstantUtf8
     * @throws ClassFormatException if constant type does not match tag
     */
    public ConstantUtf8 getConstantUtf8(final int index) throws ClassFormatException {
        return getConstant(index, Const.CONSTANT_Utf8);
    }
}
