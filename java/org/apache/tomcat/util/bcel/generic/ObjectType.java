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


    


    


    


    


    


    
}
