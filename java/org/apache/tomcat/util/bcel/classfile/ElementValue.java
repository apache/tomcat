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

import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author <A HREF="mailto:dbrosius@qis.net">D. Brosius</A>
 * @since 6.0
 */
public abstract class ElementValue
{
    protected int type;

    protected ConstantPool cpool;


    protected ElementValue(int type, ConstantPool cpool)
    {
        this.type = type;
        this.cpool = cpool;
    }

    public abstract String stringifyValue();

    public static final int STRING = 's';

    public static final int ENUM_CONSTANT = 'e';

    public static final int CLASS = 'c';

    public static final int ANNOTATION = '@';

    public static final int ARRAY = '[';

    public static final int PRIMITIVE_INT = 'I';

    public static final int PRIMITIVE_BYTE = 'B';

    public static final int PRIMITIVE_CHAR = 'C';

    public static final int PRIMITIVE_DOUBLE = 'D';

    public static final int PRIMITIVE_FLOAT = 'F';

    public static final int PRIMITIVE_LONG = 'J';

    public static final int PRIMITIVE_SHORT = 'S';

    public static final int PRIMITIVE_BOOLEAN = 'Z';

    public static ElementValue readElementValue(DataInputStream dis,
            ConstantPool cpool) throws IOException
    {
        byte type = dis.readByte();
        switch (type)
        {
        case 'B': // byte
            return new SimpleElementValue(PRIMITIVE_BYTE, dis
                    .readUnsignedShort(), cpool);
        case 'C': // char
            return new SimpleElementValue(PRIMITIVE_CHAR, dis
                    .readUnsignedShort(), cpool);
        case 'D': // double
            return new SimpleElementValue(PRIMITIVE_DOUBLE, dis
                    .readUnsignedShort(), cpool);
        case 'F': // float
            return new SimpleElementValue(PRIMITIVE_FLOAT, dis
                    .readUnsignedShort(), cpool);
        case 'I': // int
            return new SimpleElementValue(PRIMITIVE_INT, dis
                    .readUnsignedShort(), cpool);
        case 'J': // long
            return new SimpleElementValue(PRIMITIVE_LONG, dis
                    .readUnsignedShort(), cpool);
        case 'S': // short
            return new SimpleElementValue(PRIMITIVE_SHORT, dis
                    .readUnsignedShort(), cpool);
        case 'Z': // boolean
            return new SimpleElementValue(PRIMITIVE_BOOLEAN, dis
                    .readUnsignedShort(), cpool);
        case 's': // String
            return new SimpleElementValue(STRING, dis.readUnsignedShort(),
                    cpool);
        case 'e': // Enum constant
            dis.readUnsignedShort();    // Unused type_index
            return new EnumElementValue(ENUM_CONSTANT,
                    dis.readUnsignedShort(), cpool);
        case 'c': // Class
            return new ClassElementValue(CLASS, dis.readUnsignedShort(), cpool);
        case '@': // Annotation
            // TODO isRuntimeVisible
            return new AnnotationElementValue(ANNOTATION, AnnotationEntry.read(
                    dis, cpool), cpool);
        case '[': // Array
            int numArrayVals = dis.readUnsignedShort();
            ElementValue[] evalues = new ElementValue[numArrayVals];
            for (int j = 0; j < numArrayVals; j++)
            {
                evalues[j] = ElementValue.readElementValue(dis, cpool);
            }
            return new ArrayElementValue(ARRAY, evalues, cpool);
        default:
            throw new RuntimeException(
                    "Unexpected element value kind in annotation: " + type);
        }
    }
}
