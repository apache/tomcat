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
package org.apache.tomcat.util.bcel.generic;

import java.util.HashMap;
import java.util.Map;
import org.apache.tomcat.util.bcel.classfile.Constant;
import org.apache.tomcat.util.bcel.classfile.ConstantDouble;
import org.apache.tomcat.util.bcel.classfile.ConstantFloat;
import org.apache.tomcat.util.bcel.classfile.ConstantInteger;
import org.apache.tomcat.util.bcel.classfile.ConstantLong;
import org.apache.tomcat.util.bcel.classfile.ConstantPool;
import org.apache.tomcat.util.bcel.classfile.ConstantUtf8;

/** 
 * This class is used to build up a constant pool. The user adds
 * constants via `addXXX' methods, `addString', `addClass',
 * etc.. These methods return an index into the constant
 * pool. Finally, `getFinalConstantPool()' returns the constant pool
 * built up. Intermediate versions of the constant pool can be
 * obtained with `getConstantPool()'. A constant pool has capacity for
 * Constants.MAX_SHORT entries. Note that the first (0) is used by the
 * JVM and that Double and Long constants need two slots.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see Constant
 */
public class ConstantPoolGen implements java.io.Serializable {

    protected int size; 
    protected Constant[] constants;
    protected int index = 1; // First entry (0) used by JVM
    private static final String NAT_DELIM = "%";

    private static class Index implements java.io.Serializable {

        int index;


        Index(int i) {
            index = i;
        }
    }


    


    


    /**
     * Create empty constant pool.
     */
    public ConstantPoolGen() {
    	size = 256;
        constants = new Constant[size];
    }


    /** Resize internal array of constants.
     */
    protected void adjustSize() {
        if (index + 3 >= size) {
            Constant[] cs = constants;
            size *= 2;
            constants = new Constant[size];
            System.arraycopy(cs, 0, constants, 0, index);
        }
    }

    private Map class_table = new HashMap();


    /**
     * Look for ConstantClass in ConstantPool named `str'.
     *
     * @param str String to search for
     * @return index on success, -1 otherwise
     */
    public int lookupClass( String str ) {
        Index index = (Index) class_table.get(str.replace('.', '/'));
        return (index != null) ? index.index : -1;
    }


    /** 
     * Look for ConstantInteger in ConstantPool.
     *
     * @param n integer number to look for
     * @return index on success, -1 otherwise
     */
    public int lookupInteger( int n ) {
        for (int i = 1; i < index; i++) {
            if (constants[i] instanceof ConstantInteger) {
                ConstantInteger c = (ConstantInteger) constants[i];
                if (c.getBytes() == n) {
                    return i;
                }
            }
        }
        return -1;
    }


    /**
     * Add a new Integer constant to the ConstantPool, if it is not already in there.
     *
     * @param n integer number to add
     * @return index of entry
     */
    public int addInteger( int n ) {
        int ret;
        if ((ret = lookupInteger(n)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ret = index;
        constants[index++] = new ConstantInteger(n);
        return ret;
    }


    /** 
     * Look for ConstantFloat in ConstantPool.
     *
     * @param n Float number to look for
     * @return index on success, -1 otherwise
     */
    public int lookupFloat( float n ) {
        int bits = Float.floatToIntBits(n);
        for (int i = 1; i < index; i++) {
            if (constants[i] instanceof ConstantFloat) {
                ConstantFloat c = (ConstantFloat) constants[i];
                if (Float.floatToIntBits(c.getBytes()) == bits) {
                    return i;
                }
            }
        }
        return -1;
    }


    /**
     * Add a new Float constant to the ConstantPool, if it is not already in there.
     *
     * @param n Float number to add
     * @return index of entry
     */
    public int addFloat( float n ) {
        int ret;
        if ((ret = lookupFloat(n)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ret = index;
        constants[index++] = new ConstantFloat(n);
        return ret;
    }

    private Map utf8_table = new HashMap();


    /** 
     * Look for ConstantUtf8 in ConstantPool.
     *
     * @param n Utf8 string to look for
     * @return index on success, -1 otherwise
     */
    public int lookupUtf8( String n ) {
        Index index = (Index) utf8_table.get(n);
        return (index != null) ? index.index : -1;
    }


    /**
     * Add a new Utf8 constant to the ConstantPool, if it is not already in there.
     *
     * @param n Utf8 string to add
     * @return index of entry
     */
    public int addUtf8( String n ) {
        int ret;
        if ((ret = lookupUtf8(n)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ret = index;
        constants[index++] = new ConstantUtf8(n);
        if (!utf8_table.containsKey(n)) {
            utf8_table.put(n, new Index(ret));
        }
        return ret;
    }


    /** 
     * Look for ConstantLong in ConstantPool.
     *
     * @param n Long number to look for
     * @return index on success, -1 otherwise
     */
    public int lookupLong( long n ) {
        for (int i = 1; i < index; i++) {
            if (constants[i] instanceof ConstantLong) {
                ConstantLong c = (ConstantLong) constants[i];
                if (c.getBytes() == n) {
                    return i;
                }
            }
        }
        return -1;
    }


    /**
     * Add a new long constant to the ConstantPool, if it is not already in there.
     *
     * @param n Long number to add
     * @return index of entry
     */
    public int addLong( long n ) {
        int ret;
        if ((ret = lookupLong(n)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ret = index;
        constants[index] = new ConstantLong(n);
        index += 2; // Wastes one entry according to spec
        return ret;
    }


    /** 
     * Look for ConstantDouble in ConstantPool.
     *
     * @param n Double number to look for
     * @return index on success, -1 otherwise
     */
    public int lookupDouble( double n ) {
        long bits = Double.doubleToLongBits(n);
        for (int i = 1; i < index; i++) {
            if (constants[i] instanceof ConstantDouble) {
                ConstantDouble c = (ConstantDouble) constants[i];
                if (Double.doubleToLongBits(c.getBytes()) == bits) {
                    return i;
                }
            }
        }
        return -1;
    }


    /**
     * Add a new double constant to the ConstantPool, if it is not already in there.
     *
     * @param n Double number to add
     * @return index of entry
     */
    public int addDouble( double n ) {
        int ret;
        if ((ret = lookupDouble(n)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ret = index;
        constants[index] = new ConstantDouble(n);
        index += 2; // Wastes one entry according to spec
        return ret;
    }

    private Map n_a_t_table = new HashMap();


    /** 
     * Look for ConstantNameAndType in ConstantPool.
     *
     * @param name of variable/method
     * @param signature of variable/method
     * @return index on success, -1 otherwise
     */
    public int lookupNameAndType( String name, String signature ) {
        Index _index = (Index) n_a_t_table.get(name + NAT_DELIM + signature);
        return (_index != null) ? _index.index : -1;
    }

    
    /**
     * @param i index in constant pool
     * @return constant pool entry at index i
     */
    public Constant getConstant( int i ) {
        return constants[i];
    }


    /**
     * @return intermediate constant pool
     */
    public ConstantPool getConstantPool() {
        return new ConstantPool(constants);
    }


    


    


    /**
     * @return String representation.
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        for (int i = 1; i < index; i++) {
            buf.append(i).append(")").append(constants[i]).append("\n");
        }
        return buf.toString();
    }


    
}
