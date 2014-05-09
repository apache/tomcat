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
 * This class represents the constant pool, i.e., a table of constants, of
 * a parsed classfile. It may contain null references, due to the JVM
 * specification that skips an entry after an 8-byte constant (double,
 * long) entry.  Those interested in generating constant pools
 * programatically should see <a href="../generic/ConstantPoolGen.html">
 * ConstantPoolGen</a>.

 * @see     Constant
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class ConstantPool implements Cloneable, Serializable {

    private static final long serialVersionUID = -6765503791185687014L;
    private int constant_pool_count;
    private Constant[] constant_pool;


    /**
     * Read constants from given file stream.
     *
     * @param file Input stream
     * @throws IOException
     * @throws ClassFormatException
     */
    ConstantPool(DataInputStream file) throws IOException, ClassFormatException {
        byte tag;
        constant_pool_count = file.readUnsignedShort();
        constant_pool = new Constant[constant_pool_count];
        /* constant_pool[0] is unused by the compiler and may be used freely
         * by the implementation.
         */
        for (int i = 1; i < constant_pool_count; i++) {
            constant_pool[i] = Constant.readConstant(file);
            /* Quote from the JVM specification:
             * "All eight byte constants take up two spots in the constant pool.
             * If this is the n'th byte in the constant pool, then the next item
             * will be numbered n+2"
             *
             * Thus we have to increment the index counter.
             */
            tag = constant_pool[i].getTag();
            if ((tag == Constants.CONSTANT_Double) || (tag == Constants.CONSTANT_Long)) {
                i++;
            }
        }
    }


    /**
     * Resolve constant to a string representation.
     *
     * @param  c Constant to be printed
     * @return String representation
     */
    public String constantToString( Constant c ) throws ClassFormatException {
        String str;
        int i;
        byte tag = c.getTag();
        switch (tag) {
            case Constants.CONSTANT_Class:
                i = ((ConstantClass) c).getNameIndex();
                c = getConstant(i, Constants.CONSTANT_Utf8);
                str = Utility.compactClassName(((ConstantUtf8) c).getBytes());
                break;
            case Constants.CONSTANT_String:
                i = ((ConstantString) c).getStringIndex();
                c = getConstant(i, Constants.CONSTANT_Utf8);
                str = "\"" + escape(((ConstantUtf8) c).getBytes()) + "\"";
                break;
            case Constants.CONSTANT_Utf8:
                str = ((ConstantUtf8) c).getBytes();
                break;
            case Constants.CONSTANT_Double:
                str = String.valueOf(((ConstantDouble) c).getBytes());
                break;
            case Constants.CONSTANT_Float:
                str = String.valueOf(((ConstantFloat) c).getBytes());
                break;
            case Constants.CONSTANT_Long:
                str = String.valueOf(((ConstantLong) c).getBytes());
                break;
            case Constants.CONSTANT_Integer:
                str = String.valueOf(((ConstantInteger) c).getBytes());
                break;
            case Constants.CONSTANT_NameAndType:
                str = (constantToString(((ConstantNameAndType) c).getNameIndex(),
                        Constants.CONSTANT_Utf8)
                        + " " + constantToString(((ConstantNameAndType) c).getSignatureIndex(),
                        Constants.CONSTANT_Utf8));
                break;
            case Constants.CONSTANT_InterfaceMethodref:
            case Constants.CONSTANT_Methodref:
            case Constants.CONSTANT_Fieldref:
                str = (constantToString(((ConstantCP) c).getClassIndex(), Constants.CONSTANT_Class)
                        + "." + constantToString(((ConstantCP) c).getNameAndTypeIndex(),
                        Constants.CONSTANT_NameAndType));
                break;
            default: // Never reached
                throw new RuntimeException("Unknown constant type " + tag);
        }
        return str;
    }


    private static String escape( String str ) {
        int len = str.length();
        StringBuilder buf = new StringBuilder(len + 5);
        char[] ch = str.toCharArray();
        for (int i = 0; i < len; i++) {
            switch (ch[i]) {
                case '\n':
                    buf.append("\\n");
                    break;
                case '\r':
                    buf.append("\\r");
                    break;
                case '\t':
                    buf.append("\\t");
                    break;
                case '\b':
                    buf.append("\\b");
                    break;
                case '"':
                    buf.append("\\\"");
                    break;
                default:
                    buf.append(ch[i]);
            }
        }
        return buf.toString();
    }


    /**
     * Retrieve constant at `index' from constant pool and resolve it to
     * a string representation.
     *
     * @param  index of constant in constant pool
     * @param  tag expected type
     * @return String representation
     */
    public String constantToString( int index, byte tag ) throws ClassFormatException {
        Constant c = getConstant(index, tag);
        return constantToString(c);
    }


    /**
     * Get constant from constant pool.
     *
     * @param  index Index in constant pool
     * @return Constant value
     * @see    Constant
     */
    public Constant getConstant( int index ) {
        if (index >= constant_pool.length || index < 0) {
            throw new ClassFormatException("Invalid constant pool reference: " + index
                    + ". Constant pool size is: " + constant_pool.length);
        }
        return constant_pool[index];
    }


    /**
     * Get constant from constant pool and check whether it has the
     * expected type.
     *
     * @param  index Index in constant pool
     * @param  tag Tag of expected constant, i.e., its type
     * @return Constant value
     * @see    Constant
     * @throws  ClassFormatException
     */
    public Constant getConstant( int index, byte tag ) throws ClassFormatException {
        Constant c;
        c = getConstant(index);
        if (c == null) {
            throw new ClassFormatException("Constant pool at index " + index + " is null.");
        }
        if (c.getTag() != tag) {
            throw new ClassFormatException("Expected class `" + Constants.CONSTANT_NAMES[tag]
                    + "' at index " + index + " and got " + c);
        }
        return c;
    }


    /**
     * Get string from constant pool and bypass the indirection of
     * `ConstantClass' and `ConstantString' objects. I.e. these classes have
     * an index field that points to another entry of the constant pool of
     * type `ConstantUtf8' which contains the real data.
     *
     * @param  index Index in constant pool
     * @param  tag Tag of expected constant, either ConstantClass or ConstantString
     * @return Contents of string reference
     * @see    ConstantClass
     * @see    ConstantString
     * @throws  ClassFormatException
     */
    public String getConstantString( int index, byte tag ) throws ClassFormatException {
        Constant c;
        int i;
        c = getConstant(index, tag);
        /* This switch() is not that elegant, since the two classes have the
         * same contents, they just differ in the name of the index
         * field variable.
         * But we want to stick to the JVM naming conventions closely though
         * we could have solved these more elegantly by using the same
         * variable name or by subclassing.
         */
        switch (tag) {
            case Constants.CONSTANT_Class:
                i = ((ConstantClass) c).getNameIndex();
                break;
            case Constants.CONSTANT_String:
                i = ((ConstantString) c).getStringIndex();
                break;
            default:
                throw new RuntimeException("getConstantString called with illegal tag " + tag);
        }
        // Finally get the string from the constant pool
        c = getConstant(i, Constants.CONSTANT_Utf8);
        return ((ConstantUtf8) c).getBytes();
    }
}
