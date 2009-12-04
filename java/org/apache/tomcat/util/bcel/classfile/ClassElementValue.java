package org.apache.tomcat.util.bcel.classfile;

import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.tomcat.util.bcel.Constants;

public class ClassElementValue extends ElementValue
{
	// For primitive types and string type, this points to the value entry in
	// the cpool
	// For 'class' this points to the class entry in the cpool
	private int idx;

	public ClassElementValue(int type, int idx, ConstantPool cpool)
	{
		super(type, cpool);
		this.idx = idx;
	}

	public int getIndex()
	{
		return idx;
	}

	public String getClassString()
	{
		ConstantUtf8 c = (ConstantUtf8) cpool.getConstant(idx,
				Constants.CONSTANT_Utf8);
		return c.getBytes();
	}

	public String stringifyValue()
	{
		ConstantUtf8 cu8 = (ConstantUtf8) cpool.getConstant(idx,
				Constants.CONSTANT_Utf8);
		return cu8.getBytes();
	}

	public void dump(DataOutputStream dos) throws IOException
	{
		dos.writeByte(type); // u1 kind of value
		dos.writeShort(idx);
	}
}
