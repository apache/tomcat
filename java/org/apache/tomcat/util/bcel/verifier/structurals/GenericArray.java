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
 * A placeholder class that can be used to create an ObjectType of which
 * has some of the properties arrays have. They implement java.lang.Cloneable
 * and java.io.Serializable and they extend java.lang.Object.
 *
 * @version $Id$
 * @author Enver Haase
 */ 
public class GenericArray extends java.lang.Object implements java.lang.Cloneable, java.io.Serializable{
	
	protected Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
}
