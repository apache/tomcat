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
 * Instances of this class are thrown by BCEL's class file verifier "JustIce"
 * when a class file to verify does not pass the verification pass 2 as described
 * in the Java Virtual Machine specification, 2nd edition.
 *
 * @version $Id$
 * @author Enver Haase
 */
public class ClassConstraintException extends VerificationException{
	/**
	 * Constructs a new ClassConstraintException with null as its error message string.
	 */
	public ClassConstraintException(){
		super();
	}
	
	/**
	 * Constructs a new ClassConstraintException with the specified error message.
	 */
	public ClassConstraintException(String message){
		super (message);
	}
	
	/**
	 * Constructs a new ClassConstraintException with the specified error message and cause
	 */
	public ClassConstraintException(String message, Throwable initCause){
		super(message, initCause);
	}
}
