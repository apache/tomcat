package org.apache.tomcat.util.bcel.generic;

import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.tomcat.util.bcel.classfile.ConstantUtf8;
import org.apache.tomcat.util.bcel.classfile.ElementValue;
import org.apache.tomcat.util.bcel.classfile.ElementValuePair;

public class ElementValuePairGen
{
	private int nameIdx;

	private ElementValueGen value;

	private ConstantPoolGen cpool;

	public ElementValuePairGen(ElementValuePair nvp, ConstantPoolGen cpool,
			boolean copyPoolEntries)
	{
		this.cpool = cpool;
		// J5ASSERT:
		// Could assert nvp.getNameString() points to the same thing as
		// cpool.getConstant(nvp.getNameIndex())
		// if
		// (!nvp.getNameString().equals(((ConstantUtf8)cpool.getConstant(nvp.getNameIndex())).getBytes()))
		// {
		// throw new RuntimeException("envp buggered");
		// }
		if (copyPoolEntries)
		{
			nameIdx = cpool.addUtf8(nvp.getNameString());
		}
		else
		{
			nameIdx = nvp.getNameIndex();
		}
		value = ElementValueGen.copy(nvp.getValue(), cpool, copyPoolEntries);
	}

	/**
	 * Retrieve an immutable version of this ElementNameValuePairGen
	 */
	public ElementValuePair getElementNameValuePair()
	{
		ElementValue immutableValue = value.getElementValue();
		return new ElementValuePair(nameIdx, immutableValue, cpool
				.getConstantPool());
	}

	

	

	protected void dump(DataOutputStream dos) throws IOException
	{
		dos.writeShort(nameIdx); // u2 name of the element
		value.dump(dos);
	}

	

	public final String getNameString()
	{
		// ConstantString cu8 = (ConstantString)cpool.getConstant(nameIdx);
		return ((ConstantUtf8) cpool.getConstant(nameIdx)).getBytes();
	}

	

	public String toString()
	{
		return "ElementValuePair:[" + getNameString() + "="
				+ value.stringifyValue() + "]";
	}
}
