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
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.classfile.Attribute;
import org.apache.tomcat.util.bcel.classfile.Signature;

/** 
 * Abstract super class for fields and methods.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class FieldOrMethod extends AccessFlags implements Cloneable, Node {

    protected int name_index; // Points to field name in constant pool 
    protected int signature_index; // Points to encoded signature
    protected int attributes_count; // No. of attributes
    protected Attribute[] attributes; // Collection of attributes
    protected AnnotationEntry[] annotationEntries; // annotations defined on the field or method 
    protected ConstantPool constant_pool;

    private String signatureAttributeString = null;
    private boolean searchedForSignatureAttribute = false;
    

    // Annotations are collected from certain attributes, don't do it more than necessary!
    private boolean annotationsOutOfDate = true;

    FieldOrMethod() {
    }


    /**
     * Initialize from another object. Note that both objects use the same
     * references (shallow copy). Use clone() for a physical copy.
     */
    protected FieldOrMethod(FieldOrMethod c) {
        this(c.getAccessFlags(), c.getNameIndex(), c.getSignatureIndex(), c.getAttributes(), c
                .getConstantPool());
    }


    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     * @throws ClassFormatException
     */
    protected FieldOrMethod(DataInputStream file, ConstantPool constant_pool) throws IOException,
            ClassFormatException {
        this(file.readUnsignedShort(), file.readUnsignedShort(), file.readUnsignedShort(), null,
                constant_pool);
        attributes_count = file.readUnsignedShort();
        attributes = new Attribute[attributes_count];
        for (int i = 0; i < attributes_count; i++) {
            attributes[i] = Attribute.readAttribute(file, constant_pool);
        }
    }


    /**
     * @param access_flags Access rights of method
     * @param name_index Points to field name in constant pool
     * @param signature_index Points to encoded signature
     * @param attributes Collection of attributes
     * @param constant_pool Array of constants
     */
    protected FieldOrMethod(int access_flags, int name_index, int signature_index,
            Attribute[] attributes, ConstantPool constant_pool) {
        this.access_flags = access_flags;
        this.name_index = name_index;
        this.signature_index = signature_index;
        this.constant_pool = constant_pool;
        setAttributes(attributes);
    }


    /**
     * Dump object to file stream on binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    public final void dump( DataOutputStream file ) throws IOException {
        file.writeShort(access_flags);
        file.writeShort(name_index);
        file.writeShort(signature_index);
        file.writeShort(attributes_count);
        for (int i = 0; i < attributes_count; i++) {
            attributes[i].dump(file);
        }
    }


    /**
     * @return Collection of object attributes.
     */
    public final Attribute[] getAttributes() {
        return attributes;
    }


    /**
     * @param attributes Collection of object attributes.
     */
    public final void setAttributes( Attribute[] attributes ) {
        this.attributes = attributes;
        attributes_count = (attributes == null) ? 0 : attributes.length;
    }


    /**
     * @return Constant pool used by this object.
     */
    public final ConstantPool getConstantPool() {
        return constant_pool;
    }


    /**
     * @param constant_pool Constant pool to be used for this object.
     */
    public final void setConstantPool( ConstantPool constant_pool ) {
        this.constant_pool = constant_pool;
    }


    /**
     * @return Index in constant pool of object's name.
     */
    public final int getNameIndex() {
        return name_index;
    }


    /**
     * @param name_index Index in constant pool of object's name.
     */
    public final void setNameIndex( int name_index ) {
        this.name_index = name_index;
    }


    /**
     * @return Index in constant pool of field signature.
     */
    public final int getSignatureIndex() {
        return signature_index;
    }


    /**
     * @param signature_index Index in constant pool of field signature.
     */
    public final void setSignatureIndex( int signature_index ) {
        this.signature_index = signature_index;
    }


    /**
     * @return Name of object, i.e., method name or field name
     */
    public final String getName() {
        ConstantUtf8 c;
        c = (ConstantUtf8) constant_pool.getConstant(name_index, Constants.CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return String representation of object's type signature (java style)
     */
    public final String getSignature() {
        ConstantUtf8 c;
        c = (ConstantUtf8) constant_pool.getConstant(signature_index, Constants.CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return deep copy of this field
     */
    protected FieldOrMethod copy_( ConstantPool _constant_pool ) {
    	FieldOrMethod c = null;

        try {
          c = (FieldOrMethod)clone();
        } catch(CloneNotSupportedException e) {}

        c.constant_pool    = constant_pool;
        c.attributes       = new Attribute[attributes_count];

        for(int i=0; i < attributes_count; i++)
          c.attributes[i] = attributes[i].copy(constant_pool);

        return c;
    }
    
    /**
	 * Ensure we have unpacked any attributes that contain annotations.
	 * We don't remove these annotation attributes from the attributes list, they
	 * remain there.
	 */
	private void ensureAnnotationsUpToDate()
	{
		if (annotationsOutOfDate)
		{
			// Find attributes that contain annotation data
			Attribute[] attrs = getAttributes();
			List accumulatedAnnotations = new ArrayList();
			for (int i = 0; i < attrs.length; i++)
			{
				Attribute attribute = attrs[i];
				if (attribute instanceof Annotations)
				{
					Annotations annotations = (Annotations) attribute;
					for (int j = 0; j < annotations.getAnnotationEntries().length; j++)
					{
						accumulatedAnnotations.add(annotations
								.getAnnotationEntries()[j]);
					}
				}
			}
			annotationEntries = (AnnotationEntry[]) accumulatedAnnotations
					.toArray(new AnnotationEntry[accumulatedAnnotations.size()]);
			annotationsOutOfDate = false;
		}
	}

	public AnnotationEntry[] getAnnotationEntries()
	{
		ensureAnnotationsUpToDate();
		return annotationEntries;
	}

	public void addAnnotationEntry(AnnotationEntry a)
	{
		ensureAnnotationsUpToDate();
		int len = annotationEntries.length;
		AnnotationEntry[] newAnnotations = new AnnotationEntry[len + 1];
		System.arraycopy(annotationEntries, 0, newAnnotations, 0, len);
		newAnnotations[len] = a;
		annotationEntries = newAnnotations;
	}

	/**
	 * Hunts for a signature attribute on the member and returns its contents.  So where the 'regular' signature
	 * may be (Ljava/util/Vector;)V the signature attribute may in fact say 'Ljava/lang/Vector<Ljava/lang/String>;'
	 * Coded for performance - searches for the attribute only when requested - only searches for it once.
	 */
	public final String getGenericSignature()
	{
		if (!searchedForSignatureAttribute)
		{
			boolean found = false;
			for (int i = 0; !found && i < attributes_count; i++)
			{
				if (attributes[i] instanceof Signature)
				{
					signatureAttributeString = ((Signature) attributes[i])
							.getSignature();
					found = true;
				}
			}
			searchedForSignatureAttribute = true;
		}
		return signatureAttributeString;
	}
}
