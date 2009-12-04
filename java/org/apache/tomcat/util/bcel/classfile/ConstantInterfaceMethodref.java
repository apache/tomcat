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
package org.apache.tomcat.util.bcel.classfile;

import java.io.DataInputStream;
import java.io.IOException;
import org.apache.tomcat.util.bcel.Constants;

/** 
 * This class represents a constant pool reference to an interface method.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public final class ConstantInterfaceMethodref extends ConstantCP {

    /**
     * Initialize from another object.
     */
    public ConstantInterfaceMethodref(ConstantInterfaceMethodref c) {
        super(Constants.CONSTANT_InterfaceMethodref, c.getClassIndex(), c.getNameAndTypeIndex());
    }


    /**
     * Initialize instance from file data.
     *
     * @param file input stream
     * @throws IOException
     */
    ConstantInterfaceMethodref(DataInputStream file) throws IOException {
        super(Constants.CONSTANT_InterfaceMethodref, file);
    }


    /**
     * @param class_index Reference to the class containing the method
     * @param name_and_type_index and the method signature
     */
    public ConstantInterfaceMethodref(int class_index, int name_and_type_index) {
        super(Constants.CONSTANT_InterfaceMethodref, class_index, name_and_type_index);
    }


    /**
     * Called by objects that are traversing the nodes of the tree implicitely
     * defined by the contents of a Java class. I.e., the hierarchy of methods,
     * fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    public void accept( Visitor v ) {
        v.visitConstantInterfaceMethodref(this);
    }
}
