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
import java.io.Serializable;

import org.apache.tomcat.util.bcel.Constants;

/**
 * Abstract superclass for classes to represent the different constant types
 * in the constant pool of a class file. The classes keep closely to
 * the JVM specification.
 *
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class Constant implements Serializable {

    private static final long serialVersionUID = 2827409182154809454L;

    /* In fact this tag is redundant since we can distinguish different
     * `Constant' objects by their type, i.e., via `instanceof'. In some
     * places we will use the tag for switch()es anyway.
     *
     * First, we want match the specification as closely as possible. Second we
     * need the tag as an index to select the corresponding class name from the
     * `CONSTANT_NAMES' array.
     */
    protected byte tag;


    Constant(byte tag) {
        this.tag = tag;
    }


    /**
     * @return Tag of constant, i.e., its type. No setTag() method to avoid
     * confusion.
     */
    public final byte getTag() {
        return tag;
    }


    /**
     * Read one constant from the given file, the type depends on a tag byte.
     *
     * @param file Input stream
     * @return Constant object
     */
    static Constant readConstant( DataInputStream file ) throws IOException,
            ClassFormatException {
        byte b = file.readByte(); // Read tag byte
        switch (b) {
            case Constants.CONSTANT_Class:
                return new ConstantClass(file);
            case Constants.CONSTANT_Fieldref:
            case Constants.CONSTANT_Methodref:
            case Constants.CONSTANT_InterfaceMethodref:
                Utility.swallowConstantCP(file);
                return null;
            case Constants.CONSTANT_String:
                Utility.swallowConstantString(file);
                return null;
            case Constants.CONSTANT_Integer:
                return new ConstantInteger(file);
            case Constants.CONSTANT_Float:
                return new ConstantFloat(file);
            case Constants.CONSTANT_Long:
                return new ConstantLong(file);
            case Constants.CONSTANT_Double:
                return new ConstantDouble(file);
            case Constants.CONSTANT_NameAndType:
                Utility.swallowConstantNameAndType(file);
                return null;
            case Constants.CONSTANT_Utf8:
                return ConstantUtf8.getInstance(file);
            case Constants.CONSTANT_MethodHandle:
                Utility.swallowConstantMethodHandle(file);
                return null;
            case Constants.CONSTANT_MethodType:
                Utility.swallowConstantMethodType(file);
                return null;
            case Constants.CONSTANT_InvokeDynamic:
                Utility.swallowConstantInvokeDynamic(file);
                return null;
            default:
                throw new ClassFormatException("Invalid byte tag in constant pool: " + b);
        }
    }



    @Override
    public String toString() {
        return "[" + tag + "]";
    }
}
