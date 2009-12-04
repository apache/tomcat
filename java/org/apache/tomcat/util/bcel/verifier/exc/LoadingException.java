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
 * When loading a class file, BCEL will throw an instance of LoadingException if
 * the class file is malformed; so it is not conforming to the "Pass 1" verification
 * process as described in the Java Virtual Machine specification, 2nd. edition.
 * @version $Id$
 * @author Enver Haase
 */
public class LoadingException extends VerifierConstraintViolatedException{

	/**
	 * Constructs a new LoadingException with null as its error message string.
	 */
	public LoadingException(){
		super();
	}
                   
	/**
	 * Constructs a new LoadingException with the specified error message.
	 */
	public LoadingException(String message){
		super (message);
	}
}
