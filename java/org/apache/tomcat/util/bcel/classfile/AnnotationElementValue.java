package org.apache.tomcat.util.bcel.classfile;

import java.io.DataOutputStream;
import java.io.IOException;

public class AnnotationElementValue extends ElementValue
{
	// For annotation element values, this is the annotation
	private AnnotationEntry annotationEntry;

	public AnnotationElementValue(int type, AnnotationEntry annotationEntry,
			ConstantPool cpool)
	{
		super(type, cpool);
		if (type != ANNOTATION)
			throw new RuntimeException(
					"Only element values of type annotation can be built with this ctor - type specified: " + type);
		this.annotationEntry = annotationEntry;
	}

	public void dump(DataOutputStream dos) throws IOException
	{
		dos.writeByte(type); // u1 type of value (ANNOTATION == '@')
		annotationEntry.dump(dos);
	}

	public String stringifyValue()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(annotationEntry.toString());
		return sb.toString();
	}

	public String toString()
	{
		return stringifyValue();
	}

	public AnnotationEntry getAnnotationEntry()
	{
		return annotationEntry;
	}
}
