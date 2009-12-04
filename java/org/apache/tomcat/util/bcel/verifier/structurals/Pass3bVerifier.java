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


import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.Repository;
import org.apache.tomcat.util.bcel.classfile.JavaClass;
import org.apache.tomcat.util.bcel.classfile.Method;
import org.apache.tomcat.util.bcel.generic.ConstantPoolGen;
import org.apache.tomcat.util.bcel.generic.GETFIELD;
import org.apache.tomcat.util.bcel.generic.InstructionHandle;
import org.apache.tomcat.util.bcel.generic.InvokeInstruction;
import org.apache.tomcat.util.bcel.generic.JsrInstruction;
import org.apache.tomcat.util.bcel.generic.LoadInstruction;
import org.apache.tomcat.util.bcel.generic.MethodGen;
import org.apache.tomcat.util.bcel.generic.ObjectType;
import org.apache.tomcat.util.bcel.generic.RET;
import org.apache.tomcat.util.bcel.generic.ReturnInstruction;
import org.apache.tomcat.util.bcel.generic.ReturnaddressType;
import org.apache.tomcat.util.bcel.generic.Type;
import org.apache.tomcat.util.bcel.verifier.PassVerifier;
import org.apache.tomcat.util.bcel.verifier.VerificationResult;
import org.apache.tomcat.util.bcel.verifier.Verifier;
import org.apache.tomcat.util.bcel.verifier.exc.AssertionViolatedException;
import org.apache.tomcat.util.bcel.verifier.exc.StructuralCodeConstraintException;
import org.apache.tomcat.util.bcel.verifier.exc.VerifierConstraintViolatedException;

/**
 * This PassVerifier verifies a method of class file according to pass 3,
 * so-called structural verification as described in The Java Virtual Machine
 * Specification, 2nd edition.
 * More detailed information is to be found at the do_verify() method's
 * documentation. 
 *
 * @version $Id$
 * @author Enver Haase
 * @see #do_verify()
 */

public final class Pass3bVerifier extends PassVerifier{
	/* TODO:	Throughout pass 3b, upper halves of LONG and DOUBLE
						are represented by Type.UNKNOWN. This should be changed
						in favour of LONG_Upper and DOUBLE_Upper as in pass 2. */

	/**
	 * An InstructionContextQueue is a utility class that holds
	 * (InstructionContext, ArrayList) pairs in a Queue data structure.
	 * This is used to hold information about InstructionContext objects
	 * externally --- i.e. that information is not saved inside the
	 * InstructionContext object itself. This is useful to save the
	 * execution path of the symbolic execution of the
	 * Pass3bVerifier - this is not information
	 * that belongs into the InstructionContext object itself.
	 * Only at "execute()"ing
	 * time, an InstructionContext object will get the current information
	 * we have about its symbolic execution predecessors.
	 */
	private static final class InstructionContextQueue{
		private List ics = new Vector(); // Type: InstructionContext
		private List ecs = new Vector(); // Type: ArrayList (of InstructionContext)
		public void add(InstructionContext ic, ArrayList executionChain){
			ics.add(ic);
			ecs.add(executionChain);
		}
		public boolean isEmpty(){
			return ics.isEmpty();
		}
		public void remove(){
			this.remove(0);
		}
		public void remove(int i){
			ics.remove(i);
			ecs.remove(i);
		}
		public InstructionContext getIC(int i){
			return (InstructionContext) ics.get(i);
		}
		public ArrayList getEC(int i){
			return (ArrayList) ecs.get(i);
		}
		public int size(){
			return ics.size();
		}
	} // end Inner Class InstructionContextQueue

	/** In DEBUG mode, the verification algorithm is not randomized. */
	private static final boolean DEBUG = true;

	/** The Verifier that created this. */
	private Verifier myOwner;

	/** The method number to verify. */
	private int method_no;

	/**
	 * This class should only be instantiated by a Verifier.
	 *
	 * @see org.apache.tomcat.util.bcel.verifier.Verifier
	 */
	public Pass3bVerifier(Verifier owner, int method_no){
		myOwner = owner;
		this.method_no = method_no;
	}

	/**
	 * Whenever the outgoing frame
	 * situation of an InstructionContext changes, all its successors are
	 * put [back] into the queue [as if they were unvisited].
   * The proof of termination is about the existence of a
   * fix point of frame merging.
	 */
	private void circulationPump(MethodGen m,ControlFlowGraph cfg, InstructionContext start, Frame vanillaFrame, InstConstraintVisitor icv, ExecutionVisitor ev){
		final Random random = new Random();
		InstructionContextQueue icq = new InstructionContextQueue();
		
		start.execute(vanillaFrame, new ArrayList(), icv, ev);	// new ArrayList() <=>	no Instruction was executed before
																									//									=> Top-Level routine (no jsr call before)
		icq.add(start, new ArrayList());

		// LOOP!
		while (!icq.isEmpty()){
			InstructionContext u;
			ArrayList ec;
			if (!DEBUG){
				int r = random.nextInt(icq.size());
				u = icq.getIC(r);
				ec = icq.getEC(r);
				icq.remove(r);
			}
			else{
				u  = icq.getIC(0);
				ec = icq.getEC(0);
				icq.remove(0);
			}
			
			ArrayList oldchain = (ArrayList) (ec.clone());
			ArrayList newchain = (ArrayList) (ec.clone());
			newchain.add(u);

			if ((u.getInstruction().getInstruction()) instanceof RET){
//System.err.println(u);
				// We can only follow _one_ successor, the one after the
				// JSR that was recently executed.
				RET ret = (RET) (u.getInstruction().getInstruction());
				ReturnaddressType t = (ReturnaddressType) u.getOutFrame(oldchain).getLocals().get(ret.getIndex());
				InstructionContext theSuccessor = cfg.contextOf(t.getTarget());

				// Sanity check
				InstructionContext lastJSR = null;
				int skip_jsr = 0;
				for (int ss=oldchain.size()-1; ss >= 0; ss--){
					if (skip_jsr < 0){
						throw new AssertionViolatedException("More RET than JSR in execution chain?!");
					}
//System.err.println("+"+oldchain.get(ss));
					if (((InstructionContext) oldchain.get(ss)).getInstruction().getInstruction() instanceof JsrInstruction){
						if (skip_jsr == 0){
							lastJSR = (InstructionContext) oldchain.get(ss);
							break;
						}
						else{
							skip_jsr--;
						}
					}
					if (((InstructionContext) oldchain.get(ss)).getInstruction().getInstruction() instanceof RET){
						skip_jsr++;
					}
				}
				if (lastJSR == null){
					throw new AssertionViolatedException("RET without a JSR before in ExecutionChain?! EC: '"+oldchain+"'.");
				}
				JsrInstruction jsr = (JsrInstruction) (lastJSR.getInstruction().getInstruction());
				if ( theSuccessor != (cfg.contextOf(jsr.physicalSuccessor())) ){
					throw new AssertionViolatedException("RET '"+u.getInstruction()+"' info inconsistent: jump back to '"+theSuccessor+"' or '"+cfg.contextOf(jsr.physicalSuccessor())+"'?");
				}
				
				if (theSuccessor.execute(u.getOutFrame(oldchain), newchain, icv, ev)){
					icq.add(theSuccessor, (ArrayList) newchain.clone());
				}
			}
			else{// "not a ret"
			
				// Normal successors. Add them to the queue of successors.
				InstructionContext[] succs = u.getSuccessors();
				for (int s=0; s<succs.length; s++){
					InstructionContext v = succs[s];
					if (v.execute(u.getOutFrame(oldchain), newchain, icv, ev)){
						icq.add(v, (ArrayList) newchain.clone());
					}
				}
			}// end "not a ret"

			// Exception Handlers. Add them to the queue of successors.
			// [subroutines are never protected; mandated by JustIce]
			ExceptionHandler[] exc_hds = u.getExceptionHandlers();
			for (int s=0; s<exc_hds.length; s++){
				InstructionContext v = cfg.contextOf(exc_hds[s].getHandlerStart());
				// TODO: the "oldchain" and "newchain" is used to determine the subroutine
				// we're in (by searching for the last JSR) by the InstructionContext
				// implementation. Therefore, we should not use this chain mechanism
				// when dealing with exception handlers.
				// Example: a JSR with an exception handler as its successor does not
				// mean we're in a subroutine if we go to the exception handler.
				// We should address this problem later; by now we simply "cut" the chain
				// by using an empty chain for the exception handlers.
				//if (v.execute(new Frame(u.getOutFrame(oldchain).getLocals(), new OperandStack (u.getOutFrame().getStack().maxStack(), (exc_hds[s].getExceptionType()==null? Type.THROWABLE : exc_hds[s].getExceptionType())) ), newchain), icv, ev){
					//icq.add(v, (ArrayList) newchain.clone());
				if (v.execute(new Frame(u.getOutFrame(oldchain).getLocals(), new OperandStack (u.getOutFrame(oldchain).getStack().maxStack(), (exc_hds[s].getExceptionType()==null? Type.THROWABLE : exc_hds[s].getExceptionType())) ), new ArrayList(), icv, ev)){
					icq.add(v, new ArrayList());
				}
			}

		}// while (!icq.isEmpty()) END
		
		InstructionHandle ih = start.getInstruction();
		do{
			if ((ih.getInstruction() instanceof ReturnInstruction) && (!(cfg.isDead(ih)))) {
				InstructionContext ic = cfg.contextOf(ih);
				Frame f = ic.getOutFrame(new ArrayList()); // TODO: This is buggy, we check only the top-level return instructions this way. Maybe some maniac returns from a method when in a subroutine?
				LocalVariables lvs = f.getLocals();
				for (int i=0; i<lvs.maxLocals(); i++){
					if (lvs.get(i) instanceof UninitializedObjectType){
						this.addMessage("Warning: ReturnInstruction '"+ic+"' may leave method with an uninitialized object in the local variables array '"+lvs+"'.");
					}
				}
				OperandStack os = f.getStack();
				for (int i=0; i<os.size(); i++){
					if (os.peek(i) instanceof UninitializedObjectType){
						this.addMessage("Warning: ReturnInstruction '"+ic+"' may leave method with an uninitialized object on the operand stack '"+os+"'.");
					}
				}
                //see JVM $4.8.2
                //TODO implement all based on stack 
                Type returnedType = null;
                if( ih.getPrev().getInstruction() instanceof InvokeInstruction )
                {
                    returnedType = ((InvokeInstruction)ih.getPrev().getInstruction()).getType(m.getConstantPool());
                }
                if( ih.getPrev().getInstruction() instanceof LoadInstruction )
                {
                    int index = ((LoadInstruction)ih.getPrev().getInstruction()).getIndex();
                    returnedType = lvs.get(index);
                }
                if( ih.getPrev().getInstruction() instanceof GETFIELD )
                {
                    returnedType = ((GETFIELD)ih.getPrev().getInstruction()).getType(m.getConstantPool());
                }
                if( returnedType != null )
                {
                    if( returnedType instanceof ObjectType )
                    {
                        try
                        {
                            if( !((ObjectType)returnedType).isAssignmentCompatibleWith(m.getReturnType()) )
                            {
                                throw new StructuralCodeConstraintException("Returned type "+returnedType+" does not match Method's return type "+m.getReturnType());
                            }
                        }
                        catch (ClassNotFoundException e)
                        {
                            //dont know what do do now, so raise RuntimeException
                            throw new RuntimeException(e);
                        }
                    }
                    else if( !returnedType.equals(m.getReturnType()) )
                    {
                        throw new StructuralCodeConstraintException("Returned type "+returnedType+" does not match Method's return type "+m.getReturnType());
                    }
                }
			}
		}while ((ih = ih.getNext()) != null);
		
 	}

	/**
	 * Pass 3b implements the data flow analysis as described in the Java Virtual
	 * Machine Specification, Second Edition.
 	 * Later versions will use LocalVariablesInfo objects to verify if the
 	 * verifier-inferred types and the class file's debug information (LocalVariables
 	 * attributes) match [TODO].
 	 *
 	 * @see org.apache.tomcat.util.bcel.verifier.statics.LocalVariablesInfo
 	 * @see org.apache.tomcat.util.bcel.verifier.statics.Pass2Verifier#getLocalVariablesInfo(int)
 	 */
	public VerificationResult do_verify(){
		if (! myOwner.doPass3a(method_no).equals(VerificationResult.VR_OK)){
			return VerificationResult.VR_NOTYET;
		}

		// Pass 3a ran before, so it's safe to assume the JavaClass object is
		// in the BCEL repository.
		JavaClass jc;
		try {
			jc = Repository.lookupClass(myOwner.getClassName());
		} catch (ClassNotFoundException e) {
			// FIXME: maybe not the best way to handle this
			throw new AssertionViolatedException("Missing class: " + e.toString(), e);
		}

		ConstantPoolGen constantPoolGen = new ConstantPoolGen(jc.getConstantPool());
		// Init Visitors
		InstConstraintVisitor icv = new InstConstraintVisitor();
		icv.setConstantPoolGen(constantPoolGen);
		
		ExecutionVisitor ev = new ExecutionVisitor();
		ev.setConstantPoolGen(constantPoolGen);
		
		Method[] methods = jc.getMethods(); // Method no "method_no" exists, we ran Pass3a before on it!

		try{

			MethodGen mg = new MethodGen(methods[method_no], myOwner.getClassName(), constantPoolGen);

			icv.setMethodGen(mg);
				
			////////////// DFA BEGINS HERE ////////////////
			if (! (mg.isAbstract() || mg.isNative()) ){ // IF mg HAS CODE (See pass 2)
				
				ControlFlowGraph cfg = new ControlFlowGraph(mg);

				// Build the initial frame situation for this method.
				Frame f = new Frame(mg.getMaxLocals(),mg.getMaxStack());
				if ( !mg.isStatic() ){
					if (mg.getName().equals(Constants.CONSTRUCTOR_NAME)){
						Frame._this = new UninitializedObjectType(new ObjectType(jc.getClassName()));
						f.getLocals().set(0, Frame._this);
					}
					else{
						Frame._this = null;
						f.getLocals().set(0, new ObjectType(jc.getClassName()));
					}
				}
				Type[] argtypes = mg.getArgumentTypes();
				int twoslotoffset = 0;
				for (int j=0; j<argtypes.length; j++){
					if (argtypes[j] == Type.SHORT || argtypes[j] == Type.BYTE || argtypes[j] == Type.CHAR || argtypes[j] == Type.BOOLEAN){
						argtypes[j] = Type.INT;
					}
					f.getLocals().set(twoslotoffset + j + (mg.isStatic()?0:1), argtypes[j]);
					if (argtypes[j].getSize() == 2){
						twoslotoffset++;
						f.getLocals().set(twoslotoffset + j + (mg.isStatic()?0:1), Type.UNKNOWN);
					}
				}
				circulationPump(mg,cfg, cfg.contextOf(mg.getInstructionList().getStart()), f, icv, ev);
			}
		}
		catch (VerifierConstraintViolatedException ce){
			ce.extendMessage("Constraint violated in method '"+methods[method_no]+"':\n","");
			return new VerificationResult(VerificationResult.VERIFIED_REJECTED, ce.getMessage());
		}
		catch (RuntimeException re){
			// These are internal errors

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			re.printStackTrace(pw);

			throw new AssertionViolatedException("Some RuntimeException occured while verify()ing class '"+jc.getClassName()+"', method '"+methods[method_no]+"'. Original RuntimeException's stack trace:\n---\n"+sw+"---\n", re);
		}
		return VerificationResult.VR_OK;
	}

	/** Returns the method number as supplied when instantiating. */
	public int getMethodNo(){
		return method_no;
	}
}
