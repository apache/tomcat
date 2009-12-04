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

import org.apache.tomcat.util.bcel.classfile.JavaClass;

/**
 * Abstract definition of a class repository. Instances may be used
 * to load classes from different sources and may be used in the
 * Repository.setRepository method.
 *
 * @see org.apache.tomcat.util.bcel.Repository
 * @version $Id$
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @author David Dixon-Peugh
 */
public interface Repository extends java.io.Serializable {

    /**
     * Store the provided class under "clazz.getClassName()" 
     */
    public void storeClass( JavaClass clazz );


    /**
     * Remove class from repository
     */
    public void removeClass( JavaClass clazz );


    /**
     * Find the class with the name provided, if the class
     * isn't there, return NULL.
     */
    public JavaClass findClass( String className );


    /**
     * Find the class with the name provided, if the class
     * isn't there, make an attempt to load it.
     */
    public JavaClass loadClass( String className ) throws java.lang.ClassNotFoundException;


    /**
     * Find the JavaClass instance for the given run-time class object
     */
    public JavaClass loadClass( Class clazz ) throws java.lang.ClassNotFoundException;


    /** Clear all entries from cache.
     */
    public void clear();


    /** Get the ClassPath associated with this Repository
     */
    public ClassPath getClassPath();
}
