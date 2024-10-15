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
 * Abstract superclass for classes to represent the different constant types in the constant pool of a class file. The
 * classes keep closely to the JVM specification.
 */
public abstract class Constant {

    /**
     * Reads one constant from the given input, the type depends on a tag byte.
     *
     * @param dataInput Input stream
     * @return Constant object
     * @throws IOException if an I/O error occurs reading from the given {@code dataInput}.
     * @throws ClassFormatException if the next byte is not recognized
     */
    static Constant readConstant(final DataInput dataInput) throws IOException, ClassFormatException {
        final byte b = dataInput.readByte(); // Read tag byte
        int skipSize;
        switch (b) {
        case Const.CONSTANT_Class:
            return new ConstantClass(dataInput);
        case Const.CONSTANT_Integer:
            return new ConstantInteger(dataInput);
        case Const.CONSTANT_Float:
            return new ConstantFloat(dataInput);
        case Const.CONSTANT_Long:
            return new ConstantLong(dataInput);
        case Const.CONSTANT_Double:
            return new ConstantDouble(dataInput);
        case Const.CONSTANT_Utf8:
            return ConstantUtf8.getInstance(dataInput);
        case Const.CONSTANT_String:
        case Const.CONSTANT_MethodType:
        case Const.CONSTANT_Module:
        case Const.CONSTANT_Package:
            skipSize = 2; // unsigned short
            break;
        case Const.CONSTANT_MethodHandle:
            skipSize = 3; // unsigned byte, unsigned short
            break;
        case Const.CONSTANT_Fieldref:
        case Const.CONSTANT_Methodref:
        case Const.CONSTANT_InterfaceMethodref:
        case Const.CONSTANT_NameAndType:
        case Const.CONSTANT_Dynamic:
        case Const.CONSTANT_InvokeDynamic:
            skipSize = 4; // unsigned short, unsigned short
            break;
        default:
            throw new ClassFormatException("Invalid byte tag in constant pool: " + b);
        }
        Utility.skipFully(dataInput, skipSize);
        return null;
    }

    /*
     * In fact this tag is redundant since we can distinguish different 'Constant' objects by their type, i.e., via
     * 'instanceof'. In some places we will use the tag for switch()es anyway.
     *
     * First, we want match the specification as closely as possible. Second we need the tag as an index to select the
     * corresponding class name from the 'CONSTANT_NAMES' array.
     */
    private final byte tag;

    Constant(final byte tag) {
        this.tag = tag;
    }

    /**
     * @return Tag of constant, i.e., its type. No setTag() method to avoid confusion.
     */
    public final byte getTag() {
        return tag;
    }

    @Override
    public String toString() {
        return "[" + tag + "]";
    }
}
