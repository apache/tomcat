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



/**
 * This class represents a JVM execution frame; that means,
 * a local variable array and an operand stack.
 *
 * @version $Id$
 * @author Enver Haase
 */
 
public class Frame{

	/**
	 * For instance initialization methods, it is important to remember
	 * which instance it is that is not initialized yet. It will be
	 * initialized invoking another constructor later.
	 * NULL means the instance already *is* initialized.
	 */
	protected static UninitializedObjectType _this;

	/**
	 *
	 */
	private LocalVariables locals;

	/**
	 *
	 */
	private OperandStack stack;

	/**
	 *
	 */
	public Frame(int maxLocals, int maxStack){
		locals = new LocalVariables(maxLocals);
		stack = new OperandStack(maxStack);
	}

	/**
	 *
	 */
	public Frame(LocalVariables locals, OperandStack stack){
		this.locals = locals;
		this.stack = stack;
	}

	/**
	 *
	 */
	protected Object clone(){
		Frame f = new Frame(locals.getClone(), stack.getClone());
		return f;
	}

	/**
	 *
	 */
	public Frame getClone(){
		return (Frame) clone();
	}

	/**
	 *
	 */
	public LocalVariables getLocals(){
		return locals;
	}

	/**
	 *
	 */
	public OperandStack getStack(){
		return stack;
	}

	/** @return a hash code value for the object.
     */
	public int hashCode() { return stack.hashCode() ^ locals.hashCode(); }

	/**
	 *
	 */
	public boolean equals(Object o){
		if (!(o instanceof Frame)) {
            return false; // implies "null" is non-equal.
        }
		Frame f = (Frame) o;
		return this.stack.equals(f.stack) && this.locals.equals(f.locals);
	}

	/**
	 * Returns a String representation of the Frame instance.
	 */
	public String toString(){
		String s="Local Variables:\n";
		s += locals;
		s += "OperandStack:\n";
		s += stack;
		return s;
	}
}
