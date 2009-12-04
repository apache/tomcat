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
import java.util.HashMap;
import java.util.Map;
import org.apache.tomcat.util.bcel.classfile.ClassParser;
import org.apache.tomcat.util.bcel.classfile.JavaClass;

/**
 * The repository maintains information about which classes have
 * been loaded.
 *
 * It loads its data from the ClassLoader implementation
 * passed into its constructor.
 *
 * @see org.apache.tomcat.util.bcel.Repository
 *
 * @version $Id$
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @author David Dixon-Peugh
 */
public class ClassLoaderRepository implements Repository {

    private java.lang.ClassLoader loader;
    private Map loadedClasses = new HashMap(); // CLASSNAME X JAVACLASS


    public ClassLoaderRepository(java.lang.ClassLoader loader) {
        this.loader = loader;
    }


    /**
     * Store a new JavaClass into this Repository.
     */
    public void storeClass( JavaClass clazz ) {
        loadedClasses.put(clazz.getClassName(), clazz);
        clazz.setRepository(this);
    }


    /**
     * Remove class from repository
     */
    public void removeClass( JavaClass clazz ) {
        loadedClasses.remove(clazz.getClassName());
    }


    /**
     * Find an already defined JavaClass.
     */
    public JavaClass findClass( String className ) {
        if (loadedClasses.containsKey(className)) {
            return (JavaClass) loadedClasses.get(className);
        } else {
            return null;
        }
    }


    /**
     * Lookup a JavaClass object from the Class Name provided.
     */
    public JavaClass loadClass( String className ) throws ClassNotFoundException {
        String classFile = className.replace('.', '/');
        JavaClass RC = findClass(className);
        if (RC != null) {
            return RC;
        }
        try {
            InputStream is = loader.getResourceAsStream(classFile + ".class");
            if (is == null) {
                throw new ClassNotFoundException(className + " not found.");
            }
            ClassParser parser = new ClassParser(is, className);
            RC = parser.parse();
            storeClass(RC);
            return RC;
        } catch (IOException e) {
            throw new ClassNotFoundException(className + " not found: " + e.toString(), e);
        }
    }


    public JavaClass loadClass( Class clazz ) throws ClassNotFoundException {
        return loadClass(clazz.getName());
    }


    /** Clear all entries from cache.
     */
    public void clear() {
        loadedClasses.clear();
    }


    /*
     * @return null
     */
    public ClassPath getClassPath() {
        return null;
    }
}
