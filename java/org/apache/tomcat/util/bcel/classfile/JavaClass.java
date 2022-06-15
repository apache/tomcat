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

import java.util.HashMap;
import java.util.List;

/**
 * Represents a Java class, i.e., the data structures, constant pool,
 * fields, methods and commands contained in a Java .class file.
 * See <a href="https://docs.oracle.com/javase/specs/">JVM specification</a> for details.
 * The intent of this class is to represent a parsed or otherwise existing
 * class file.  Those interested in programmatically generating classes
 * should see the <a href="../generic/ClassGen.html">ClassGen</a> class.
 */
public class JavaClass {

    private final int accessFlags;
    private final String className;
    private final String superclassName;
    private final String[] interfaceNames;
    private final Annotations runtimeVisibleAnnotations; // "RuntimeVisibleAnnotations" attribute defined in the class
    private final List<Annotations> runtimeVisibleFieldOrMethodAnnotations; // "RuntimeVisibleAnnotations" attribute defined elsewhere

    /**
     * Constructor gets all contents as arguments.
     *
     * @param className Name of this class.
     * @param superclassName Name of this class's superclass.
     * @param accessFlags Access rights defined by bit flags
     * @param constant_pool Array of constants
     * @param interfaceNames Implemented interfaces
     * @param runtimeVisibleAnnotations "RuntimeVisibleAnnotations" attribute defined on the Class, or null
     * @param runtimeVisibleFieldOrMethodAnnotations "RuntimeVisibleAnnotations" attribute defined on the fields or methods, or null
     */
    JavaClass(final String className, final String superclassName,
            final int accessFlags, final ConstantPool constant_pool, final String[] interfaceNames,
            final Annotations runtimeVisibleAnnotations, final List<Annotations> runtimeVisibleFieldOrMethodAnnotations) {
        this.accessFlags = accessFlags;
        this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;
        this.runtimeVisibleFieldOrMethodAnnotations = runtimeVisibleFieldOrMethodAnnotations;
        this.className = className;
        this.superclassName = superclassName;
        this.interfaceNames = interfaceNames;
    }

    /**
     * @return Access flags of the object aka. "modifiers".
     */
    public final int getAccessFlags() {
        return accessFlags;
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
     * Return annotations entries from "RuntimeVisibleAnnotations" attribute on
     * the class, fields or methods if there is any.
     *
     * @return An array of entries or {@code null}
     */
    public AnnotationEntry[] getAllAnnotationEntries() {
        HashMap<String, AnnotationEntry> annotationEntries = new HashMap<>();
        if (runtimeVisibleAnnotations != null) {
            for (AnnotationEntry annotationEntry : runtimeVisibleAnnotations.getAnnotationEntries()) {
                annotationEntries.put(annotationEntry.getAnnotationType(), annotationEntry);
            }
        }
        if (runtimeVisibleFieldOrMethodAnnotations != null) {
            for (Annotations annotations : runtimeVisibleFieldOrMethodAnnotations.toArray(new Annotations[0])) {
                for (AnnotationEntry annotationEntry : annotations.getAnnotationEntries()) {
                    annotationEntries.putIfAbsent(annotationEntry.getAnnotationType(), annotationEntry);
                }
            }
        }
        if (annotationEntries.isEmpty()) {
            return null;
        } else {
            return annotationEntries.values().toArray(new AnnotationEntry[0]);
        }
    }

    /**
     * @return Class name.
     */
    public String getClassName() {
        return className;
    }


    /**
     * @return Names of implemented interfaces.
     */
    public String[] getInterfaceNames() {
        return interfaceNames;
    }


    /**
     * returns the super class name of this class. In the case that this class is
     * java.lang.Object, it will return itself (java.lang.Object). This is probably incorrect
     * but isn't fixed at this time to not break existing clients.
     *
     * @return Superclass name.
     */
    public String getSuperclassName() {
        return superclassName;
    }
}
