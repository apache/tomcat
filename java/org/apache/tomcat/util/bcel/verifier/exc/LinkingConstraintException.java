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
 * of a constraint that is usually only verified at run-time (pass 4).
 * The Java Virtual Machine Specification, 2nd edition, states that certain constraints
 * are usually verified at run-time for performance reasons (the verification of those
 * constraints requires loading in and recursively verifying referenced classes) that
 * conceptually belong to pass 3; to be precise, that conceptually belong to the
 * data flow analysis of pass 3 (called pass 3b in JustIce).
 * These are the checks necessary for resolution: Compare pages 142-143 ("4.9.1 The
 * Verification Process") and pages 50-51 ("2.17.3 Linking: Verification, Preparation,
 * and Resolution") of the above mentioned book.
 * <B>TODO: At this time, this class is not used in JustIce.</B>
 *
 * @version $Id$
 * @author Enver Haase
 */
public class LinkingConstraintException extends StructuralCodeConstraintException{
}
