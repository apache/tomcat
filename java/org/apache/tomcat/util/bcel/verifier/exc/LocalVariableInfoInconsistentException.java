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
package org.apache.tomcat.util.bcel.verifier.exc;


/**
 * A LocalVariableInfoInconsistentException instance is thrown by
 * the LocalVariableInfo class when it detects that the information
 * it holds is inconsistent; this is normally due to inconsistent
 * LocalVariableTable entries in the Code attribute of a certain
 * Method object.
 *
 * @version $Id$
 * @author Enver Haase
 */
public class LocalVariableInfoInconsistentException extends ClassConstraintException{
	/**
	 * Constructs a new LocalVariableInfoInconsistentException with null as its error message string.
	 */
	public LocalVariableInfoInconsistentException(){
		super();
	}
	
	/**
	 * Constructs a new LocalVariableInfoInconsistentException with the specified error message.
	 */
	public LocalVariableInfoInconsistentException(String message){
		super (message);
	}
}
