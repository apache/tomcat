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

/**
 * Denotes that an instruction may start the process of loading and resolving 
 * the referenced class in the Virtual Machine.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public interface LoadClass {

    /**
     * Returns the ObjectType of the referenced class or interface
     * that may be loaded and resolved.
     * @return object type that may be loaded or null if a primitive is
     * referenced
     */
    public ObjectType getLoadClassType( ConstantPoolGen cpg );


    /**
     * Returns the type associated with this instruction.
     * LoadClass instances are always typed, but this type
     * does not always refer to the type of the class or interface
     * that it possibly forces to load. For example, GETFIELD would
     * return the type of the field and not the type of the class
     * where the field is defined.
     * If no class is forced to be loaded, <B>null</B> is returned.
     * An example for this is an ANEWARRAY instruction that creates
     * an int[][].
     * @see #getLoadClassType(ConstantPoolGen)
     */
    public Type getType( ConstantPoolGen cpg );
}
