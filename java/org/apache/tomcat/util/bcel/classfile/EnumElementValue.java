package org.apache.tomcat.util.bcel.classfile;

import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.tomcat.util.bcel.Constants;

public class EnumElementValue extends ElementValue
{
	// For enum types, these two indices point to the type and value
	private int typeIdx;

	private int valueIdx;

	public EnumElementValue(int type, int typeIdx, int valueIdx,
			ConstantPool cpool)
	{
		super(type, cpool);
		if (type != ENUM_CONSTANT)
			throw new RuntimeException(
					"Only element values of type enum can be built with this ctor - type specified: " + type);
		this.typeIdx = typeIdx;
		this.valueIdx = valueIdx;
	}

	public void dump(DataOutputStream dos) throws IOException
	{
		dos.writeByte(type); // u1 type of value (ENUM_CONSTANT == 'e')
		dos.writeShort(typeIdx); // u2
		dos.writeShort(valueIdx); // u2
	}

	public String stringifyValue()
	{
		ConstantUtf8 cu8 = (ConstantUtf8) cpool.getConstant(valueIdx,
				Constants.CONSTANT_Utf8);
		return cu8.getBytes();
	}

	public String getEnumTypeString()
	{
		ConstantUtf8 cu8 = (ConstantUtf8) cpool.getConstant(typeIdx,
				Constants.CONSTANT_Utf8);
		return cu8.getBytes();// Utility.signatureToString(cu8.getBytes());
	}

	public String getEnumValueString()
	{
		ConstantUtf8 cu8 = (ConstantUtf8) cpool.getConstant(valueIdx,
				Constants.CONSTANT_Utf8);
		return cu8.getBytes();
	}

	public int getValueIndex()
	{
		return valueIdx;
	}

	public int getTypeIndex()
	{
		return typeIdx;
	}
}
