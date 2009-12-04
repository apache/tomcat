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
package org.apache.tomcat.util.bcel.generic;

import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.Repository;
import org.apache.tomcat.util.bcel.classfile.JavaClass;

/** 
 * Denotes reference such as java.lang.String.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class ObjectType extends ReferenceType {

    private String class_name; // Class name of type


    /**
     * @param class_name fully qualified class name, e.g. java.lang.String
     */
    public ObjectType(String class_name) {
        super(Constants.T_REFERENCE, "L" + class_name.replace('.', '/') + ";");
        this.class_name = class_name.replace('/', '.');
    }


    /** @return name of referenced class
     */
    public String getClassName() {
        return class_name;
    }


    /** @return a hash code value for the object.
     */
    public int hashCode() {
        return class_name.hashCode();
    }


    /** @return true if both type objects refer to the same class.
     */
    public boolean equals( Object type ) {
        return (type instanceof ObjectType)
                ? ((ObjectType) type).class_name.equals(class_name)
                : false;
    }


    /**
     * If "this" doesn't reference a class, it references an interface
     * or a non-existant entity.
     * @deprecated this method returns an inaccurate result
     *   if the class or interface referenced cannot
     *   be found: use referencesClassExact() instead
     */
    public boolean referencesClass() {
        try {
            JavaClass jc = Repository.lookupClass(class_name);
            return jc.isClass();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    /**
     * If "this" doesn't reference an interface, it references a class
     * or a non-existant entity.
     * @deprecated this method returns an inaccurate result
     *   if the class or interface referenced cannot
     *   be found: use referencesInterfaceExact() instead
     */
    public boolean referencesInterface() {
        try {
            JavaClass jc = Repository.lookupClass(class_name);
            return !jc.isClass();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    /**
     * Return true if this type references a class,
     * false if it references an interface.
     * @return true if the type references a class, false if
     *   it references an interface
     * @throws ClassNotFoundException if the class or interface
     *   referenced by this type can't be found
     */
    public boolean referencesClassExact() throws ClassNotFoundException {
        JavaClass jc = Repository.lookupClass(class_name);
        return jc.isClass();
    }


    /**
     * Return true if this type references an interface,
     * false if it references a class.
     * @return true if the type references an interface, false if
     *   it references a class
     * @throws ClassNotFoundException if the class or interface
     *   referenced by this type can't be found
     */
    public boolean referencesInterfaceExact() throws ClassNotFoundException {
        JavaClass jc = Repository.lookupClass(class_name);
        return !jc.isClass();
    }


    /**
     * Return true if this type is a subclass of given ObjectType.
     * @throws ClassNotFoundException if any of this class's superclasses
     *  can't be found
     */
    public boolean subclassOf( ObjectType superclass ) throws ClassNotFoundException {
        if (this.referencesInterface() || superclass.referencesInterface()) {
            return false;
        }
        return Repository.instanceOf(this.class_name, superclass.class_name);
    }


    /**
     * Java Virtual Machine Specification edition 2, � 5.4.4 Access Control
     * @throws ClassNotFoundException if the class referenced by this type
     *   can't be found
     */
    public boolean accessibleTo( ObjectType accessor ) throws ClassNotFoundException {
        JavaClass jc = Repository.lookupClass(class_name);
        if (jc.isPublic()) {
            return true;
        } else {
            JavaClass acc = Repository.lookupClass(accessor.class_name);
            return acc.getPackageName().equals(jc.getPackageName());
        }
    }
}
