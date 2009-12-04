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
package org.apache.tomcat.util.bcel.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import org.apache.tomcat.util.bcel.classfile.ClassParser;
import org.apache.tomcat.util.bcel.classfile.JavaClass;

/**
 * This repository is used in situations where a Class is created
 * outside the realm of a ClassLoader. Classes are loaded from
 * the file systems using the paths specified in the given
 * class path. By default, this is the value returned by
 * ClassPath.getClassPath().
 * <br>
 * It is designed to be used as a singleton, however it
 * can also be used with custom classpaths.
 *
 /**
 * Abstract definition of a class repository. Instances may be used
 * to load classes from different sources and may be used in the
 * Repository.setRepository method.
 *
 * @see org.apache.tomcat.util.bcel.Repository
 *
 * @version $Id$
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @author David Dixon-Peugh
 */
public class SyntheticRepository implements Repository {

    //private static final String DEFAULT_PATH = ClassPath.getClassPath();
    private static Map _instances = new HashMap(); // CLASSPATH X REPOSITORY
    private ClassPath _path = null;
    private Map _loadedClasses = new HashMap(); // CLASSNAME X JAVACLASS


    private SyntheticRepository(ClassPath path) {
        _path = path;
    }


    public static SyntheticRepository getInstance() {
        return getInstance(ClassPath.SYSTEM_CLASS_PATH);
    }


    public static SyntheticRepository getInstance( ClassPath classPath ) {
        SyntheticRepository rep = (SyntheticRepository) _instances.get(classPath);
        if (rep == null) {
            rep = new SyntheticRepository(classPath);
            _instances.put(classPath, rep);
        }
        return rep;
    }


    /**
     * Store a new JavaClass instance into this Repository.
     */
    public void storeClass( JavaClass clazz ) {
        _loadedClasses.put(clazz.getClassName(), new SoftReference(clazz));
        clazz.setRepository(this);
    }


    /**
     * Remove class from repository
     */
    public void removeClass( JavaClass clazz ) {
        _loadedClasses.remove(clazz.getClassName());
    }


    /**
     * Find an already defined (cached) JavaClass object by name.
     */
    public JavaClass findClass( String className ) {
        SoftReference ref = (SoftReference) _loadedClasses.get(className);
        if (ref == null) {
            return null;
        }
        return (JavaClass) ref.get();
    }


    /**
     * Find a JavaClass object by name.
     * If it is already in this Repository, the Repository version
     * is returned.  Otherwise, the Repository's classpath is searched for
     * the class (and it is added to the Repository if found).
     *
     * @param className the name of the class
     * @return the JavaClass object
     * @throws ClassNotFoundException if the class is not in the
     *   Repository, and could not be found on the classpath
     */
    public JavaClass loadClass( String className ) throws ClassNotFoundException {
        if (className == null || className.equals("")) {
            throw new IllegalArgumentException("Invalid class name " + className);
        }
        className = className.replace('/', '.'); // Just in case, canonical form
        JavaClass clazz = findClass(className);
        if (clazz != null) {
            return clazz;
        }
        try {
            return loadClass(_path.getInputStream(className), className);
        } catch (IOException e) {
            throw new ClassNotFoundException("Exception while looking for class " + className
                    + ": " + e.toString(), e);
        }
    }


    /**
     * Find the JavaClass object for a runtime Class object.
     * If a class with the same name is already in this Repository,
     * the Repository version is returned.  Otherwise, getResourceAsStream()
     * is called on the Class object to find the class's representation.
     * If the representation is found, it is added to the Repository.
     *
     * @see Class
     * @param clazz the runtime Class object
     * @return JavaClass object for given runtime class
     * @throws ClassNotFoundException if the class is not in the
     *   Repository, and its representation could not be found
     */
    public JavaClass loadClass( Class clazz ) throws ClassNotFoundException {
    	InputStream clsStream = null;
    	try{
	        String className = clazz.getName();
	        JavaClass repositoryClass = findClass(className);
	        if (repositoryClass != null) {
	            return repositoryClass;
	        }
	        String name = className;
	        int i = name.lastIndexOf('.');
	        if (i > 0) {
	            name = name.substring(i + 1);
	        }
	        clsStream = clazz.getResourceAsStream(name + ".class");
	        return loadClass(clsStream, className);
    	} finally {
    		try{
	    		if (clsStream != null){
	    			clsStream.close();
	    		}
    		} catch(IOException ioe){
    			//don't care
    		}
    	}
    }


    private JavaClass loadClass( InputStream is, String className ) throws ClassNotFoundException {
        try {
            if (is != null) {
                ClassParser parser = new ClassParser(is, className);
                JavaClass clazz = parser.parse();
                storeClass(clazz);
                return clazz;
            }
        } catch (IOException e) {
            throw new ClassNotFoundException("Exception while looking for class " + className
                    + ": " + e.toString(), e);
        }
        throw new ClassNotFoundException("SyntheticRepository could not load " + className);
    }


    /** ClassPath associated with the Repository.
     */
    public ClassPath getClassPath() {
        return _path;
    }


    /** Clear all entries from cache.
     */
    public void clear() {
        _loadedClasses.clear();
    }
}
