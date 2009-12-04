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
package org.apache.tomcat.util.bcel;

import java.io.IOException;
import org.apache.tomcat.util.bcel.classfile.JavaClass;
import org.apache.tomcat.util.bcel.util.ClassPath;
import org.apache.tomcat.util.bcel.util.SyntheticRepository;

/**
 * The repository maintains informations about class interdependencies, e.g.,
 * whether a class is a sub-class of another. Delegates actual class loading
 * to SyntheticRepository with current class path by default.
 *
 * @see org.apache.tomcat.util.bcel.util.Repository
 * @see org.apache.tomcat.util.bcel.util.SyntheticRepository
 *
 * @version $Id$
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class Repository {

    private static org.apache.tomcat.util.bcel.util.Repository _repository = SyntheticRepository.getInstance();


    /** @return currently used repository instance
     */
    public static org.apache.tomcat.util.bcel.util.Repository getRepository() {
        return _repository;
    }


    /** Set repository instance to be used for class loading
     */
    public static void setRepository( org.apache.tomcat.util.bcel.util.Repository rep ) {
        _repository = rep;
    }


    /** Lookup class somewhere found on your CLASSPATH, or whereever the
     * repository instance looks for it.
     *
     * @return class object for given fully qualified class name
     * @throws ClassNotFoundException if the class could not be found or
     * parsed correctly
     */
    public static JavaClass lookupClass( String class_name ) throws ClassNotFoundException {
        return _repository.loadClass(class_name);
    }


    /**
     * Try to find class source using the internal repository instance.
     * @see Class
     * @return JavaClass object for given runtime class
     * @throws ClassNotFoundException if the class could not be found or
     * parsed correctly
     */
    public static JavaClass lookupClass( Class clazz ) throws ClassNotFoundException {
        return _repository.loadClass(clazz);
    }


    /**
     * @return class file object for given Java class by looking on the
     *  system class path; returns null if the class file can't be
     *  found
     */
    public static ClassPath.ClassFile lookupClassFile( String class_name ) {
        try {
            ClassPath path = _repository.getClassPath();
            if (path == null) {
                return null;
            }
            return path.getClassFile(class_name);
        } catch (IOException e) {
            return null;
        }
    }


    /** Clear the repository.
     */
    public static void clearCache() {
        _repository.clear();
    }


    /**
     * Add clazz to repository if there isn't an equally named class already in there.
     *
     * @return old entry in repository
     */
    public static JavaClass addClass( JavaClass clazz ) {
        JavaClass old = _repository.findClass(clazz.getClassName());
        _repository.storeClass(clazz);
        return old;
    }


    /**
     * Remove class with given (fully qualified) name from repository.
     */
    public static void removeClass( String clazz ) {
        _repository.removeClass(_repository.findClass(clazz));
    }


    /**
     * Remove given class from repository.
     */
    public static void removeClass( JavaClass clazz ) {
        _repository.removeClass(clazz);
    }


    /**
     * @return list of super classes of clazz in ascending order, i.e.,
     * Object is always the last element
     * @throws ClassNotFoundException if any of the superclasses can't be found
     */
    public static JavaClass[] getSuperClasses( JavaClass clazz ) throws ClassNotFoundException {
        return clazz.getSuperClasses();
    }


    /**
     * @return list of super classes of clazz in ascending order, i.e.,
     * Object is always the last element.
     * @throws ClassNotFoundException if the named class or any of its
     *  superclasses can't be found
     */
    public static JavaClass[] getSuperClasses( String class_name ) throws ClassNotFoundException {
        JavaClass jc = lookupClass(class_name);
        return getSuperClasses(jc);
    }


    /**
     * @return all interfaces implemented by class and its super
     * classes and the interfaces that those interfaces extend, and so on.
     * (Some people call this a transitive hull).
     * @throws ClassNotFoundException if any of the class's
     *  superclasses or superinterfaces can't be found
     */
    public static JavaClass[] getInterfaces( JavaClass clazz ) throws ClassNotFoundException {
        return clazz.getAllInterfaces();
    }


    /**
     * @return all interfaces implemented by class and its super
     * classes and the interfaces that extend those interfaces, and so on
     * @throws ClassNotFoundException if the named class can't be found,
     *   or if any of its superclasses or superinterfaces can't be found
     */
    public static JavaClass[] getInterfaces( String class_name ) throws ClassNotFoundException {
        return getInterfaces(lookupClass(class_name));
    }


    /**
     * Equivalent to runtime "instanceof" operator.
     * @return true, if clazz is an instance of super_class
     * @throws ClassNotFoundException if any superclasses or superinterfaces
     *   of clazz can't be found
     */
    public static boolean instanceOf( JavaClass clazz, JavaClass super_class )
            throws ClassNotFoundException {
        return clazz.instanceOf(super_class);
    }


    /**
     * @return true, if clazz is an instance of super_class
     * @throws ClassNotFoundException if either clazz or super_class
     *   can't be found
     */
    public static boolean instanceOf( String clazz, String super_class )
            throws ClassNotFoundException {
        return instanceOf(lookupClass(clazz), lookupClass(super_class));
    }


    /**
     * @return true, if clazz is an instance of super_class
     * @throws ClassNotFoundException if super_class can't be found
     */
    public static boolean instanceOf( JavaClass clazz, String super_class )
            throws ClassNotFoundException {
        return instanceOf(clazz, lookupClass(super_class));
    }


    /**
     * @return true, if clazz is an instance of super_class
     * @throws ClassNotFoundException if clazz can't be found
     */
    public static boolean instanceOf( String clazz, JavaClass super_class )
            throws ClassNotFoundException {
        return instanceOf(lookupClass(clazz), super_class);
    }


    /**
     * @return true, if clazz is an implementation of interface inter
     * @throws ClassNotFoundException if any superclasses or superinterfaces
     *   of clazz can't be found
     */
    public static boolean implementationOf( JavaClass clazz, JavaClass inter )
            throws ClassNotFoundException {
        return clazz.implementationOf(inter);
    }


    /**
     * @return true, if clazz is an implementation of interface inter
     * @throws ClassNotFoundException if clazz, inter, or any superclasses
     *   or superinterfaces of clazz can't be found
     */
    public static boolean implementationOf( String clazz, String inter )
            throws ClassNotFoundException {
        return implementationOf(lookupClass(clazz), lookupClass(inter));
    }


    /**
     * @return true, if clazz is an implementation of interface inter
     * @throws ClassNotFoundException if inter or any superclasses
     *   or superinterfaces of clazz can't be found
     */
    public static boolean implementationOf( JavaClass clazz, String inter )
            throws ClassNotFoundException {
        return implementationOf(clazz, lookupClass(inter));
    }


    /**
     * @return true, if clazz is an implementation of interface inter
     * @throws ClassNotFoundException if clazz or any superclasses or
     *   superinterfaces of clazz can't be found
     */
    public static boolean implementationOf( String clazz, JavaClass inter )
            throws ClassNotFoundException {
        return implementationOf(lookupClass(clazz), inter);
    }
}
