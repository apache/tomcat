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
package org.apache.tomcat.util.bcel.util;

import java.util.Stack;
import org.apache.tomcat.util.bcel.classfile.JavaClass;

/** 
 * Utility class implementing a (typesafe) stack of JavaClass objects.
 *
 * @version $Id$
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A> 
 * @see Stack
 */
public class ClassStack implements java.io.Serializable {

    private Stack stack = new Stack();


    public void push( JavaClass clazz ) {
        stack.push(clazz);
    }


    public JavaClass pop() {
        return (JavaClass) stack.pop();
    }


    public JavaClass top() {
        return (JavaClass) stack.peek();
    }


    public boolean empty() {
        return stack.empty();
    }
}
