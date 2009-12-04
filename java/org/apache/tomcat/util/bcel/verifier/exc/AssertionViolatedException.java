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
 * Instances of this class should never be thrown. When such an instance is thrown,
 * this is due to an INTERNAL ERROR of BCEL's class file verifier &quot;JustIce&quot;.
 *
 * @version $Id$
 * @author Enver Haase
 */
public final class AssertionViolatedException extends RuntimeException{
	/** The error message. */
	private String detailMessage;
	/** Constructs a new AssertionViolatedException with null as its error message string. */
	public AssertionViolatedException(){
		super();
	}
	/**
	 * Constructs a new AssertionViolatedException with the specified error message preceded
	 * by &quot;INTERNAL ERROR: &quot;.
	 */
	public AssertionViolatedException(String message){
		super(message = "INTERNAL ERROR: "+message); // Thanks to Java, the constructor call here must be first.
		detailMessage=message;
	}
	/**
	 * Constructs a new AssertionViolationException with the specified error message and initial cause
	 */
	public AssertionViolatedException(String message, Throwable initCause) {
		super(message = "INTERNAL ERROR: "+message, initCause);
		detailMessage=message;
	}	
	/** Extends the error message with a string before ("pre") and after ("post") the
	    'old' error message. All of these three strings are allowed to be null, and null
	    is always replaced by the empty string (""). In particular, after invoking this
	    method, the error message of this object can no longer be null.
	*/
	public void extendMessage(String pre, String post){
		if (pre  == null) {
            pre="";
        }
		if (detailMessage == null) {
            detailMessage="";
        }
		if (post == null) {
            post="";
        }
		detailMessage = pre+detailMessage+post;
	}
	/**
	 * Returns the error message string of this AssertionViolatedException object.
	 * @return the error message string of this AssertionViolatedException.
	 */
	public String getMessage(){
		return detailMessage;
	}

	/** 
	 * DO NOT USE. It's for experimental testing during development only.
	 */
	public static void main(String[] args){
		AssertionViolatedException ave = new AssertionViolatedException("Oops!");
		ave.extendMessage("\nFOUND:\n\t","\nExiting!!\n");
		throw ave;
	}

}
