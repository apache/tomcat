package org.apache.tomcat.util.bcel.generic;

import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.tomcat.util.bcel.classfile.ConstantDouble;
import org.apache.tomcat.util.bcel.classfile.ConstantFloat;
import org.apache.tomcat.util.bcel.classfile.ConstantInteger;
import org.apache.tomcat.util.bcel.classfile.ConstantLong;
import org.apache.tomcat.util.bcel.classfile.ConstantUtf8;
import org.apache.tomcat.util.bcel.classfile.ElementValue;
import org.apache.tomcat.util.bcel.classfile.SimpleElementValue;

public class SimpleElementValueGen extends ElementValueGen
{
	// For primitive types and string type, this points to the value entry in
	// the cpGen
	// For 'class' this points to the class entry in the cpGen
	private int idx;

	// ctors for each supported type... type could be inferred but for now lets
	// force it to be passed
	/**
	 * Protected ctor used for deserialization, doesn't *put* an entry in the
	 * constant pool, assumes the one at the supplied index is correct.
	 */
	protected SimpleElementValueGen(int type, int idx, ConstantPoolGen cpGen)
	{
		super(type, cpGen);
		this.idx = idx;
	}

	public SimpleElementValueGen(int type, ConstantPoolGen cpGen, int value)
	{
		super(type, cpGen);
		idx = cpGen.addInteger(value);
	}

	public SimpleElementValueGen(int type, ConstantPoolGen cpGen, long value)
	{
		super(type, cpGen);
		idx = cpGen.addLong(value);
	}

	public SimpleElementValueGen(int type, ConstantPoolGen cpGen, double value)
	{
		super(type, cpGen);
		idx = cpGen.addDouble(value);
	}

	public SimpleElementValueGen(int type, ConstantPoolGen cpGen, float value)
	{
		super(type, cpGen);
		idx = cpGen.addFloat(value);
	}

	public SimpleElementValueGen(int type, ConstantPoolGen cpGen, short value)
	{
		super(type, cpGen);
		idx = cpGen.addInteger(value);
	}

	public SimpleElementValueGen(int type, ConstantPoolGen cpGen, byte value)
	{
		super(type, cpGen);
		idx = cpGen.addInteger(value);
	}

	public SimpleElementValueGen(int type, ConstantPoolGen cpGen, char value)
	{
		super(type, cpGen);
		idx = cpGen.addInteger(value);
	}

	public SimpleElementValueGen(int type, ConstantPoolGen cpGen, boolean value)
	{
		super(type, cpGen);
		if (value)
			idx = cpGen.addInteger(1);
		else
			idx = cpGen.addInteger(0);
	}

	public SimpleElementValueGen(int type, ConstantPoolGen cpGen, String value)
	{
		super(type, cpGen);
		idx = cpGen.addUtf8(value);
	}

	/**
	 * The boolean controls whether we copy info from the 'old' constant pool to
	 * the 'new'. You need to use this ctor if the annotation is being copied
	 * from one file to another.
	 */
	public SimpleElementValueGen(SimpleElementValue value,
			ConstantPoolGen cpool, boolean copyPoolEntries)
	{
		super(value.getElementValueType(), cpool);
		if (!copyPoolEntries)
		{
			// J5ASSERT: Could assert value.stringifyValue() is the same as
			// cpool.getConstant(SimpleElementValuevalue.getIndex())
			idx = value.getIndex();
		}
		else
		{
			switch (value.getElementValueType())
			{
			case STRING:
				idx = cpool.addUtf8(value.getValueString());
				break;
			case PRIMITIVE_INT:
				idx = cpool.addInteger(value.getValueInt());
				break;
			case PRIMITIVE_BYTE:
				idx = cpool.addInteger(value.getValueByte());
				break;
			case PRIMITIVE_CHAR:
				idx = cpool.addInteger(value.getValueChar());
				break;
			case PRIMITIVE_LONG:
				idx = cpool.addLong(value.getValueLong());
				break;
			case PRIMITIVE_FLOAT:
				idx = cpool.addFloat(value.getValueFloat());
				break;
			case PRIMITIVE_DOUBLE:
				idx = cpool.addDouble(value.getValueDouble());
				break;
			case PRIMITIVE_BOOLEAN:
				if (value.getValueBoolean())
				{
					idx = cpool.addInteger(1);
				}
				else
				{
					idx = cpool.addInteger(0);
				}
				break;
			case PRIMITIVE_SHORT:
				idx = cpool.addInteger(value.getValueShort());
				break;
			default:
				throw new RuntimeException(
						"SimpleElementValueGen class does not know how "
								+ "to copy this type " + type);
			}
		}
	}

	/**
	 * Return immutable variant
	 */
	public ElementValue getElementValue()
	{
		return new SimpleElementValue(type, idx, cpGen.getConstantPool());
	}

	public int getIndex()
	{
		return idx;
	}

	public String getValueString()
	{
		if (type != STRING)
			throw new RuntimeException(
					"Dont call getValueString() on a non STRING ElementValue");
		ConstantUtf8 c = (ConstantUtf8) cpGen.getConstant(idx);
		return c.getBytes();
	}

	public int getValueInt()
	{
		if (type != PRIMITIVE_INT)
			throw new RuntimeException(
					"Dont call getValueString() on a non STRING ElementValue");
		ConstantInteger c = (ConstantInteger) cpGen.getConstant(idx);
		return c.getBytes();
	}

	// Whatever kind of value it is, return it as a string
	public String stringifyValue()
	{
		switch (type)
		{
		case PRIMITIVE_INT:
			ConstantInteger c = (ConstantInteger) cpGen.getConstant(idx);
			return Integer.toString(c.getBytes());
		case PRIMITIVE_LONG:
			ConstantLong j = (ConstantLong) cpGen.getConstant(idx);
			return Long.toString(j.getBytes());
		case PRIMITIVE_DOUBLE:
			ConstantDouble d = (ConstantDouble) cpGen.getConstant(idx);
			return Double.toString(d.getBytes());
		case PRIMITIVE_FLOAT:
			ConstantFloat f = (ConstantFloat) cpGen.getConstant(idx);
			return Float.toString(f.getBytes());
		case PRIMITIVE_SHORT:
			ConstantInteger s = (ConstantInteger) cpGen.getConstant(idx);
			return Integer.toString(s.getBytes());
		case PRIMITIVE_BYTE:
			ConstantInteger b = (ConstantInteger) cpGen.getConstant(idx);
			return Integer.toString(b.getBytes());
		case PRIMITIVE_CHAR:
			ConstantInteger ch = (ConstantInteger) cpGen.getConstant(idx);
			return Integer.toString(ch.getBytes());
		case PRIMITIVE_BOOLEAN:
			ConstantInteger bo = (ConstantInteger) cpGen.getConstant(idx);
			if (bo.getBytes() == 0)
				return "false";
			if (bo.getBytes() != 0)
				return "true";
		case STRING:
			ConstantUtf8 cu8 = (ConstantUtf8) cpGen.getConstant(idx);
			return cu8.getBytes();
		default:
			throw new RuntimeException(
					"SimpleElementValueGen class does not know how to stringify type "
							+ type);
		}
	}

	public void dump(DataOutputStream dos) throws IOException
	{
		dos.writeByte(type); // u1 kind of value
		switch (type)
		{
		case PRIMITIVE_INT:
		case PRIMITIVE_BYTE:
		case PRIMITIVE_CHAR:
		case PRIMITIVE_FLOAT:
		case PRIMITIVE_LONG:
		case PRIMITIVE_BOOLEAN:
		case PRIMITIVE_SHORT:
		case PRIMITIVE_DOUBLE:
		case STRING:
			dos.writeShort(idx);
			break;
		default:
			throw new RuntimeException(
					"SimpleElementValueGen doesnt know how to write out type "
							+ type);
		}
	}
}
