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

import java.util.ArrayList;
import java.util.List;
import org.apache.tomcat.util.bcel.classfile.JavaClass;

/** 
 * Utility class implementing a (typesafe) collection of JavaClass
 * objects. Contains the most important methods of a Vector.
 *
 * @version $Id$
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A> 
 * 
 * @deprecated as of 5.1.1 - 7/17/2005
 */
public class ClassVector implements java.io.Serializable {

    protected List vec = new ArrayList();


    public void addElement( JavaClass clazz ) {
        vec.add(clazz);
    }


    public JavaClass elementAt( int index ) {
        return (JavaClass) vec.get(index);
    }


    public void removeElementAt( int index ) {
        vec.remove(index);
    }


    public JavaClass[] toArray() {
        JavaClass[] classes = new JavaClass[vec.size()];
        vec.toArray(classes);
        return classes;
    }
}
