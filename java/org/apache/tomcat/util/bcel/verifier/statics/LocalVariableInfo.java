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
package org.apache.tomcat.util.bcel.verifier.statics;


import java.util.Hashtable;
import org.apache.tomcat.util.bcel.generic.Type;
import org.apache.tomcat.util.bcel.verifier.exc.LocalVariableInfoInconsistentException;

/**
 * A utility class holding the information about
 * the name and the type of a local variable in
 * a given slot (== index). This information
 * often changes in course of byte code offsets.
 *
 * @version $Id$
 * @author Enver Haase
 */
public class LocalVariableInfo{

	/** The types database. KEY: String representing the offset integer. */
	private Hashtable types = new Hashtable();
	/** The names database. KEY: String representing the offset integer. */
	private Hashtable names = new Hashtable();

	/**
	 * Adds a name of a local variable and a certain slot to our 'names'
	 * (Hashtable) database.
	 */
	private void setName(int offset, String name){
		names.put( ((Integer.toString(offset))), name);
	}
	/**
	 * Adds a type of a local variable and a certain slot to our 'types'
	 * (Hashtable) database.
	 */
	private void setType(int offset, Type t){
		types.put( ((Integer.toString(offset))), t);
	}

	/**
	 * Returns the type of the local variable that uses this local
	 * variable slot at the given bytecode offset.
	 * Care for legal bytecode offsets yourself, otherwise the return value
	 * might be wrong.
	 * May return 'null' if nothing is known about the type of this local
	 * variable slot at the given bytecode offset.
	 */
	public Type getType(int offset){
		return (Type) types.get(Integer.toString(offset));
	}
	/**
	 * Returns the name of the local variable that uses this local
	 * variable slot at the given bytecode offset.
	 * Care for legal bytecode offsets yourself, otherwise the return value
	 * might be wrong.
	 * May return 'null' if nothing is known about the type of this local
	 * variable slot at the given bytecode offset.
	 */
	public String getName(int offset){
		return (String) (names.get(Integer.toString(offset)));
	}
	/**
	 * Adds some information about this local variable (slot).
	 * @throws LocalVariableInfoInconsistentException if the new information conflicts
	 *         with already gathered information.
	 */
	public void add(String name, int startpc, int length, Type t) throws LocalVariableInfoInconsistentException{
		for (int i=startpc; i<=startpc+length; i++){ // incl/incl-notation!
			add(i,name,t);
		}
	}

	/**
	 * Adds information about name and type for a given offset.
	 * @throws LocalVariableInfoInconsistentException if the new information conflicts
	 *         with already gathered information.
	 */
	private void add(int offset, String name, Type t) throws LocalVariableInfoInconsistentException{
		if (getName(offset) != null){
			if (! getName(offset).equals(name)){
				throw new LocalVariableInfoInconsistentException("At bytecode offset '"+offset+"' a local variable has two different names: '"+getName(offset)+"' and '"+name+"'.");
			}
		}
		if (getType(offset) != null){
			if (! getType(offset).equals(t)){
				throw new LocalVariableInfoInconsistentException("At bytecode offset '"+offset+"' a local variable has two different types: '"+getType(offset)+"' and '"+t+"'.");
			}
		}
		setName(offset, name);
		setType(offset, t);
	}
}
