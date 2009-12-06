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

import java.util.ArrayList;
import java.util.List;
import org.apache.tomcat.util.bcel.classfile.AccessFlags;
import org.apache.tomcat.util.bcel.classfile.Attribute;

/**
 * Super class for FieldGen and MethodGen objects, since they have
 * some methods in common!
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class FieldGenOrMethodGen extends AccessFlags implements NamedAndTyped, Cloneable {

    protected String name;
    protected Type type;
    protected ConstantPoolGen cp;
    private List attribute_vec = new ArrayList();
    


    protected FieldGenOrMethodGen() {
    }


    


    


    /** @return name of method/field.
     */
    public String getName() {
        return name;
    }


    


    


    


    
    
    


    
    
    


    
    
    


    /**
     * @return all attributes of this method.
     */
    public Attribute[] getAttributes() {
        Attribute[] attributes = new Attribute[attribute_vec.size()];
        attribute_vec.toArray(attributes);
        return attributes;
    }
    
    


    /** @return signature of method/field.
     */
    public abstract String getSignature();


    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            System.err.println(e);
            return null;
        }
    }
}
