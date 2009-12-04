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
package org.apache.tomcat.util.bcel.verifier.structurals;


import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.generic.ObjectType;
import org.apache.tomcat.util.bcel.generic.ReferenceType;

/**
 * This class represents an uninitialized object type; see The Java
 * Virtual Machine Specification, Second Edition, page 147: 4.9.4 for
 * more details.
 *
 * @version $Id$
 * @author Enver Haase
 */
public class UninitializedObjectType extends ReferenceType implements Constants{

	/** The "initialized" version. */
	private ObjectType initialized;
	
	/** Creates a new instance. */
	public UninitializedObjectType(ObjectType t){
		super(T_UNKNOWN, "<UNINITIALIZED OBJECT OF TYPE '"+t.getClassName()+"'>");
		initialized = t;
	}

	/**
	 * Returns the ObjectType of the same class as the one of the uninitialized object
	 * represented by this UninitializedObjectType instance.
	 */
	public ObjectType getInitialized(){
		return initialized;
	}

	/** @return a hash code value for the object.
     */
	public int hashCode() { return initialized.hashCode(); }

	/**
	 * Returns true on equality of this and o.
	 * Equality means the ObjectType instances of "initialized"
	 * equal one another in this and the o instance.
	 *
	 */
	public boolean equals(Object o){
		if (! (o instanceof UninitializedObjectType)) {
            return false;
        }
		return initialized.equals(((UninitializedObjectType)o).initialized);
	}
}
