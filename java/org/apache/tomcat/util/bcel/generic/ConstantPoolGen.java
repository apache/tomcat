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
import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.classfile.Constant;
import org.apache.tomcat.util.bcel.classfile.ConstantCP;
import org.apache.tomcat.util.bcel.classfile.ConstantClass;
import org.apache.tomcat.util.bcel.classfile.ConstantDouble;
import org.apache.tomcat.util.bcel.classfile.ConstantFieldref;
import org.apache.tomcat.util.bcel.classfile.ConstantFloat;
import org.apache.tomcat.util.bcel.classfile.ConstantInteger;
import org.apache.tomcat.util.bcel.classfile.ConstantInterfaceMethodref;
import org.apache.tomcat.util.bcel.classfile.ConstantLong;
import org.apache.tomcat.util.bcel.classfile.ConstantMethodref;
import org.apache.tomcat.util.bcel.classfile.ConstantNameAndType;
import org.apache.tomcat.util.bcel.classfile.ConstantPool;
import org.apache.tomcat.util.bcel.classfile.ConstantString;
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
    private static final String METHODREF_DELIM = ":";
    private static final String IMETHODREF_DELIM = "#";
    private static final String FIELDREF_DELIM = "&";
    private static final String NAT_DELIM = "%";

    private static class Index implements java.io.Serializable {

        int index;


        Index(int i) {
            index = i;
        }
    }


    /**
     * Initialize with given array of constants.
     *
     * @param cs array of given constants, new ones will be appended
     */
    public ConstantPoolGen(Constant[] cs) {
    	StringBuffer sb = new StringBuffer(256);
        
        size = Math.max(256, cs.length + 64);
        constants = new Constant[size];
        
        System.arraycopy(cs, 0, constants, 0, cs.length);
        if (cs.length > 0) {
            index = cs.length;
        }
    	
    	
        for (int i = 1; i < index; i++) {
            Constant c = constants[i];
            if (c instanceof ConstantString) {
                ConstantString s = (ConstantString) c;
                ConstantUtf8 u8 = (ConstantUtf8) constants[s.getStringIndex()];
                String key = u8.getBytes();
                if (!string_table.containsKey(key)) {
                    string_table.put(key, new Index(i));
                }
            } else if (c instanceof ConstantClass) {
                ConstantClass s = (ConstantClass) c;
                ConstantUtf8 u8 = (ConstantUtf8) constants[s.getNameIndex()];
                String key = u8.getBytes();
                if (!class_table.containsKey(key)) {
                    class_table.put(key, new Index(i));
                }
            } else if (c instanceof ConstantNameAndType) {
                ConstantNameAndType n = (ConstantNameAndType) c;
                ConstantUtf8 u8 = (ConstantUtf8) constants[n.getNameIndex()];
                ConstantUtf8 u8_2 = (ConstantUtf8) constants[n.getSignatureIndex()];
                
                sb.append(u8.getBytes());
                sb.append(NAT_DELIM);
                sb.append(u8_2.getBytes());
                String key = sb.toString();
                sb.delete(0, sb.length());
                
                if (!n_a_t_table.containsKey(key)) {
                    n_a_t_table.put(key, new Index(i));
                }
            } else if (c instanceof ConstantUtf8) {
                ConstantUtf8 u = (ConstantUtf8) c;
                String key = u.getBytes();
                if (!utf8_table.containsKey(key)) {
                    utf8_table.put(key, new Index(i));
                }
            } else if (c instanceof ConstantCP) {
                ConstantCP m = (ConstantCP) c;
                ConstantClass clazz = (ConstantClass) constants[m.getClassIndex()];
                ConstantNameAndType n = (ConstantNameAndType) constants[m.getNameAndTypeIndex()];
                ConstantUtf8 u8 = (ConstantUtf8) constants[clazz.getNameIndex()];
                String class_name = u8.getBytes().replace('/', '.');
                u8 = (ConstantUtf8) constants[n.getNameIndex()];
                String method_name = u8.getBytes();
                u8 = (ConstantUtf8) constants[n.getSignatureIndex()];
                String signature = u8.getBytes();
                String delim = METHODREF_DELIM;
                if (c instanceof ConstantInterfaceMethodref) {
                    delim = IMETHODREF_DELIM;
                } else if (c instanceof ConstantFieldref) {
                    delim = FIELDREF_DELIM;
                }
                
                sb.append(class_name);
                sb.append(delim);
                sb.append(method_name);
                sb.append(delim);
                sb.append(signature);
                String key = sb.toString();
                sb.delete(0, sb.length());
                
                if (!cp_table.containsKey(key)) {
                    cp_table.put(key, new Index(i));
                }
            }
        }
    }


    /**
     * Initialize with given constant pool.
     */
    public ConstantPoolGen(ConstantPool cp) {
        this(cp.getConstantPool());
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

    private Map string_table = new HashMap();


    /** 
     * Look for ConstantString in ConstantPool containing String `str'.
     *
     * @param str String to search for
     * @return index on success, -1 otherwise
     */
    public int lookupString( String str ) {
        Index index = (Index) string_table.get(str);
        return (index != null) ? index.index : -1;
    }


    /**
     * Add a new String constant to the ConstantPool, if it is not already in there.
     *
     * @param str String to add
     * @return index of entry
     */
    public int addString( String str ) {
        int ret;
        if ((ret = lookupString(str)) != -1) {
            return ret; // Already in CP
        }
        int utf8 = addUtf8(str);
        adjustSize();
        ConstantString s = new ConstantString(utf8);
        ret = index;
        constants[index++] = s;
        if (!string_table.containsKey(str)) {
            string_table.put(str, new Index(ret));
        }
        return ret;
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


    private int addClass_( String clazz ) {
        int ret;
        if ((ret = lookupClass(clazz)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        ConstantClass c = new ConstantClass(addUtf8(clazz));
        ret = index;
        constants[index++] = c;
        if (!class_table.containsKey(clazz)) {
            class_table.put(clazz, new Index(ret));
        }
        return ret;
    }


    /**
     * Add a new Class reference to the ConstantPool, if it is not already in there.
     *
     * @param str Class to add
     * @return index of entry
     */
    public int addClass( String str ) {
        return addClass_(str.replace('.', '/'));
    }


    /**
     * Add a new Class reference to the ConstantPool for a given type.
     *
     * @param type Class to add
     * @return index of entry
     */
    public int addClass( ObjectType type ) {
        return addClass(type.getClassName());
    }


    /**
     * Add a reference to an array class (e.g. String[][]) as needed by MULTIANEWARRAY
     * instruction, e.g. to the ConstantPool.
     *
     * @param type type of array class
     * @return index of entry
     */
    public int addArrayClass( ArrayType type ) {
        return addClass_(type.getSignature());
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
     * Add a new NameAndType constant to the ConstantPool if it is not already 
     * in there.
     *
     * @param name Name string to add
     * @param signature signature string to add
     * @return index of entry
     */
    public int addNameAndType( String name, String signature ) {
        int ret;
        int name_index, signature_index;
        if ((ret = lookupNameAndType(name, signature)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        name_index = addUtf8(name);
        signature_index = addUtf8(signature);
        ret = index;
        constants[index++] = new ConstantNameAndType(name_index, signature_index);
        String key = name + NAT_DELIM + signature;
        if (!n_a_t_table.containsKey(key)) {
            n_a_t_table.put(key, new Index(ret));
        }
        return ret;
    }

    private Map cp_table = new HashMap();


    /** 
     * Look for ConstantMethodref in ConstantPool.
     *
     * @param class_name Where to find method
     * @param method_name Guess what
     * @param signature return and argument types
     * @return index on success, -1 otherwise
     */
    public int lookupMethodref( String class_name, String method_name, String signature ) {
        Index index = (Index) cp_table.get(class_name + METHODREF_DELIM + method_name
                + METHODREF_DELIM + signature);
        return (index != null) ? index.index : -1;
    }


    public int lookupMethodref( MethodGen method ) {
        return lookupMethodref(method.getClassName(), method.getName(), method.getSignature());
    }


    /**
     * Add a new Methodref constant to the ConstantPool, if it is not already 
     * in there.
     *
     * @param class_name class name string to add
     * @param method_name method name string to add
     * @param signature method signature string to add
     * @return index of entry
     */
    public int addMethodref( String class_name, String method_name, String signature ) {
        int ret, class_index, name_and_type_index;
        if ((ret = lookupMethodref(class_name, method_name, signature)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        name_and_type_index = addNameAndType(method_name, signature);
        class_index = addClass(class_name);
        ret = index;
        constants[index++] = new ConstantMethodref(class_index, name_and_type_index);
        String key = class_name + METHODREF_DELIM + method_name + METHODREF_DELIM + signature;
        if (!cp_table.containsKey(key)) {
            cp_table.put(key, new Index(ret));
        }
        return ret;
    }


    public int addMethodref( MethodGen method ) {
        return addMethodref(method.getClassName(), method.getName(), method.getSignature());
    }


    /** 
     * Look for ConstantInterfaceMethodref in ConstantPool.
     *
     * @param class_name Where to find method
     * @param method_name Guess what
     * @param signature return and argument types
     * @return index on success, -1 otherwise
     */
    public int lookupInterfaceMethodref( String class_name, String method_name, String signature ) {
        Index index = (Index) cp_table.get(class_name + IMETHODREF_DELIM + method_name
                + IMETHODREF_DELIM + signature);
        return (index != null) ? index.index : -1;
    }


    public int lookupInterfaceMethodref( MethodGen method ) {
        return lookupInterfaceMethodref(method.getClassName(), method.getName(), method
                .getSignature());
    }


    /**
     * Add a new InterfaceMethodref constant to the ConstantPool, if it is not already 
     * in there.
     *
     * @param class_name class name string to add
     * @param method_name method name string to add
     * @param signature signature string to add
     * @return index of entry
     */
    public int addInterfaceMethodref( String class_name, String method_name, String signature ) {
        int ret, class_index, name_and_type_index;
        if ((ret = lookupInterfaceMethodref(class_name, method_name, signature)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        class_index = addClass(class_name);
        name_and_type_index = addNameAndType(method_name, signature);
        ret = index;
        constants[index++] = new ConstantInterfaceMethodref(class_index, name_and_type_index);
        String key = class_name + IMETHODREF_DELIM + method_name + IMETHODREF_DELIM + signature;
        if (!cp_table.containsKey(key)) {
            cp_table.put(key, new Index(ret));
        }
        return ret;
    }


    public int addInterfaceMethodref( MethodGen method ) {
        return addInterfaceMethodref(method.getClassName(), method.getName(), method.getSignature());
    }


    /** 
     * Look for ConstantFieldref in ConstantPool.
     *
     * @param class_name Where to find method
     * @param field_name Guess what
     * @param signature return and argument types
     * @return index on success, -1 otherwise
     */
    public int lookupFieldref( String class_name, String field_name, String signature ) {
        Index index = (Index) cp_table.get(class_name + FIELDREF_DELIM + field_name
                + FIELDREF_DELIM + signature);
        return (index != null) ? index.index : -1;
    }


    /**
     * Add a new Fieldref constant to the ConstantPool, if it is not already 
     * in there.
     *
     * @param class_name class name string to add
     * @param field_name field name string to add
     * @param signature signature string to add
     * @return index of entry
     */
    public int addFieldref( String class_name, String field_name, String signature ) {
        int ret;
        int class_index, name_and_type_index;
        if ((ret = lookupFieldref(class_name, field_name, signature)) != -1) {
            return ret; // Already in CP
        }
        adjustSize();
        class_index = addClass(class_name);
        name_and_type_index = addNameAndType(field_name, signature);
        ret = index;
        constants[index++] = new ConstantFieldref(class_index, name_and_type_index);
        String key = class_name + FIELDREF_DELIM + field_name + FIELDREF_DELIM + signature;
        if (!cp_table.containsKey(key)) {
            cp_table.put(key, new Index(ret));
        }
        return ret;
    }


    /**
     * @param i index in constant pool
     * @return constant pool entry at index i
     */
    public Constant getConstant( int i ) {
        return constants[i];
    }


    /**
     * Use with care!
     *
     * @param i index in constant pool
     * @param c new constant pool entry at index i
     */
    public void setConstant( int i, Constant c ) {
        constants[i] = c;
    }


    /**
     * @return intermediate constant pool
     */
    public ConstantPool getConstantPool() {
        return new ConstantPool(constants);
    }


    /**
     * @return current size of constant pool
     */
    public int getSize() {
        return index;
    }


    /**
     * @return constant pool with proper length
     */
    public ConstantPool getFinalConstantPool() {
        Constant[] cs = new Constant[index];
        System.arraycopy(constants, 0, cs, 0, index);
        return new ConstantPool(cs);
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


    /** Import constant from another ConstantPool and return new index.
     */
    public int addConstant( Constant c, ConstantPoolGen cp ) {
        Constant[] constants = cp.getConstantPool().getConstantPool();
        switch (c.getTag()) {
            case Constants.CONSTANT_String: {
                ConstantString s = (ConstantString) c;
                ConstantUtf8 u8 = (ConstantUtf8) constants[s.getStringIndex()];
                return addString(u8.getBytes());
            }
            case Constants.CONSTANT_Class: {
                ConstantClass s = (ConstantClass) c;
                ConstantUtf8 u8 = (ConstantUtf8) constants[s.getNameIndex()];
                return addClass(u8.getBytes());
            }
            case Constants.CONSTANT_NameAndType: {
                ConstantNameAndType n = (ConstantNameAndType) c;
                ConstantUtf8 u8 = (ConstantUtf8) constants[n.getNameIndex()];
                ConstantUtf8 u8_2 = (ConstantUtf8) constants[n.getSignatureIndex()];
                return addNameAndType(u8.getBytes(), u8_2.getBytes());
            }
            case Constants.CONSTANT_Utf8:
                return addUtf8(((ConstantUtf8) c).getBytes());
            case Constants.CONSTANT_Double:
                return addDouble(((ConstantDouble) c).getBytes());
            case Constants.CONSTANT_Float:
                return addFloat(((ConstantFloat) c).getBytes());
            case Constants.CONSTANT_Long:
                return addLong(((ConstantLong) c).getBytes());
            case Constants.CONSTANT_Integer:
                return addInteger(((ConstantInteger) c).getBytes());
            case Constants.CONSTANT_InterfaceMethodref:
            case Constants.CONSTANT_Methodref:
            case Constants.CONSTANT_Fieldref: {
                ConstantCP m = (ConstantCP) c;
                ConstantClass clazz = (ConstantClass) constants[m.getClassIndex()];
                ConstantNameAndType n = (ConstantNameAndType) constants[m.getNameAndTypeIndex()];
                ConstantUtf8 u8 = (ConstantUtf8) constants[clazz.getNameIndex()];
                String class_name = u8.getBytes().replace('/', '.');
                u8 = (ConstantUtf8) constants[n.getNameIndex()];
                String name = u8.getBytes();
                u8 = (ConstantUtf8) constants[n.getSignatureIndex()];
                String signature = u8.getBytes();
                switch (c.getTag()) {
                    case Constants.CONSTANT_InterfaceMethodref:
                        return addInterfaceMethodref(class_name, name, signature);
                    case Constants.CONSTANT_Methodref:
                        return addMethodref(class_name, name, signature);
                    case Constants.CONSTANT_Fieldref:
                        return addFieldref(class_name, name, signature);
                    default: // Never reached
                        throw new RuntimeException("Unknown constant type " + c);
                }
            }
            default: // Never reached
                throw new RuntimeException("Unknown constant type " + c);
        }
    }
}
