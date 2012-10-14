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
 * This class represents an entry in the exception table of the <em>Code</em>
 * attribute and is used only there. It contains a range in which a
 * particular exception handler is active.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Code
 */
public final class CodeException implements Cloneable, Constants, Serializable {

    private static final long serialVersionUID = -6351674720658890686L;


    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    CodeException(DataInput file) throws IOException {
        file.readUnsignedShort();   // Unused start_pc
        file.readUnsignedShort();   // Unused end_pc
        file.readUnsignedShort();   // Unused handler_pc
        file.readUnsignedShort();   // Unused catch_type
    }
}
