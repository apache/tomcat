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

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.util.bcel.Const;

/**
 * Wrapper class that parses a given Java .class file. The method <a href="#parse">parse</a> returns a
 * <a href="JavaClass.html"> JavaClass</a> object on success. When an I/O error or an inconsistency occurs an
 * appropriate exception is propagated back to the caller.
 *
 * The structure and the names comply, except for a few conveniences, exactly with the
 * <a href="https://docs.oracle.com/javase/specs/"> JVM specification 1.0</a>. See this paper for further details about
 * the structure of a bytecode file.
 */
public final class ClassParser {

    private static final int BUFSIZE = 8192;
    private final DataInput dataInputStream;
    private String className;
    private String superclassName;
    private int accessFlags; // Access rights of parsed class
    private String[] interfaceNames; // Names of implemented interfaces
    private ConstantPool constantPool; // collection of constants
    private Annotations runtimeVisibleAnnotations; // "RuntimeVisibleAnnotations" attribute defined in the class
    private List<Annotations> runtimeVisibleFieldOrMethodAnnotations; // "RuntimeVisibleAnnotations" attribute defined elsewhere

    private static final String[] INTERFACES_EMPTY_ARRAY = {};

    /**
     * Parses class from the given stream.
     *
     * @param inputStream Input stream
     */
    public ClassParser(final InputStream inputStream) {
        this.dataInputStream = new DataInputStream(new BufferedInputStream(inputStream, BUFSIZE));
    }


    /**
     * Parses the given Java class file and return an object that represents the contained data, i.e., constants, methods,
     * fields and commands. A <em>ClassFormatException</em> is raised, if the file is not a valid .class file. (This does
     * not include verification of the byte code as it is performed by the Java interpreter).
     *
     * @return Class object representing the parsed class file
     * @throws IOException if an I/O error occurs.
     * @throws ClassFormatException if a class is malformed or cannot be interpreted as a class file
     */
    public JavaClass parse() throws IOException, ClassFormatException {
        //****************** Read headers ********************************
        // Check magic tag of class file
        readID();
        // Get compiler version
        readVersion();
        //***************** Read constant pool and related **************
        // Read constant pool entries
        readConstantPool();
        // Get class information
        readClassInfo();
        // Get interface information, i.e., implemented interfaces
        readInterfaces();
        //***************** Read class fields and methods ***************
        // Read class fields, i.e., the variables of the class
        readFields();
        // Read class methods, i.e., the functions in the class
        readMethods();
        // Read class attributes
        readAttributes(false);

        // Return the information we have gathered in a new object
        return new JavaClass(className, superclassName, accessFlags, constantPool, interfaceNames,
                runtimeVisibleAnnotations, runtimeVisibleFieldOrMethodAnnotations);
    }


    /**
     * Reads information about the attributes of the class.
     * @param fieldOrMethod false if processing a class
     * @throws IOException if an I/O error occurs.
     * @throws ClassFormatException if a class is malformed or cannot be interpreted as a class file
     */
    private void readAttributes(boolean fieldOrMethod) throws IOException, ClassFormatException {
        final int attributesCount = dataInputStream.readUnsignedShort();
        for (int i = 0; i < attributesCount; i++) {
            ConstantUtf8 c;
            String name;
            int name_index;
            int length;
            // Get class name from constant pool via 'name_index' indirection
            name_index = dataInputStream.readUnsignedShort();
            c = (ConstantUtf8) constantPool.getConstant(name_index,
                    Const.CONSTANT_Utf8);
            name = c.getBytes();
            // Length of data in bytes
            length = dataInputStream.readInt();
            if (name.equals("RuntimeVisibleAnnotations")) {
                if (fieldOrMethod) {
                    Annotations fieldOrMethodAnnotations = new Annotations(dataInputStream, constantPool);
                    if (runtimeVisibleFieldOrMethodAnnotations == null) {
                        runtimeVisibleFieldOrMethodAnnotations = new ArrayList<>();
                    }
                    runtimeVisibleFieldOrMethodAnnotations.add(fieldOrMethodAnnotations);
                } else {
                    if (runtimeVisibleAnnotations != null) {
                        throw new ClassFormatException(
                                "RuntimeVisibleAnnotations attribute is not allowed more than once in a class file");
                    }
                    runtimeVisibleAnnotations = new Annotations(dataInputStream, constantPool);
                }
            } else {
                // All other attributes are skipped
                Utility.skipFully(dataInputStream, length);
            }
        }
    }


    /**
     * Reads information about the class and its super class.
     *
     * @throws IOException if an I/O error occurs.
     * @throws ClassFormatException if a class is malformed or cannot be interpreted as a class file
     */
    private void readClassInfo() throws IOException, ClassFormatException {
        accessFlags = dataInputStream.readUnsignedShort();
        /*
         * Interfaces are implicitly abstract, the flag should be set according to the JVM specification.
         */
        if ((accessFlags & Const.ACC_INTERFACE) != 0) {
            accessFlags |= Const.ACC_ABSTRACT;
        }
        if ((accessFlags & Const.ACC_ABSTRACT) != 0 && (accessFlags & Const.ACC_FINAL) != 0) {
            throw new ClassFormatException("Class can't be both final and abstract");
        }

        int classNameIndex = dataInputStream.readUnsignedShort();
        className = Utility.getClassName(constantPool, classNameIndex);

        int superclass_name_index = dataInputStream.readUnsignedShort();
        if (superclass_name_index > 0) {
            // May be zero -> class is java.lang.Object
            superclassName = Utility.getClassName(constantPool, superclass_name_index);
        } else {
            superclassName = "java.lang.Object";
        }
    }


    /**
     * Reads constant pool entries.
     *
     * @throws IOException if an I/O error occurs.
     * @throws ClassFormatException if a class is malformed or cannot be interpreted as a class file
     */
    private void readConstantPool() throws IOException, ClassFormatException {
        constantPool = new ConstantPool(dataInputStream);
    }


    /**
     * Reads information about the fields of the class, i.e., its variables.
     *
     * @throws IOException if an I/O error occurs.
     * @throws ClassFormatException if a class is malformed or cannot be interpreted as a class file
     */
    private void readFields() throws IOException, ClassFormatException {
        final int fieldsCount = dataInputStream.readUnsignedShort();
        for (int i = 0; i < fieldsCount; i++) {
            // file.readUnsignedShort(); // Unused access flags
            // file.readUnsignedShort(); // name index
            // file.readUnsignedShort(); // signature index
            Utility.skipFully(dataInputStream, 6);

            readAttributes(true);
        }
    }


    /**
     * Checks whether the header of the file is ok. Of course, this has to be the first action on successive file reads.
     *
     * @throws IOException if an I/O error occurs.
     * @throws ClassFormatException if a class is malformed or cannot be interpreted as a class file
     */
    private void readID() throws IOException, ClassFormatException {
        if (dataInputStream.readInt() != Const.JVM_CLASSFILE_MAGIC) {
            throw new ClassFormatException("It is not a Java .class file");
        }
    }


    /**
     * Reads information about the interfaces implemented by this class.
     *
     * @throws IOException if an I/O error occurs.
     * @throws ClassFormatException if a class is malformed or cannot be interpreted as a class file
     */
    private void readInterfaces() throws IOException, ClassFormatException {
        final int interfacesCount = dataInputStream.readUnsignedShort();
        if (interfacesCount > 0) {
            interfaceNames = new String[interfacesCount];
            for (int i = 0; i < interfacesCount; i++) {
                int index = dataInputStream.readUnsignedShort();
                interfaceNames[i] = Utility.getClassName(constantPool, index);
            }
        } else {
            interfaceNames = INTERFACES_EMPTY_ARRAY;
        }
    }


    /**
     * Reads information about the methods of the class.
     *
     * @throws IOException if an I/O error occurs.
     * @throws ClassFormatException if a class is malformed or cannot be interpreted as a class file
     */
    private void readMethods() throws IOException, ClassFormatException {
        final int methodsCount = dataInputStream.readUnsignedShort();
        for (int i = 0; i < methodsCount; i++) {
            // file.readUnsignedShort(); // Unused access flags
            // file.readUnsignedShort(); // name index
            // file.readUnsignedShort(); // signature index
            Utility.skipFully(dataInputStream, 6);

            readAttributes(true);
        }
    }


    /**
     * Reads major and minor version of compiler which created the file.
     *
     * @throws IOException if an I/O error occurs.
     * @throws ClassFormatException if a class is malformed or cannot be interpreted as a class file
     */
    private void readVersion() throws IOException, ClassFormatException {
        // file.readUnsignedShort(); // Unused minor
        // file.readUnsignedShort(); // Unused major
        Utility.skipFully(dataInputStream, 4);
    }
}
