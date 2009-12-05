/*
 * Copyright  2000-2009 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.util.BCELComparator;
import org.apache.tomcat.util.bcel.util.ClassQueue;
import org.apache.tomcat.util.bcel.util.SyntheticRepository;

/**
 * Represents a Java class, i.e., the data structures, constant pool,
 * fields, methods and commands contained in a Java .class file.
 * See <a href="ftp://java.sun.com/docs/specs/">JVM specification</a> for details.
 * The intent of this class is to represent a parsed or otherwise existing
 * class file.  Those interested in programatically generating classes
 * should see the <a href="../generic/ClassGen.html">ClassGen</a> class.

 * @version $Id$
 * @see org.apache.tomcat.util.bcel.generic.ClassGen
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class JavaClass extends AccessFlags implements Cloneable, Node, Comparable {

    private String file_name;
    private String package_name;
    private String source_file_name = "<Unknown>";
    private int class_name_index;
    private int superclass_name_index;
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
    private byte source = HEAP; // Generated in memory
    private boolean isAnonymous = false;
    private boolean isNested = false;
    private boolean computedNestedTypeStatus = false;
    public static final byte HEAP = 1;
    public static final byte FILE = 2;
    public static final byte ZIP = 3;
    
    
    
    //  Annotations are collected from certain attributes, don't do it more than necessary!
    private boolean annotationsOutOfDate = true;
    
    private static BCELComparator _cmp = new BCELComparator() {

        public boolean equals( Object o1, Object o2 ) {
            JavaClass THIS = (JavaClass) o1;
            JavaClass THAT = (JavaClass) o2;
            return THIS.getClassName().equals(THAT.getClassName());
        }


        public int hashCode( Object o ) {
            JavaClass THIS = (JavaClass) o;
            return THIS.getClassName().hashCode();
        }
    };
    /**
     * In cases where we go ahead and create something,
     * use the default SyntheticRepository, because we
     * don't know any better.
     */
    private transient org.apache.tomcat.util.bcel.util.Repository repository = SyntheticRepository
            .getInstance();


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
     * @param source Read from file or generated in memory?
     */
    public JavaClass(int class_name_index, int superclass_name_index, String file_name, int major,
            int minor, int access_flags, ConstantPool constant_pool, int[] interfaces,
            Field[] fields, Method[] methods, Attribute[] attributes, byte source) {
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
        this.class_name_index = class_name_index;
        this.superclass_name_index = superclass_name_index;
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
        this.source = source;
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
        int index = class_name.lastIndexOf('.');
        if (index < 0) {
            package_name = "";
        } else {
            package_name = class_name.substring(0, index);
        }
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
      		List accumulatedAnnotations = new ArrayList();
      		for (int i = 0; i < attrs.length; i++) {
    			Attribute attribute = attrs[i];
    			if (attribute instanceof Annotations) {				
    				Annotations runtimeAnnotations = (Annotations)attribute;
    				for(int j = 0; j < runtimeAnnotations.getAnnotationEntries().length; j++)
    					accumulatedAnnotations.add(runtimeAnnotations.getAnnotationEntries()[j]);
    			}
    		}
      		annotations = (AnnotationEntry[])accumulatedAnnotations.toArray(new AnnotationEntry[accumulatedAnnotations.size()]);
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
     * @return Names of implemented interfaces.
     */
    public String[] getInterfaceNames() {
        return interface_names;
    }


    


    


    


    


    


    


    /**
     * @return Superclass name.
     */
    public String getSuperclassName() {
        return superclass_name;
    }


    /**
     * @return String representing class contents.
     */
    public String toString() {
        String access = Utility.accessToString(access_flags, true);
        access = access.equals("") ? "" : (access + " ");
        StringBuffer buf = new StringBuffer(128);
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
        StringBuffer buf = new StringBuffer();
        while (tok.hasMoreTokens()) {
            buf.append("\t").append(tok.nextToken()).append("\n");
        }
        return buf.toString();
    }


    


    public final boolean isSuper() {
        return (access_flags & Constants.ACC_SUPER) != 0;
    }


    
    
    
    
    
    
    /**
     * Sets the ClassRepository which loaded the JavaClass.
     * Should be called immediately after parsing is done.
     */
    public void setRepository( org.apache.tomcat.util.bcel.util.Repository repository ) {
        this.repository = repository;
    }


    /** Equivalent to runtime "instanceof" operator.
     *
     * @return true if this JavaClass is derived from the super class
     * @throws ClassNotFoundException if superclasses or superinterfaces
     *   of this object can't be found
     */
    public final boolean instanceOf( JavaClass super_class ) throws ClassNotFoundException {
        if (this.equals(super_class)) {
            return true;
        }
        JavaClass[] super_classes = getSuperClasses();
        for (int i = 0; i < super_classes.length; i++) {
            if (super_classes[i].equals(super_class)) {
                return true;
            }
        }
        if (super_class.isInterface()) {
            return implementationOf(super_class);
        }
        return false;
    }


    /**
     * @return true, if this class is an implementation of interface inter
     * @throws ClassNotFoundException if superclasses or superinterfaces
     *   of this class can't be found
     */
    public boolean implementationOf( JavaClass inter ) throws ClassNotFoundException {
        if (!inter.isInterface()) {
            throw new IllegalArgumentException(inter.getClassName() + " is no interface");
        }
        if (this.equals(inter)) {
            return true;
        }
        JavaClass[] super_interfaces = getAllInterfaces();
        for (int i = 0; i < super_interfaces.length; i++) {
            if (super_interfaces[i].equals(inter)) {
                return true;
            }
        }
        return false;
    }


    /**
     * @return the superclass for this JavaClass object, or null if this
     * is java.lang.Object
     * @throws ClassNotFoundException if the superclass can't be found
     */
    public JavaClass getSuperClass() throws ClassNotFoundException {
        if ("java.lang.Object".equals(getClassName())) {
            return null;
        }
        return repository.loadClass(getSuperclassName());
    }


    /**
     * @return list of super classes of this class in ascending order, i.e.,
     * java.lang.Object is always the last element
     * @throws ClassNotFoundException if any of the superclasses can't be found
     */
    public JavaClass[] getSuperClasses() throws ClassNotFoundException {
        JavaClass clazz = this;
        List allSuperClasses = new ArrayList();
        for (clazz = clazz.getSuperClass(); clazz != null; clazz = clazz.getSuperClass()) {
            allSuperClasses.add(clazz);
        }
        return (JavaClass[]) allSuperClasses.toArray(new JavaClass[allSuperClasses.size()]);
    }


    /**
     * Get interfaces directly implemented by this JavaClass.
     */
    public JavaClass[] getInterfaces() throws ClassNotFoundException {
        String[] _interfaces = getInterfaceNames();
        JavaClass[] classes = new JavaClass[_interfaces.length];
        for (int i = 0; i < _interfaces.length; i++) {
            classes[i] = repository.loadClass(_interfaces[i]);
        }
        return classes;
    }


    /**
     * Get all interfaces implemented by this JavaClass (transitively).
     */
    public JavaClass[] getAllInterfaces() throws ClassNotFoundException {
        ClassQueue queue = new ClassQueue();
        Set allInterfaces = new TreeSet();
        queue.enqueue(this);
        while (!queue.empty()) {
            JavaClass clazz = queue.dequeue();
            JavaClass souper = clazz.getSuperClass();
            JavaClass[] _interfaces = clazz.getInterfaces();
            if (clazz.isInterface()) {
                allInterfaces.add(clazz);
            } else {
                if (souper != null) {
                    queue.enqueue(souper);
                }
            }
            for (int i = 0; i < _interfaces.length; i++) {
                queue.enqueue(_interfaces[i]);
            }
        }
        return (JavaClass[]) allInterfaces.toArray(new JavaClass[allInterfaces.size()]);
    }


    


    


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default two JavaClass objects are said to be equal when
     * their class names are equal.
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals( Object obj ) {
        return _cmp.equals(this, obj);
    }


    /**
     * Return the natural ordering of two JavaClasses.
     * This ordering is based on the class name
     */
    public int compareTo( Object obj ) {
        return getClassName().compareTo(((JavaClass) obj).getClassName());
    }


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default return the hashcode of the class name.
     * 
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return _cmp.hashCode(this);
    }
}
