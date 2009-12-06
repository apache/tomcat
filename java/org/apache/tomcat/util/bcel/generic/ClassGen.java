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

import org.apache.tomcat.util.bcel.classfile.AccessFlags;
import org.apache.tomcat.util.bcel.classfile.JavaClass;
import org.apache.tomcat.util.bcel.util.BCELComparator;

/** 
 * Template class for building up a java class. May be initialized with an
 * existing java class (file).
 *
 * @see JavaClass
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public class ClassGen extends AccessFlags implements Cloneable {

    /* Corresponds to the fields found in a JavaClass object.
     */
    private String class_name;
	
    private static BCELComparator _cmp = new BCELComparator() {

        public boolean equals( Object o1, Object o2 ) {
            ClassGen THIS = (ClassGen) o1;
            ClassGen THAT = (ClassGen) o2;
            return THIS.getClassName().equals(THAT.getClassName());
        }


        public int hashCode( Object o ) {
            ClassGen THIS = (ClassGen) o;
            return THIS.getClassName().hashCode();
        }
    };
    
    
    public String getClassName() {
        return class_name;
    }

    
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            System.err.println(e);
            return null;
        }
    }

    
    /**
     * Return value as defined by given BCELComparator strategy.
     * By default two ClassGen objects are said to be equal when
     * their class names are equal.
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals( Object obj ) {
        return _cmp.equals(this, obj);
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
