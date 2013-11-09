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
 * This class represents the type of a local variable or item on stack
 * used in the StackMap entries.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     StackMapEntry
 * @see     StackMap
 * @see     Constants
 */
public final class StackMapType implements Cloneable, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    StackMapType(DataInput file) throws IOException {
        byte type = file.readByte();
        if ((type < Constants.ITEM_Bogus) || (type > Constants.ITEM_NewObject)) {
            throw new RuntimeException("Illegal type for StackMapType: " + type);
        }
        // Check to see if type has an index
        if ((type == Constants.ITEM_Object) || (type == Constants.ITEM_NewObject)) {
            file.readShort();   // Unused index
        }
    }
}
