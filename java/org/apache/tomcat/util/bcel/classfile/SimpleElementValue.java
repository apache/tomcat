/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.bcel.classfile;

import org.apache.tomcat.util.bcel.Const;

public class SimpleElementValue extends ElementValue {
    private final int index;

    SimpleElementValue(final int type, final int index, final ConstantPool cpool) {
        super(type, cpool);
        this.index = index;
    }

    /**
     * @return Value entry index in the cpool
     */
    public int getIndex() {
        return index;
    }


    // Whatever kind of value it is, return it as a string
    @Override
    public String stringifyValue() {
        final ConstantPool cpool = super.getConstantPool();
        final int type = super.getType();
        switch (type) {
        case PRIMITIVE_INT:
            return Integer.toString(cpool.getConstantInteger(getIndex()).getBytes());
        case PRIMITIVE_LONG:
            final ConstantLong j = cpool.getConstant(getIndex(), Const.CONSTANT_Long);
            return Long.toString(j.getBytes());
        case PRIMITIVE_DOUBLE:
            final ConstantDouble d = cpool.getConstant(getIndex(), Const.CONSTANT_Double);
            return Double.toString(d.getBytes());
        case PRIMITIVE_FLOAT:
            final ConstantFloat f = cpool.getConstant(getIndex(), Const.CONSTANT_Float);
            return Float.toString(f.getBytes());
        case PRIMITIVE_SHORT:
            final ConstantInteger s = cpool.getConstantInteger(getIndex());
            return Integer.toString(s.getBytes());
        case PRIMITIVE_BYTE:
            final ConstantInteger b = cpool.getConstantInteger(getIndex());
            return Integer.toString(b.getBytes());
        case PRIMITIVE_CHAR:
            final ConstantInteger ch = cpool.getConstantInteger(getIndex());
            return String.valueOf((char) ch.getBytes());
        case PRIMITIVE_BOOLEAN:
            final ConstantInteger bo = cpool.getConstantInteger(getIndex());
            if (bo.getBytes() == 0) {
                return "false";
            }
            return "true";
        case STRING:
            return cpool.getConstantUtf8(getIndex()).getBytes();
        default:
            throw new IllegalStateException("SimpleElementValue class does not know how to stringify type " + type);
        }
    }
}
