package org.apache.tomcat.util.bcel.generic;

import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.tomcat.util.bcel.classfile.ConstantUtf8;
import org.apache.tomcat.util.bcel.classfile.ElementValue;
import org.apache.tomcat.util.bcel.classfile.EnumElementValue;

public class EnumElementValueGen extends ElementValueGen
{
	// For enum types, these two indices point to the type and value
	private int typeIdx;

	private int valueIdx;

	

	/**
	 * Return immutable variant of this EnumElementValue
	 */
	public ElementValue getElementValue()
	{
		System.err.println("Duplicating value: " + getEnumTypeString() + ":"
				+ getEnumValueString());
		return new EnumElementValue(type, typeIdx, valueIdx, cpGen
				.getConstantPool());
	}

	

	public EnumElementValueGen(EnumElementValue value, ConstantPoolGen cpool,
			boolean copyPoolEntries)
	{
		super(ENUM_CONSTANT, cpool);
		if (copyPoolEntries)
		{
			typeIdx = cpool.addUtf8(value.getEnumTypeString());// was
																// addClass(value.getEnumTypeString());
			valueIdx = cpool.addUtf8(value.getEnumValueString()); // was
																	// addString(value.getEnumValueString());
		}
		else
		{
			typeIdx = value.getTypeIndex();
			valueIdx = value.getValueIndex();
		}
	}

	public void dump(DataOutputStream dos) throws IOException
	{
		dos.writeByte(type); // u1 type of value (ENUM_CONSTANT == 'e')
		dos.writeShort(typeIdx); // u2
		dos.writeShort(valueIdx); // u2
	}

	public String stringifyValue()
	{
		ConstantUtf8 cu8 = (ConstantUtf8) getConstantPool().getConstant(
				valueIdx);
		return cu8.getBytes();
		// ConstantString cu8 =
		// (ConstantString)getConstantPool().getConstant(valueIdx);
		// return
		// ((ConstantUtf8)getConstantPool().getConstant(cu8.getStringIndex())).getBytes();
	}

	// BCELBUG: Should we need to call utility.signatureToString() on the output
	// here?
	public String getEnumTypeString()
	{
		// Constant cc = getConstantPool().getConstant(typeIdx);
		// ConstantClass cu8 =
		// (ConstantClass)getConstantPool().getConstant(typeIdx);
		// return
		// ((ConstantUtf8)getConstantPool().getConstant(cu8.getNameIndex())).getBytes();
		return ((ConstantUtf8) getConstantPool().getConstant(typeIdx))
				.getBytes();
		// return Utility.signatureToString(cu8.getBytes());
	}

	public String getEnumValueString()
	{
		return ((ConstantUtf8) getConstantPool().getConstant(valueIdx))
				.getBytes();
		// ConstantString cu8 =
		// (ConstantString)getConstantPool().getConstant(valueIdx);
		// return
		// ((ConstantUtf8)getConstantPool().getConstant(cu8.getStringIndex())).getBytes();
	}

	

	
}
