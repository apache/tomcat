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

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.util.BCELComparator;

/**
 * Represents a Java class, i.e., the data structures, constant pool,
 * fields, methods and commands contained in a Java .class file.
 * See <a href="ftp://java.sun.com/docs/specs/">JVM specification</a> for details.
 * The intent of this class is to represent a parsed or otherwise existing
 * class file.  Those interested in programatically generating classes
 * should see the <a href="../generic/ClassGen.html">ClassGen</a> class.

 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class JavaClass extends AccessFlags
        implements Cloneable, Comparable<JavaClass> {

    private static final long serialVersionUID = 7029227708237523236L;
    private String file_name;
    private String source_file_name = "<Unknown>";
    private String class_name;
    private String superclass_name;
    private int major, minor; // Compiler version
    private ConstantPool constant_pool; // Constant pool
    private int[] interfaces; // implemented interfaces
    private String[] interface_names;
    private Field[] fields; // Fields, i.e., variables of class
    private Method[] methods; // methods defined in the class
    private Attribute[] attributes; // attributes defined in the class
    private AnnotationEntry[] annotations;   // annotations defined on the class


    //  Annotations are collected from certain attributes, don't do it more than necessary!
    private boolean annotationsOutOfDate = true;

    private static BCELComparator _cmp = new BCELComparator() {

        @Override
        public boolean equals( Object o1, Object o2 ) {
            JavaClass THIS = (JavaClass) o1;
            JavaClass THAT = (JavaClass) o2;
            return THIS.getClassName().equals(THAT.getClassName());
        }


        @Override
        public int hashCode( Object o ) {
            JavaClass THIS = (JavaClass) o;
            return THIS.getClassName().hashCode();
        }
    };


    /**
     * Constructor gets all contents as arguments.
     *
     * @param class_name_index Index into constant pool referencing a
     * ConstantClass that represents this class.
     * @param superclass_name_index Index into constant pool referencing a
     * ConstantClass that represents this class's superclass.
     * @param file_name File name
     * @param major Major compiler version
     * @param minor Minor compiler version
     * @param access_flags Access rights defined by bit flags
     * @param constant_pool Array of constants
     * @param interfaces Implemented interfaces
     * @param fields Class fields
     * @param methods Class methods
     * @param attributes Class attributes
     */
    public JavaClass(int class_name_index, int superclass_name_index, String file_name, int major,
            int minor, int access_flags, ConstantPool constant_pool, int[] interfaces,
            Field[] fields, Method[] methods, Attribute[] attributes) {
        if (interfaces == null) {
            interfaces = new int[0];
        }
        if (attributes == null) {
            attributes = new Attribute[0];
        }
        if (fields == null) {
            fields = new Field[0];
        }
        if (methods == null) {
            methods = new Method[0];
        }
        this.file_name = file_name;
        this.major = major;
        this.minor = minor;
        this.access_flags = access_flags;
        this.constant_pool = constant_pool;
        this.interfaces = interfaces;
        this.fields = fields;
        this.methods = methods;
        this.attributes = attributes;
        annotationsOutOfDate = true;
        // Get source file name if available
        for (int i = 0; i < attributes.length; i++) {
            if (attributes[i] instanceof SourceFile) {
                source_file_name = ((SourceFile) attributes[i]).getSourceFileName();
                break;
            }
        }
        /* According to the specification the following entries must be of type
         * `ConstantClass' but we check that anyway via the
         * `ConstPool.getConstant' method.
         */
        class_name = constant_pool.getConstantString(class_name_index, Constants.CONSTANT_Class);
        class_name = Utility.compactClassName(class_name, false);
        if (superclass_name_index > 0) {
            // May be zero -> class is java.lang.Object
            superclass_name = constant_pool.getConstantString(superclass_name_index,
                    Constants.CONSTANT_Class);
            superclass_name = Utility.compactClassName(superclass_name, false);
        } else {
            superclass_name = "java.lang.Object";
        }
        interface_names = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            String str = constant_pool.getConstantString(interfaces[i], Constants.CONSTANT_Class);
            interface_names[i] = Utility.compactClassName(str, false);
        }
    }


    /**
     * @return Attributes of the class.
     */
    public Attribute[] getAttributes() {
        return attributes;
    }

    public AnnotationEntry[] getAnnotationEntries() {
        if (annotationsOutOfDate) {
            // Find attributes that contain annotation data
            Attribute[] attrs = getAttributes();
            List<AnnotationEntry> accumulatedAnnotations = new ArrayList<AnnotationEntry>();
            for (int i = 0; i < attrs.length; i++) {
                Attribute attribute = attrs[i];
                if (attribute instanceof Annotations) {
                    Annotations runtimeAnnotations = (Annotations)attribute;
                    for(int j = 0; j < runtimeAnnotations.getAnnotationEntries().length; j++)
                        accumulatedAnnotations.add(runtimeAnnotations.getAnnotationEntries()[j]);
                }
            }
            annotations = accumulatedAnnotations.toArray(new AnnotationEntry[accumulatedAnnotations.size()]);
            annotationsOutOfDate = false;
        }
        return annotations;
    }

    /**
     * @return Class name.
     */
    public String getClassName() {
        return class_name;
    }


    /**
     * @return String representing class contents.
     */
    @Override
    public String toString() {
        String access = Utility.accessToString(access_flags, true);
        access = access.equals("") ? "" : (access + " ");
        StringBuilder buf = new StringBuilder(128);
        buf.append(access).append(Utility.classOrInterface(access_flags)).append(" ").append(
                class_name).append(" extends ").append(
                Utility.compactClassName(superclass_name, false)).append('\n');
        int size = interfaces.length;
        if (size > 0) {
            buf.append("implements\t\t");
            for (int i = 0; i < size; i++) {
                buf.append(interface_names[i]);
                if (i < size - 1) {
                    buf.append(", ");
                }
            }
            buf.append('\n');
        }
        buf.append("filename\t\t").append(file_name).append('\n');
        buf.append("compiled from\t\t").append(source_file_name).append('\n');
        buf.append("compiler version\t").append(major).append(".").append(minor).append('\n');
        buf.append("access flags\t\t").append(access_flags).append('\n');
        buf.append("constant pool\t\t").append(constant_pool.getLength()).append(" entries\n");
        buf.append("ACC_SUPER flag\t\t").append(isSuper()).append("\n");
        if (attributes.length > 0) {
            buf.append("\nAttribute(s):\n");
            for (int i = 0; i < attributes.length; i++) {
                buf.append(indent(attributes[i]));
            }
        }
        AnnotationEntry[] annotations = getAnnotationEntries();
        if (annotations!=null && annotations.length>0) {
            buf.append("\nAnnotation(s):\n");
            for (int i=0; i<annotations.length; i++)
                buf.append(indent(annotations[i]));
        }
        if (fields.length > 0) {
            buf.append("\n").append(fields.length).append(" fields:\n");
            for (int i = 0; i < fields.length; i++) {
                buf.append("\t").append(fields[i]).append('\n');
            }
        }
        if (methods.length > 0) {
            buf.append("\n").append(methods.length).append(" methods:\n");
            for (int i = 0; i < methods.length; i++) {
                buf.append("\t").append(methods[i]).append('\n');
            }
        }
        return buf.toString();
    }


    private static final String indent( Object obj ) {
        StringTokenizer tok = new StringTokenizer(obj.toString(), "\n");
        StringBuilder buf = new StringBuilder();
        while (tok.hasMoreTokens()) {
            buf.append("\t").append(tok.nextToken()).append("\n");
        }
        return buf.toString();
    }


    public final boolean isSuper() {
        return (access_flags & Constants.ACC_SUPER) != 0;
    }

    /**
     * Return value as defined by given BCELComparator strategy.
     * By default two JavaClass objects are said to be equal when
     * their class names are equal.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals( Object obj ) {
        return _cmp.equals(this, obj);
    }


    /**
     * Return the natural ordering of two JavaClasses.
     * This ordering is based on the class name
     */
    @Override
    public int compareTo(JavaClass obj) {
        return getClassName().compareTo(obj.getClassName());
    }


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default return the hashcode of the class name.
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return _cmp.hashCode(this);
    }
}
