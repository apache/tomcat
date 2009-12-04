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
 * Instances of this class are thrown by BCEL's class file verifier "JustIce" when
 * a class file to verify does not pass the verification pass 3 because of a violation
 * of a structural constraint as described in the Java Virtual Machine Specification,
 * 2nd edition, 4.8.2, pages 137-139.
 * Note that the notion of a "structural" constraint is somewhat misleading. Structural
 * constraints are constraints on relationships between Java virtual machine instructions.
 * These are the constraints where data-flow analysis is needed to verify if they hold.
 * The data flow analysis of pass 3 is called pass 3b in JustIce.
 *
 * @version $Id$
 * @author Enver Haase
 */
public class StructuralCodeConstraintException extends CodeConstraintException{
	/**
	 * Constructs a new StructuralCodeConstraintException with the specified error message.
	 */
	public StructuralCodeConstraintException(String message){
		super(message);
	}
	/**
	 * Constructs a new StructuralCodeConstraintException with null as its error message string.
	 */
	public StructuralCodeConstraintException(){
		super();
	}
}
