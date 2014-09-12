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

import org.apache.tomcat.util.bcel.Constants;

/**
 * Represents a Java class, i.e., the data structures, constant pool,
 * fields, methods and commands contained in a Java .class file.
 * See <a href="ftp://java.sun.com/docs/specs/">JVM specification</a> for details.
 * The intent of this class is to represent a parsed or otherwise existing
 * class file.  Those interested in programatically generating classes
 * should see the <a href="../generic/ClassGen.html">ClassGen</a> class.

 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class JavaClass extends AccessFlags {

    private String class_name;
    private String superclass_name;
    private String[] interface_names;
    private Annotations runtimeVisibleAnnotations; // "RuntimeVisibleAnnotations" attribute defined in the class


    /**
     * Constructor gets all contents as arguments.
     *
     * @param class_name_index Index into constant pool referencing a
     * ConstantClass that represents this class.
     * @param superclass_name_index Index into constant pool referencing a
     * ConstantClass that represents this class's superclass.
     * @param access_flags Access rights defined by bit flags
     * @param constant_pool Array of constants
     * @param interfaces Implemented interfaces
     * @param runtimeVisibleAnnotations "RuntimeVisibleAnnotations" attribute defined on the Class, or null
     */
    JavaClass(int class_name_index, int superclass_name_index,
            int access_flags, ConstantPool constant_pool, int[] interfaces,
            Annotations runtimeVisibleAnnotations) {
        if (interfaces == null) {
            interfaces = new int[0];
        }
        this.access_flags = access_flags;
        this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;

        /* According to the specification the following entries must be of type
         * `ConstantClass' but we check that anyway via the
         * `ConstPool.getConstant' method.
         */
        class_name = constant_pool.getConstantString(class_name_index, Constants.CONSTANT_Class);
        class_name = Utility.compactClassName(class_name);
        if (superclass_name_index > 0) {
            // May be zero -> class is java.lang.Object
            superclass_name = constant_pool.getConstantString(superclass_name_index,
                    Constants.CONSTANT_Class);
            superclass_name = Utility.compactClassName(superclass_name);
        } else {
            superclass_name = "java.lang.Object";
        }
        interface_names = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            String str = constant_pool.getConstantString(interfaces[i], Constants.CONSTANT_Class);
            interface_names[i] = Utility.compactClassName(str);
        }
    }

    /**
     * Return annotations entries from "RuntimeVisibleAnnotations" attribute on
     * the class, if there is any.
     *
     * @return An array of entries or {@code null}
     */
    public AnnotationEntry[] getAnnotationEntries() {
        if (runtimeVisibleAnnotations != null) {
            return runtimeVisibleAnnotations.getAnnotationEntries();
        }
        return null;
    }

    /**
     * @return Class name.
     */
    public String getClassName() {
        return class_name;
    }


    /**
     * @return Names of implemented interfaces.
     */
    public String[] getInterfaceNames() {
        return interface_names;
    }


    /**
     * returns the super class name of this class. In the case that this class is
     * java.lang.Object, it will return itself (java.lang.Object). This is probably incorrect
     * but isn't fixed at this time to not break existing clients.
     *
     * @return Superclass name.
     */
    public String getSuperclassName() {
        return superclass_name;
    }
}
