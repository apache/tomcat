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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.classfile.AnnotationEntry;
import org.apache.tomcat.util.bcel.classfile.Annotations;
import org.apache.tomcat.util.bcel.classfile.Attribute;
import org.apache.tomcat.util.bcel.classfile.Constant;
import org.apache.tomcat.util.bcel.classfile.ConstantObject;
import org.apache.tomcat.util.bcel.classfile.ConstantPool;
import org.apache.tomcat.util.bcel.classfile.ConstantValue;
import org.apache.tomcat.util.bcel.classfile.Field;
import org.apache.tomcat.util.bcel.classfile.Utility;
import org.apache.tomcat.util.bcel.util.BCELComparator;

/** 
 * Template class for building up a field.  The only extraordinary thing
 * one can do is to add a constant value attribute to a field (which must of
 * course be compatible with to the declared type).
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 * @see Field
 */
public class FieldGen extends FieldGenOrMethodGen {

    private Object value = null;
    private static BCELComparator _cmp = new BCELComparator() {

        public boolean equals( Object o1, Object o2 ) {
            FieldGen THIS = (FieldGen) o1;
            FieldGen THAT = (FieldGen) o2;
            return THIS.getName().equals(THAT.getName())
                    && THIS.getSignature().equals(THAT.getSignature());
        }


        public int hashCode( Object o ) {
            FieldGen THIS = (FieldGen) o;
            return THIS.getSignature().hashCode() ^ THIS.getName().hashCode();
        }
    };


    /**
     * Declare a field. If it is static (isStatic() == true) and has a
     * basic type like int or String it may have an initial value
     * associated with it as defined by setInitValue().
     *
     * @param access_flags access qualifiers
     * @param type  field type
     * @param name field name
     * @param cp constant pool
     */
    public FieldGen(int access_flags, Type type, String name, ConstantPoolGen cp) {
        setAccessFlags(access_flags);
        setType(type);
        setName(name);
        setConstantPool(cp);
    }


    /**
     * Instantiate from existing field.
     *
     * @param field Field object
     * @param cp constant pool (must contain the same entries as the field's constant pool)
     */
    public FieldGen(Field field, ConstantPoolGen cp) {
        this(field.getAccessFlags(), Type.getType(field.getSignature()), field.getName(), cp);
        Attribute[] attrs = field.getAttributes();
        for (int i = 0; i < attrs.length; i++) {
            if (attrs[i] instanceof ConstantValue) {
                setValue(((ConstantValue) attrs[i]).getConstantValueIndex());
            } else if (attrs[i] instanceof Annotations) {
            	Annotations runtimeAnnotations = (Annotations)attrs[i];
        		AnnotationEntry[] annotationEntries = runtimeAnnotations.getAnnotationEntries();
        		for (int j = 0; j < annotationEntries.length; j++) {
        			AnnotationEntry element = annotationEntries[j];
        			addAnnotationEntry(new AnnotationEntryGen(element,cp,false));
        		}
            } else {
                addAttribute(attrs[i]);
            }
        }
    }


    private void setValue( int index ) {
        ConstantPool cp = this.cp.getConstantPool();
        Constant c = cp.getConstant(index);
        value = ((ConstantObject) c).getConstantValue(cp);
    }


    /**
     * Set (optional) initial value of field, otherwise it will be set to null/0/false
     * by the JVM automatically.
     */
    public void setInitValue( String str ) {
        checkType(new ObjectType("java.lang.String"));
        if (str != null) {
            value = str;
        }
    }


    public void setInitValue( long l ) {
        checkType(Type.LONG);
        if (l != 0L) {
            value = new Long(l);
        }
    }


    public void setInitValue( int i ) {
        checkType(Type.INT);
        if (i != 0) {
            value = new Integer(i);
        }
    }


    public void setInitValue( short s ) {
        checkType(Type.SHORT);
        if (s != 0) {
            value = new Integer(s);
        }
    }


    public void setInitValue( char c ) {
        checkType(Type.CHAR);
        if (c != 0) {
            value = new Integer(c);
        }
    }


    public void setInitValue( byte b ) {
        checkType(Type.BYTE);
        if (b != 0) {
            value = new Integer(b);
        }
    }


    public void setInitValue( boolean b ) {
        checkType(Type.BOOLEAN);
        if (b) {
            value = new Integer(1);
        }
    }


    public void setInitValue( float f ) {
        checkType(Type.FLOAT);
        if (f != 0.0) {
            value = new Float(f);
        }
    }


    public void setInitValue( double d ) {
        checkType(Type.DOUBLE);
        if (d != 0.0) {
            value = new Double(d);
        }
    }


    /** Remove any initial value.
     */
    public void cancelInitValue() {
        value = null;
    }


    private void checkType( Type atype ) {
        if (type == null) {
            throw new ClassGenException("You haven't defined the type of the field yet");
        }
        if (!isFinal()) {
            throw new ClassGenException("Only final fields may have an initial value!");
        }
        if (!type.equals(atype)) {
            throw new ClassGenException("Types are not compatible: " + type + " vs. " + atype);
        }
    }


    /**
     * Get field object after having set up all necessary values.
     */
    public Field getField() {
        String signature = getSignature();
        int name_index = cp.addUtf8(name);
        int signature_index = cp.addUtf8(signature);
        if (value != null) {
            checkType(type);
            int index = addConstant();
            addAttribute(new ConstantValue(cp.addUtf8("ConstantValue"), 2, index, cp
                    .getConstantPool()));
        }
        addAnnotationsAsAttribute(cp);
        return new Field(access_flags, name_index, signature_index, getAttributes(), cp
                .getConstantPool());
    }
    
    private void addAnnotationsAsAttribute(ConstantPoolGen cp) {
      	Attribute[] attrs = Utility.getAnnotationAttributes(cp,annotation_vec);
        for (int i = 0; i < attrs.length; i++) {
    		addAttribute(attrs[i]);
    	}
      }


    private int addConstant() {
        switch (type.getType()) {
            case Constants.T_INT:
            case Constants.T_CHAR:
            case Constants.T_BYTE:
            case Constants.T_BOOLEAN:
            case Constants.T_SHORT:
                return cp.addInteger(((Integer) value).intValue());
            case Constants.T_FLOAT:
                return cp.addFloat(((Float) value).floatValue());
            case Constants.T_DOUBLE:
                return cp.addDouble(((Double) value).doubleValue());
            case Constants.T_LONG:
                return cp.addLong(((Long) value).longValue());
            case Constants.T_REFERENCE:
                return cp.addString(((String) value));
            default:
                throw new RuntimeException("Oops: Unhandled : " + type.getType());
        }
    }


    public String getSignature() {
        return type.getSignature();
    }

    private List observers;


    /** Add observer for this object.
     */
    public void addObserver( FieldObserver o ) {
        if (observers == null) {
            observers = new ArrayList();
        }
        observers.add(o);
    }


    /** Remove observer for this object.
     */
    public void removeObserver( FieldObserver o ) {
        if (observers != null) {
            observers.remove(o);
        }
    }


    /** Call notify() method on all observers. This method is not called
     * automatically whenever the state has changed, but has to be
     * called by the user after he has finished editing the object.
     */
    public void update() {
        if (observers != null) {
            for (Iterator e = observers.iterator(); e.hasNext();) {
                ((FieldObserver) e.next()).notify(this);
            }
        }
    }


    public String getInitValue() {
        if (value != null) {
            return value.toString();
        } else {
            return null;
        }
    }


    /**
     * Return string representation close to declaration format,
     * `public static final short MAX = 100', e.g..
     *
     * @return String representation of field
     */
    public final String toString() {
        String name, signature, access; // Short cuts to constant pool
        access = Utility.accessToString(access_flags);
        access = access.equals("") ? "" : (access + " ");
        signature = type.toString();
        name = getName();
        StringBuffer buf = new StringBuffer(32);
        buf.append(access).append(signature).append(" ").append(name);
        String value = getInitValue();
        if (value != null) {
            buf.append(" = ").append(value);
        }
        return buf.toString();
    }


    /** @return deep copy of this field
     */
    public FieldGen copy( ConstantPoolGen cp ) {
        FieldGen fg = (FieldGen) clone();
        fg.setConstantPool(cp);
        return fg;
    }


    /**
     * @return Comparison strategy object
     */
    public static BCELComparator getComparator() {
        return _cmp;
    }


    /**
     * @param comparator Comparison strategy object
     */
    public static void setComparator( BCELComparator comparator ) {
        _cmp = comparator;
    }


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default two FieldGen objects are said to be equal when
     * their names and signatures are equal.
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals( Object obj ) {
        return _cmp.equals(this, obj);
    }


    /**
     * Return value as defined by given BCELComparator strategy.
     * By default return the hashcode of the field's name XOR signature.
     * 
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return _cmp.hashCode(this);
    }
}
