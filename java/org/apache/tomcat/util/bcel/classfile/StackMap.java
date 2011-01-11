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
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.tomcat.util.bcel.Constants;

/**
 * This class represents a stack map attribute used for
 * preverification of Java classes for the <a
 * href="http://java.sun.com/j2me/"> Java 2 Micro Edition</a>
 * (J2ME). This attribute is used by the <a
 * href="http://java.sun.com/products/cldc/">KVM</a> and contained
 * within the Code attribute of a method. See CLDC specification
 * &sect;5.3.1.2
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see     Code
 * @see     StackMapEntry
 * @see     StackMapType
 */
public final class StackMap extends Attribute {

    private static final long serialVersionUID = 264958819110329590L;
    private int map_length;
    private StackMapEntry[] map; // Table of stack map entries


    /*
     * @param name_index Index of name
     * @param length Content length in bytes
     * @param map Table of stack map entries
     * @param constant_pool Array of constants
     */
    public StackMap(int name_index, int length, StackMapEntry[] map, ConstantPool constant_pool) {
        super(Constants.ATTR_STACK_MAP, name_index, length, constant_pool);
        setStackMap(map);
    }


    /**
     * Construct object from file stream.
     * @param name_index Index of name
     * @param length Content length in bytes
     * @param file Input stream
     * @param constant_pool Array of constants
     * @throws IOException
     */
    StackMap(int name_index, int length, DataInputStream file, ConstantPool constant_pool)
            throws IOException {
        this(name_index, length, (StackMapEntry[]) null, constant_pool);
        map_length = file.readUnsignedShort();
        map = new StackMapEntry[map_length];
        for (int i = 0; i < map_length; i++) {
            map[i] = new StackMapEntry(file, constant_pool);
        }
    }


    /**
     * Dump line number table attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    @Override
    public final void dump( DataOutputStream file ) throws IOException {
        super.dump(file);
        file.writeShort(map_length);
        for (int i = 0; i < map_length; i++) {
            map[i].dump(file);
        }
    }


    /**
     * @param map Array of stack map entries
     */
    public final void setStackMap( StackMapEntry[] map ) {
        this.map = map;
        map_length = (map == null) ? 0 : map.length;
    }


    /**
     * @return String representation.
     */
    @Override
    public final String toString() {
        StringBuffer buf = new StringBuffer("StackMap(");
        for (int i = 0; i < map_length; i++) {
            buf.append(map[i].toString());
            if (i < map_length - 1) {
                buf.append(", ");
            }
        }
        buf.append(')');
        return buf.toString();
    }


    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy( ConstantPool _constant_pool ) {
        StackMap c = (StackMap) clone();
        c.map = new StackMapEntry[map_length];
        for (int i = 0; i < map_length; i++) {
            c.map[i] = map[i].copy();
        }
        c.constant_pool = _constant_pool;
        return c;
    }
}
