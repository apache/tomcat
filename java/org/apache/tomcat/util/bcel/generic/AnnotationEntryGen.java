package org.apache.tomcat.util.bcel.generic;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.tomcat.util.bcel.classfile.AnnotationEntry;
import org.apache.tomcat.util.bcel.classfile.ConstantUtf8;
import org.apache.tomcat.util.bcel.classfile.ElementValuePair;


public class AnnotationEntryGen
{
	private int typeIndex;

	private List /* ElementNameValuePairGen */evs;

	private ConstantPoolGen cpool;

	private boolean isRuntimeVisible = false;

	/**
	 * Here we are taking a fixed annotation of type Annotation and building a
	 * modifiable AnnotationGen object. If the pool passed in is for a different
	 * class file, then copyPoolEntries should have been passed as true as that
	 * will force us to do a deep copy of the annotation and move the cpool
	 * entries across. We need to copy the type and the element name value pairs
	 * and the visibility.
	 */
	public AnnotationEntryGen(AnnotationEntry a, ConstantPoolGen cpool,
			boolean copyPoolEntries)
	{
		this.cpool = cpool;
		if (copyPoolEntries)
		{
			typeIndex = cpool.addUtf8(a.getAnnotationType());
		}
		else
		{
			typeIndex = a.getAnnotationTypeIndex();
		}
		isRuntimeVisible = a.isRuntimeVisible();
		evs = copyValues(a.getElementValuePairs(), cpool, copyPoolEntries);
	}

	private List copyValues(ElementValuePair[] in, ConstantPoolGen cpool,
			boolean copyPoolEntries)
	{
		List out = new ArrayList();
		int l = in.length;
		for (int i = 0; i < l; i++)
		{
			ElementValuePair nvp = (ElementValuePair) in[i];
			out.add(new ElementValuePairGen(nvp, cpool, copyPoolEntries));
		}
		return out;
	}

	/**
	 * Retrieve an immutable version of this AnnotationGen
	 */
	public AnnotationEntry getAnnotation()
	{
		AnnotationEntry a = new AnnotationEntry(typeIndex, cpool.getConstantPool(),
				isRuntimeVisible);
		for (Iterator iter = evs.iterator(); iter.hasNext();)
		{
			ElementValuePairGen element = (ElementValuePairGen) iter
					.next();
			a.addElementNameValuePair(element.getElementNameValuePair());
		}
		return a;
	}

	

	

	public void dump(DataOutputStream dos) throws IOException
	{
		dos.writeShort(typeIndex); // u2 index of type name in cpool
		dos.writeShort(evs.size()); // u2 element_value pair count
		for (int i = 0; i < evs.size(); i++)
		{
			ElementValuePairGen envp = (ElementValuePairGen) evs.get(i);
			envp.dump(dos);
		}
	}

	

	

	public final String getTypeSignature()
	{
		// ConstantClass c = (ConstantClass)cpool.getConstant(typeIndex);
		ConstantUtf8 utf8 = (ConstantUtf8) cpool
				.getConstant(typeIndex/* c.getNameIndex() */);
		return utf8.getBytes();
	}

	public final String getTypeName()
	{
		return getTypeSignature();// BCELBUG: Should I use this instead?
									// Utility.signatureToString(getTypeSignature());
	}

	

	public String toString()
	{
		StringBuffer s = new StringBuffer();
		s.append("AnnotationGen:[" + getTypeName() + " #" + evs.size() + " {");
		for (int i = 0; i < evs.size(); i++)
		{
			s.append(evs.get(i));
			if (i + 1 < evs.size())
				s.append(",");
		}
		s.append("}]");
		return s.toString();
	}

	

	
}
