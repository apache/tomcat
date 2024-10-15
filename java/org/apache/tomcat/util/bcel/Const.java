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
package org.apache.tomcat.util.bcel;

/**
 * Constants for the project, mostly defined in the JVM specification.
 */
public final class Const {

    /**
     * Java class file format Magic number: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.1-200-A"> The ClassFile Structure
     *      in The Java Virtual Machine Specification</a>
     */
    public static final int JVM_CLASSFILE_MAGIC = 0xCAFEBABE;

    /**
     * One of the access flags for fields, methods, or classes: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.1-200-E.1"> Flag definitions for
     *      Classes in the Java Virtual Machine Specification (Java SE 9 Edition).</a>
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.5"> Flag definitions for Fields
     *      in the Java Virtual Machine Specification (Java SE 9 Edition).</a>
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.6"> Flag definitions for Methods
     *      in the Java Virtual Machine Specification (Java SE 9 Edition).</a>
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.6-300-D.1-D.1"> Flag
     *      definitions for Inner Classes in the Java Virtual Machine Specification (Java SE 9 Edition).</a>
     */
    public static final short ACC_FINAL      = 0x0010;

    /**
     * One of the access flags for classes: {@value}.
     *
     * @see #ACC_FINAL
     */
    public static final short ACC_INTERFACE    = 0x0200;

    /**
     * One of the access flags for methods or classes: {@value}.
     *
     * @see #ACC_FINAL
     */
    public static final short ACC_ABSTRACT     = 0x0400;

    /**
     * One of the access flags for classes: {@value}.
     *
     * @see #ACC_FINAL
     */
    public static final short ACC_ANNOTATION   = 0x2000;

    /**
     * Marks a constant pool entry as type UTF-8: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.7"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_Utf8 = 1;

    /*
     * The description of the constant pool is at: https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4
     * References below are to the individual sections
     */

    /**
     * Marks a constant pool entry as type Integer: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.4"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_Integer = 3;

    /**
     * Marks a constant pool entry as type Float: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.4"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_Float = 4;

    /**
     * Marks a constant pool entry as type Long: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.5"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_Long = 5;

    /**
     * Marks a constant pool entry as type Double: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.5"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_Double = 6;

    /**
     * Marks a constant pool entry as a Class: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.1"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_Class = 7;

    /**
     * Marks a constant pool entry as a Field Reference: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.2"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_Fieldref = 9;

    /**
     * Marks a constant pool entry as type String: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.3"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_String = 8;

    /**
     * Marks a constant pool entry as a Method Reference: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.2"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_Methodref = 10;

    /**
     * Marks a constant pool entry as an Interface Method Reference: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.2"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_InterfaceMethodref = 11;

    /**
     * Marks a constant pool entry as a name and type: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.6"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_NameAndType = 12;

    /**
     * Marks a constant pool entry as a Method Handle: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.8"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_MethodHandle = 15;

    /**
     * Marks a constant pool entry as a Method Type: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.9"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_MethodType = 16;

    /**
     * Marks a constant pool entry as dynamically computed: {@value}.
     *
     * @see <a href="https://bugs.openjdk.java.net/secure/attachment/74618/constant-dynamic.html"> Change request for JEP
     *      309</a>
     */
    public static final byte CONSTANT_Dynamic = 17;

    /**
     * Marks a constant pool entry as an Invoke Dynamic: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.10"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_InvokeDynamic = 18;

    /**
     * Marks a constant pool entry as a Module Reference: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4.11"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_Module = 19;

    /**
     * Marks a constant pool entry as a Package Reference: {@value}.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4.12"> The Constant Pool in The
     *      Java Virtual Machine Specification</a>
     */
    public static final byte CONSTANT_Package = 20;

    /**
     * The names of the types of entries in a constant pool. Use getConstantName instead
     */
    private static final String[] CONSTANT_NAMES = {"", "CONSTANT_Utf8", "", "CONSTANT_Integer", "CONSTANT_Float", "CONSTANT_Long", "CONSTANT_Double",
            "CONSTANT_Class", "CONSTANT_String", "CONSTANT_Fieldref", "CONSTANT_Methodref", "CONSTANT_InterfaceMethodref", "CONSTANT_NameAndType", "", "",
            "CONSTANT_MethodHandle", "CONSTANT_MethodType", "CONSTANT_Dynamic", "CONSTANT_InvokeDynamic", "CONSTANT_Module", "CONSTANT_Package"};

    /**
     * The maximum number of dimensions in an array: {@value}. One of the limitations of the Java Virtual Machine.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2-150"> Field Descriptors in
     *      The Java Virtual Machine Specification</a>
     */
    public static final int MAX_ARRAY_DIMENSIONS = 255;

    /**
     * Minor version number of class files for Java 22: {@value}.
     *
     * @see #MAJOR_22
     * @since 6.10.0
     */
    public static final short MINOR_22 = 0;

    /**
     * Minor version number of class files for Java 23: {@value}.
     *
     * @see #MAJOR_23
     * @since 6.10.0
     */
    public static final short MINOR_23 = 0;

    /**
     * Minor version number of class files for Java 24: {@value}.
     *
     * @see #MAJOR_24
     * @since 6.10.0
     */
    public static final short MINOR_24 = 0;

    /**
     * Major version number of class files for Java 22: {@value}.
     *
     * @see #MINOR_22
     * @since 6.10.0
     */
    public static final short MAJOR_22 = 66;

    /**
     * Major version number of class files for Java 23: {@value}.
     *
     * @see #MINOR_23
     * @since 6.10.0
     */
    public static final short MAJOR_23 = 67;

    /**
     * Major version number of class files for Java 24: {@value}.
     *
     * @see #MINOR_24
     * @since 6.10.0
     */
    public static final short MAJOR_24 = 68;

    /**
     * Get the CONSTANT_NAMES entry at the given index.
     *
     * @param index index into {@code CONSTANT_NAMES}.
     * @return the CONSTANT_NAMES entry at the given index
     */
    public static String getConstantName(final int index) {
        return CONSTANT_NAMES[index];
    }
}
