package org.apache.tomcat.util.bcel.generic;

import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.tomcat.util.bcel.classfile.AnnotationElementValue;
import org.apache.tomcat.util.bcel.classfile.ArrayElementValue;
import org.apache.tomcat.util.bcel.classfile.ClassElementValue;
import org.apache.tomcat.util.bcel.classfile.ElementValue;
import org.apache.tomcat.util.bcel.classfile.EnumElementValue;
import org.apache.tomcat.util.bcel.classfile.SimpleElementValue;

public abstract class ElementValueGen
{
	protected int type;

	protected ConstantPoolGen cpGen;

	protected ElementValueGen(int type, ConstantPoolGen cpGen)
	{
		this.type = type;
		this.cpGen = cpGen;
	}

	/**
	 * Subtypes return an immutable variant of the ElementValueGen
	 */
	public abstract ElementValue getElementValue();

	

	public abstract String stringifyValue();

	public abstract void dump(DataOutputStream dos) throws IOException;

	public static final int STRING = 's';

	public static final int ENUM_CONSTANT = 'e';

	public static final int CLASS = 'c';

	public static final int ANNOTATION = '@';

	public static final int ARRAY = '[';

	public static final int PRIMITIVE_INT = 'I';

	public static final int PRIMITIVE_BYTE = 'B';

	public static final int PRIMITIVE_CHAR = 'C';

	public static final int PRIMITIVE_DOUBLE = 'D';

	public static final int PRIMITIVE_FLOAT = 'F';

	public static final int PRIMITIVE_LONG = 'J';

	public static final int PRIMITIVE_SHORT = 'S';

	public static final int PRIMITIVE_BOOLEAN = 'Z';

	

	protected ConstantPoolGen getConstantPool()
	{
		return cpGen;
	}

	/**
	 * Creates an (modifiable) ElementValueGen copy of an (immutable)
	 * ElementValue - constant pool is assumed correct.
	 */
	public static ElementValueGen copy(ElementValue value,
			ConstantPoolGen cpool, boolean copyPoolEntries)
	{
		switch (value.getElementValueType())
		{
		case 'B': // byte
		case 'C': // char
		case 'D': // double
		case 'F': // float
		case 'I': // int
		case 'J': // long
		case 'S': // short
		case 'Z': // boolean
		case 's': // String
			return new SimpleElementValueGen((SimpleElementValue) value, cpool,
					copyPoolEntries);
		case 'e': // Enum constant
			return new EnumElementValueGen((EnumElementValue) value, cpool,
					copyPoolEntries);
		case '@': // Annotation
			return new AnnotationElementValueGen(
					(AnnotationElementValue) value, cpool, copyPoolEntries);
		case '[': // Array
			return new ArrayElementValueGen((ArrayElementValue) value, cpool,
					copyPoolEntries);
		case 'c': // Class
			return new ClassElementValueGen((ClassElementValue) value, cpool,
					copyPoolEntries);
		default:
			throw new RuntimeException("Not implemented yet! ("
					+ value.getElementValueType() + ")");
		}
	}
}
