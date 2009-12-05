package org.apache.tomcat.util.bcel.classfile;

import java.io.DataOutputStream;
import java.io.IOException;

public class ArrayElementValue extends ElementValue
{
	// For array types, this is the array
	private ElementValue[] evalues;

	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("{");
		for (int i = 0; i < evalues.length; i++)
		{
			sb.append(evalues[i].toString());
			if ((i + 1) < evalues.length)
				sb.append(",");
		}
		sb.append("}");
		return sb.toString();
	}

	public ArrayElementValue(int type, ElementValue[] datums, ConstantPool cpool)
	{
		super(type, cpool);
		if (type != ARRAY)
			throw new RuntimeException(
					"Only element values of type array can be built with this ctor - type specified: " + type);
		this.evalues = datums;
	}

	public void dump(DataOutputStream dos) throws IOException
	{
		dos.writeByte(type); // u1 type of value (ARRAY == '[')
		dos.writeShort(evalues.length);
		for (int i = 0; i < evalues.length; i++)
		{
			evalues[i].dump(dos);
		}
	}

	public String stringifyValue()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("[");
		for (int i = 0; i < evalues.length; i++)
		{
			sb.append(evalues[i].stringifyValue());
			if ((i + 1) < evalues.length)
				sb.append(",");
		}
		sb.append("]");
		return sb.toString();
	}

	public ElementValue[] getElementValuesArray()
	{
		return evalues;
	}

	
}
