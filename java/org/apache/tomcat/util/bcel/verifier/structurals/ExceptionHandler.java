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


import org.apache.tomcat.util.bcel.generic.InstructionHandle;
import org.apache.tomcat.util.bcel.generic.ObjectType;

/**
 * This class represents an exception handler; that is, an ObjectType
 * representing a subclass of java.lang.Throwable and the instruction
 * the handler starts off (represented by an InstructionContext).
 * 
 * @version $Id$
 * @author Enver Haase
 */
public class ExceptionHandler{
	/** The type of the exception to catch. NULL means ANY. */
	private ObjectType catchtype;
	
	/** The InstructionHandle where the handling begins. */
	private InstructionHandle handlerpc;

	/** Leave instance creation to JustIce. */
	ExceptionHandler(ObjectType catch_type, InstructionHandle handler_pc){
		catchtype = catch_type;
		handlerpc = handler_pc;
	}

	/**
	 * Returns the type of the exception that's handled. <B>'null' means 'ANY'.</B>
	 */
	public ObjectType getExceptionType(){
		return catchtype;
	}

	/**
	 * Returns the InstructionHandle where the handler starts off.
	 */
	public InstructionHandle getHandlerStart(){
		return handlerpc;
	}
}
