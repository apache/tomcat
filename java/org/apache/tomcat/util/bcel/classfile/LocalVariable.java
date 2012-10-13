/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
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

import java.io.DataInput;
import java.io.IOException;
import java.io.Serializable;

import org.apache.tomcat.util.bcel.Constants;

/**
 * This class represents a local variable within a method. It contains its
 * scope, name, signature and index on the method's frame.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     LocalVariableTable
 */
public final class LocalVariable implements Constants, Cloneable, Serializable {

    private static final long serialVersionUID = -914189896372081589L;
    private int index; /* Variable is `index'th local variable on
     * this method's frame.
     */


    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    LocalVariable(DataInput file) throws IOException {
        file.readUnsignedShort();
        file.readUnsignedShort();
        file.readUnsignedShort();
        file.readUnsignedShort();
        this.index = file.readUnsignedShort();
    }


    /**
     * @return index of register where variable is stored
     */
    public final int getIndex() {
        return index;
    }


    /**
     * @return deep copy of this object
     */
    public LocalVariable copy() {
        try {
            return (LocalVariable) clone();
        } catch (CloneNotSupportedException e) {
        }
        return null;
    }
}
