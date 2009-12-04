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


import java.util.ArrayList;
import org.apache.tomcat.util.bcel.generic.ObjectType;
import org.apache.tomcat.util.bcel.generic.ReferenceType;
import org.apache.tomcat.util.bcel.generic.Type;
import org.apache.tomcat.util.bcel.verifier.exc.AssertionViolatedException;
import org.apache.tomcat.util.bcel.verifier.exc.StructuralCodeConstraintException;

/**
 * This class implements a stack used for symbolic JVM stack simulation.
 * [It's used an an operand stack substitute.]
 * Elements of this stack are org.apache.tomcat.util.bcel.generic.Type objects.
 *
 * @version $Id$
 * @author Enver Haase
 */
public class OperandStack{

	/** We hold the stack information here. */
	private ArrayList stack = new ArrayList();

	/** The maximum number of stack slots this OperandStack instance may hold. */
	private int maxStack;

	/**
	 * Creates an empty stack with a maximum of maxStack slots.
	 */
	public OperandStack(int maxStack){
		this.maxStack = maxStack;
	}

	/**
	 * Creates an otherwise empty stack with a maximum of maxStack slots and
	 * the ObjectType 'obj' at the top.
	 */
	public OperandStack(int maxStack, ObjectType obj){
		this.maxStack = maxStack;
		this.push(obj);
	}	
	/**
	 * Returns a deep copy of this object; that means, the clone operates
	 * on a new stack. However, the Type objects on the stack are
	 * shared.
	 */
	protected Object clone(){
		OperandStack newstack = new OperandStack(this.maxStack);
		newstack.stack = (ArrayList) this.stack.clone();
		return newstack;
	}

	/**
	 * Clears the stack.
	 */
	public void clear(){
		stack = new ArrayList();
	}

	/** @return a hash code value for the object.
     */
	public int hashCode() { return stack.hashCode(); }

	/**
	 * Returns true if and only if this OperandStack
	 * equals another, meaning equal lengths and equal
	 * objects on the stacks.
	 */
	public boolean equals(Object o){
		if (!(o instanceof OperandStack)) {
            return false;
        }
		OperandStack s = (OperandStack) o;
		return this.stack.equals(s.stack);
	}

	/**
	 * Returns a (typed!) clone of this.
	 *
	 * @see #clone()
	 */
	public OperandStack getClone(){
		return (OperandStack) this.clone();
	}

	/**
	 * Returns true IFF this OperandStack is empty.
   */
	public boolean isEmpty(){
		return stack.isEmpty();
	}

	/**
	 * Returns the number of stack slots this stack can hold.
	 */
	public int maxStack(){
		return this.maxStack;
	}

	/**
	 * Returns the element on top of the stack. The element is not popped off the stack!
	 */
	public Type peek(){
		return peek(0);
	}

	/**
   * Returns the element that's i elements below the top element; that means,
   * iff i==0 the top element is returned. The element is not popped off the stack!
   */
	public Type peek(int i){
		return (Type) stack.get(size()-i-1);
	}

	/**
	 * Returns the element on top of the stack. The element is popped off the stack.
	 */
	public Type pop(){
		Type e = (Type) stack.remove(size()-1);
		return e;
	}

	/**
	 * Pops i elements off the stack. ALWAYS RETURNS "null"!!!
	 */
	public Type pop(int i){
		for (int j=0; j<i; j++){
			pop();
		}
		return null;
	}

	/**
	 * Pushes a Type object onto the stack.
	 */
	public void push(Type type){
		if (type == null) {
            throw new AssertionViolatedException("Cannot push NULL onto OperandStack.");
        }
		if (type == Type.BOOLEAN || type == Type.CHAR || type == Type.BYTE || type == Type.SHORT){
			throw new AssertionViolatedException("The OperandStack does not know about '"+type+"'; use Type.INT instead.");
		}
		if (slotsUsed() >= maxStack){
			throw new AssertionViolatedException("OperandStack too small, should have thrown proper Exception elsewhere. Stack: "+this);
		}
		stack.add(type);
	}

	/**
	 * Returns the size of this OperandStack; that means, how many Type objects there are.
	 */
	public int size(){
		return stack.size();
	}

	/**
	 * Returns the number of stack slots used.
	 * @see #maxStack()
	 */	
	public int slotsUsed(){
		/*  XXX change this to a better implementation using a variable
		    that keeps track of the actual slotsUsed()-value monitoring
		    all push()es and pop()s.
		*/
		int slots = 0;
		for (int i=0; i<stack.size(); i++){
			slots += peek(i).getSize();
		}
		return slots;
	}
	
	/**
	 * Returns a String representation of this OperandStack instance.
	 */
	public String toString(){
		StringBuffer sb = new StringBuffer();
		sb.append("Slots used: ");
		sb.append(slotsUsed());
		sb.append(" MaxStack: ");
		sb.append(maxStack);
		sb.append(".\n");
		for (int i=0; i<size(); i++){
			sb.append(peek(i));
			sb.append(" (Size: ");
			sb.append(String.valueOf(peek(i).getSize()));
			sb.append(")\n");
		}
		return sb.toString();
	}

	/**
	 * Merges another stack state into this instance's stack state.
	 * See the Java Virtual Machine Specification, Second Edition, page 146: 4.9.2
	 * for details.
	 */
	public void merge(OperandStack s){
	    try {
		if ( (slotsUsed() != s.slotsUsed()) || (size() != s.size()) ) {
            throw new StructuralCodeConstraintException("Cannot merge stacks of different size:\nOperandStack A:\n"+this+"\nOperandStack B:\n"+s);
        }
		
		for (int i=0; i<size(); i++){
			// If the object _was_ initialized and we're supposed to merge
			// in some uninitialized object, we reject the code (see vmspec2, 4.9.4, last paragraph).
			if ( (! (stack.get(i) instanceof UninitializedObjectType)) && (s.stack.get(i) instanceof UninitializedObjectType) ){
				throw new StructuralCodeConstraintException("Backwards branch with an uninitialized object on the stack detected.");
			}
			// Even harder, we're not initialized but are supposed to broaden
			// the known object type
			if ( (!(stack.get(i).equals(s.stack.get(i)))) && (stack.get(i) instanceof UninitializedObjectType) && (!(s.stack.get(i) instanceof UninitializedObjectType))){
				throw new StructuralCodeConstraintException("Backwards branch with an uninitialized object on the stack detected.");
			}
			// on the other hand...
			if (stack.get(i) instanceof UninitializedObjectType){ //if we have an uninitialized object here
				if (! (s.stack.get(i) instanceof UninitializedObjectType)){ //that has been initialized by now
					stack.set(i, ((UninitializedObjectType) (stack.get(i))).getInitialized() ); //note that.
				}
			}
			if (! stack.get(i).equals(s.stack.get(i))){
				if (	(stack.get(i) instanceof ReferenceType) &&
							(s.stack.get(i) instanceof ReferenceType)  ){
					stack.set(i, ((ReferenceType) stack.get(i)).getFirstCommonSuperclass((ReferenceType) (s.stack.get(i))));
				}
				else{
					throw new StructuralCodeConstraintException("Cannot merge stacks of different types:\nStack A:\n"+this+"\nStack B:\n"+s);
				}
			}
		}
	    } catch (ClassNotFoundException e) {
		// FIXME: maybe not the best way to handle this
		throw new AssertionViolatedException("Missing class: " + e.toString(), e);
	    }
	}

	/**
	 * Replaces all occurences of u in this OperandStack instance
	 * with an "initialized" ObjectType.
	 */
	public void initializeObject(UninitializedObjectType u){
		for (int i=0; i<stack.size(); i++){
			if (stack.get(i) == u){
				stack.set(i, u.getInitialized());
			}
		}
	}

}
