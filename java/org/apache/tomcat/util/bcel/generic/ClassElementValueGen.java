package org.apache.tomcat.util.bcel.generic;

import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.tomcat.util.bcel.classfile.ElementValue;
import org.apache.tomcat.util.bcel.classfile.ConstantUtf8;
import org.apache.tomcat.util.bcel.classfile.ClassElementValue;
import org.apache.tomcat.util.bcel.generic.ConstantPoolGen;
import org.apache.tomcat.util.bcel.generic.ElementValueGen;

public class ClassElementValueGen extends ElementValueGen
{
	// For primitive types and string type, this points to the value entry in
	// the cpool
	// For 'class' this points to the class entry in the cpool
	private int idx;

	

	

	/**
	 * Return immutable variant of this ClassElementValueGen
	 */
	public ElementValue getElementValue()
	{
		return new ClassElementValue(type, idx, cpGen.getConstantPool());
	}

	public ClassElementValueGen(ClassElementValue value, ConstantPoolGen cpool,
			boolean copyPoolEntries)
	{
		super(CLASS, cpool);
		if (copyPoolEntries)
		{
			// idx = cpool.addClass(value.getClassString());
			idx = cpool.addUtf8(value.getClassString());
		}
		else
		{
			idx = value.getIndex();
		}
	}

	

	public String getClassString()
	{
		ConstantUtf8 cu8 = (ConstantUtf8) getConstantPool().getConstant(idx);
		return cu8.getBytes();
		// ConstantClass c = (ConstantClass)getConstantPool().getConstant(idx);
		// ConstantUtf8 utf8 =
		// (ConstantUtf8)getConstantPool().getConstant(c.getNameIndex());
		// return utf8.getBytes();
	}

	public String stringifyValue()
	{
		return getClassString();
	}

	public void dump(DataOutputStream dos) throws IOException
	{
		dos.writeByte(type); // u1 kind of value
		dos.writeShort(idx);
	}
}
