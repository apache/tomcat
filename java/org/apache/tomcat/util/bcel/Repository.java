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

import org.apache.tomcat.util.bcel.classfile.JavaClass;
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
     * @return true, if clazz is an implementation of interface inter
     * @throws ClassNotFoundException if any superclasses or superinterfaces
     *   of clazz can't be found
     */
    public static boolean implementationOf( JavaClass clazz, JavaClass inter )
            throws ClassNotFoundException {
        return clazz.implementationOf(inter);
    }


    


    


    
}
