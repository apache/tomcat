/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.tomcat.util.bcel.classfile;

import java.io.IOException;

import org.apache.tomcat.util.bcel.Constants;
import org.apache.tomcat.util.bcel.util.ByteSequence;

/**
 * Utility functions that do not really belong to any class in particular.
 *
 * @version $Id$
 * @author  <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A>
 */
public abstract class Utility {

    private static int unwrap( ThreadLocal<Integer> tl ) {
        return tl.get().intValue();
    }


    private static void wrap( ThreadLocal<Integer> tl, int value ) {
        tl.set(Integer.valueOf(value));
    }

    private static ThreadLocal<Integer> consumed_chars =
            new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return Integer.valueOf(0);
        }
    };/* How many chars have been consumed
     * during parsing in signatureToString().
     * Read by methodSignatureToString().
     * Set by side effect,but only internally.
     */
    private static boolean wide = false; /* The `WIDE' instruction is used in the
     * byte code to allow 16-bit wide indices
     * for local variables. This opcode
     * precedes an `ILOAD', e.g.. The opcode
     * immediately following takes an extra
     * byte which is combined with the
     * following byte to form a
     * 16-bit value.
     */


    /**
     * Convert bit field of flags into string such as `static final'.
     *
     * @param  access_flags Access flags
     * @return String representation of flags
     */
    public static final String accessToString( int access_flags ) {
        return accessToString(access_flags, false);
    }


    /**
     * Convert bit field of flags into string such as `static final'.
     *
     * Special case: Classes compiled with new compilers and with the
     * `ACC_SUPER' flag would be said to be "synchronized". This is
     * because SUN used the same value for the flags `ACC_SUPER' and
     * `ACC_SYNCHRONIZED'. 
     *
     * @param  access_flags Access flags
     * @param  for_class access flags are for class qualifiers ?
     * @return String representation of flags
     */
    public static final String accessToString( int access_flags, boolean for_class ) {
        StringBuilder buf = new StringBuilder();
        int p = 0;
        for (int i = 0; p < Constants.MAX_ACC_FLAG; i++) { // Loop through known flags
            p = pow2(i);
            if ((access_flags & p) != 0) {
                /* Special case: Classes compiled with new compilers and with the
                 * `ACC_SUPER' flag would be said to be "synchronized". This is
                 * because SUN used the same value for the flags `ACC_SUPER' and
                 * `ACC_SYNCHRONIZED'.
                 */
                if (for_class && ((p == Constants.ACC_SUPER) || (p == Constants.ACC_INTERFACE))) {
                    continue;
                }
                buf.append(Constants.ACCESS_NAMES[i]).append(" ");
            }
        }
        return buf.toString().trim();
    }


    /**
     * @param access_flags the class flags
     * 
     * @return "class" or "interface", depending on the ACC_INTERFACE flag
     */
    public static final String classOrInterface( int access_flags ) {
        return ((access_flags & Constants.ACC_INTERFACE) != 0) ? "interface" : "class";
    }


    /**
     * Disassemble a byte array of JVM byte codes starting from code line 
     * `index' and return the disassembled string representation. Decode only
     * `num' opcodes (including their operands), use -1 if you want to
     * decompile everything.
     *
     * @param  code byte code array
     * @param  constant_pool Array of constants
     * @param  index offset in `code' array
     * <EM>(number of opcodes, not bytes!)</EM>
     * @param  length number of opcodes to decompile, -1 for all
     * @param  verbose be verbose, e.g. print constant pool index
     * @return String representation of byte codes
     */
    public static final String codeToString( byte[] code, ConstantPool constant_pool, int index,
            int length, boolean verbose ) {
        StringBuilder buf = new StringBuilder(code.length * 20); // Should be sufficient
        ByteSequence stream = new ByteSequence(code);
        try {
            for (int i = 0; i < index; i++) {
                codeToString(stream, constant_pool, verbose);
            }
            for (int i = 0; stream.available() > 0; i++) {
                if ((length < 0) || (i < length)) {
                    String indices = fillup(stream.getIndex() + ":", 6, true, ' ');
                    buf.append(indices).append(codeToString(stream, constant_pool, verbose))
                            .append('\n');
                }
            }
        } catch (IOException e) {
            System.out.println(buf.toString());
            e.printStackTrace();
            throw new ClassFormatException("Byte code error: " + e, e);
        }
        return buf.toString();
    }


    /**
     * Disassemble a stream of byte codes and return the
     * string representation.
     *
     * @param  bytes stream of bytes
     * @param  constant_pool Array of constants
     * @param  verbose be verbose, e.g. print constant pool index
     * @return String representation of byte code
     * 
     * @throws IOException if a failure from reading from the bytes argument occurs
     */
    public static final String codeToString( ByteSequence bytes, ConstantPool constant_pool,
            boolean verbose ) throws IOException {
        short opcode = (short) bytes.readUnsignedByte();
        int default_offset = 0, low, high, npairs;
        int index, vindex, constant;
        int[] match, jump_table;
        int no_pad_bytes = 0, offset;
        StringBuilder buf = new StringBuilder(Constants.OPCODE_NAMES[opcode]);
        /* Special case: Skip (0-3) padding bytes, i.e., the
         * following bytes are 4-byte-aligned
         */
        if ((opcode == Constants.TABLESWITCH) || (opcode == Constants.LOOKUPSWITCH)) {
            int remainder = bytes.getIndex() % 4;
            no_pad_bytes = (remainder == 0) ? 0 : 4 - remainder;
            for (int i = 0; i < no_pad_bytes; i++) {
                byte b;
                if ((b = bytes.readByte()) != 0) {
                    System.err.println("Warning: Padding byte != 0 in "
                            + Constants.OPCODE_NAMES[opcode] + ":" + b);
                }
            }
            // Both cases have a field default_offset in common
            default_offset = bytes.readInt();
        }
        switch (opcode) {
            /* Table switch has variable length arguments.
             */
            case Constants.TABLESWITCH:
                low = bytes.readInt();
                high = bytes.readInt();
                offset = bytes.getIndex() - 12 - no_pad_bytes - 1;
                default_offset += offset;
                buf.append("\tdefault = ").append(default_offset).append(", low = ").append(low)
                        .append(", high = ").append(high).append("(");
                jump_table = new int[high - low + 1];
                for (int i = 0; i < jump_table.length; i++) {
                    jump_table[i] = offset + bytes.readInt();
                    buf.append(jump_table[i]);
                    if (i < jump_table.length - 1) {
                        buf.append(", ");
                    }
                }
                buf.append(")");
                break;
            /* Lookup switch has variable length arguments.
             */
            case Constants.LOOKUPSWITCH: {
                npairs = bytes.readInt();
                offset = bytes.getIndex() - 8 - no_pad_bytes - 1;
                match = new int[npairs];
                jump_table = new int[npairs];
                default_offset += offset;
                buf.append("\tdefault = ").append(default_offset).append(", npairs = ").append(
                        npairs).append(" (");
                for (int i = 0; i < npairs; i++) {
                    match[i] = bytes.readInt();
                    jump_table[i] = offset + bytes.readInt();
                    buf.append("(").append(match[i]).append(", ").append(jump_table[i]).append(")");
                    if (i < npairs - 1) {
                        buf.append(", ");
                    }
                }
                buf.append(")");
            }
                break;
            /* Two address bytes + offset from start of byte stream form the
             * jump target
             */
            case Constants.GOTO:
            case Constants.IFEQ:
            case Constants.IFGE:
            case Constants.IFGT:
            case Constants.IFLE:
            case Constants.IFLT:
            case Constants.JSR:
            case Constants.IFNE:
            case Constants.IFNONNULL:
            case Constants.IFNULL:
            case Constants.IF_ACMPEQ:
            case Constants.IF_ACMPNE:
            case Constants.IF_ICMPEQ:
            case Constants.IF_ICMPGE:
            case Constants.IF_ICMPGT:
            case Constants.IF_ICMPLE:
            case Constants.IF_ICMPLT:
            case Constants.IF_ICMPNE:
                buf.append("\t\t#").append((bytes.getIndex() - 1) + bytes.readShort());
                break;
            /* 32-bit wide jumps
             */
            case Constants.GOTO_W:
            case Constants.JSR_W:
                buf.append("\t\t#").append(((bytes.getIndex() - 1) + bytes.readInt()));
                break;
            /* Index byte references local variable (register)
             */
            case Constants.ALOAD:
            case Constants.ASTORE:
            case Constants.DLOAD:
            case Constants.DSTORE:
            case Constants.FLOAD:
            case Constants.FSTORE:
            case Constants.ILOAD:
            case Constants.ISTORE:
            case Constants.LLOAD:
            case Constants.LSTORE:
            case Constants.RET:
                if (wide) {
                    vindex = bytes.readUnsignedShort();
                    wide = false; // Clear flag
                } else {
                    vindex = bytes.readUnsignedByte();
                }
                buf.append("\t\t%").append(vindex);
                break;
            /*
             * Remember wide byte which is used to form a 16-bit address in the
             * following instruction. Relies on that the method is called again with
             * the following opcode.
             */
            case Constants.WIDE:
                wide = true;
                buf.append("\t(wide)");
                break;
            /* Array of basic type.
             */
            case Constants.NEWARRAY:
                buf.append("\t\t<").append(Constants.TYPE_NAMES[bytes.readByte()]).append(">");
                break;
            /* Access object/class fields.
             */
            case Constants.GETFIELD:
            case Constants.GETSTATIC:
            case Constants.PUTFIELD:
            case Constants.PUTSTATIC:
                index = bytes.readUnsignedShort();
                buf.append("\t\t").append(
                        constant_pool.constantToString(index, Constants.CONSTANT_Fieldref)).append(
                        (verbose ? " (" + index + ")" : ""));
                break;
            /* Operands are references to classes in constant pool
             */
            case Constants.NEW:
            case Constants.CHECKCAST:
                buf.append("\t");
                //$FALL-THROUGH$
            case Constants.INSTANCEOF:
                index = bytes.readUnsignedShort();
                buf.append("\t<").append(
                        constant_pool.constantToString(index, Constants.CONSTANT_Class))
                        .append(">").append((verbose ? " (" + index + ")" : ""));
                break;
            /* Operands are references to methods in constant pool
             */
            case Constants.INVOKESPECIAL:
            case Constants.INVOKESTATIC:
            case Constants.INVOKEVIRTUAL:
                index = bytes.readUnsignedShort();
                buf.append("\t").append(
                        constant_pool.constantToString(index, Constants.CONSTANT_Methodref))
                        .append((verbose ? " (" + index + ")" : ""));
                break;
            case Constants.INVOKEINTERFACE:
                index = bytes.readUnsignedShort();
                int nargs = bytes.readUnsignedByte(); // historical, redundant
                buf.append("\t").append(
                        constant_pool
                                .constantToString(index, Constants.CONSTANT_InterfaceMethodref))
                        .append(verbose ? " (" + index + ")\t" : "").append(nargs).append("\t")
                        .append(bytes.readUnsignedByte()); // Last byte is a reserved space
                break;
            /* Operands are references to items in constant pool
             */
            case Constants.LDC_W:
            case Constants.LDC2_W:
                index = bytes.readUnsignedShort();
                buf.append("\t\t").append(
                        constant_pool.constantToString(index, constant_pool.getConstant(index)
                                .getTag())).append((verbose ? " (" + index + ")" : ""));
                break;
            case Constants.LDC:
                index = bytes.readUnsignedByte();
                buf.append("\t\t").append(
                        constant_pool.constantToString(index, constant_pool.getConstant(index)
                                .getTag())).append((verbose ? " (" + index + ")" : ""));
                break;
            /* Array of references.
             */
            case Constants.ANEWARRAY:
                index = bytes.readUnsignedShort();
                buf.append("\t\t<").append(
                        compactClassName(constant_pool.getConstantString(index,
                                Constants.CONSTANT_Class), false)).append(">").append(
                        (verbose ? " (" + index + ")" : ""));
                break;
            /* Multidimensional array of references.
             */
            case Constants.MULTIANEWARRAY: {
                index = bytes.readUnsignedShort();
                int dimensions = bytes.readUnsignedByte();
                buf.append("\t<").append(
                        compactClassName(constant_pool.getConstantString(index,
                                Constants.CONSTANT_Class), false)).append(">\t").append(dimensions)
                        .append((verbose ? " (" + index + ")" : ""));
            }
                break;
            /* Increment local variable.
             */
            case Constants.IINC:
                if (wide) {
                    vindex = bytes.readUnsignedShort();
                    constant = bytes.readShort();
                    wide = false;
                } else {
                    vindex = bytes.readUnsignedByte();
                    constant = bytes.readByte();
                }
                buf.append("\t\t%").append(vindex).append("\t").append(constant);
                break;
            default:
                if (Constants.NO_OF_OPERANDS[opcode] > 0) {
                    for (int i = 0; i < Constants.TYPE_OF_OPERANDS[opcode].length; i++) {
                        buf.append("\t\t");
                        switch (Constants.TYPE_OF_OPERANDS[opcode][i]) {
                            case Constants.T_BYTE:
                                buf.append(bytes.readByte());
                                break;
                            case Constants.T_SHORT:
                                buf.append(bytes.readShort());
                                break;
                            case Constants.T_INT:
                                buf.append(bytes.readInt());
                                break;
                            default: // Never reached
                                System.err.println("Unreachable default case reached!");
                                System.exit(-1);
                        }
                    }
                }
        }
        return buf.toString();
    }


    /**
     * Shorten long class names, <em>java/lang/String</em> becomes 
     * <em>String</em>.
     *
     * @param str The long class name
     * @return Compacted class name
     */
    public static final String compactClassName( String str ) {
        return compactClassName(str, true);
    }


    /**
     * Shorten long class name <em>str</em>, i.e., chop off the <em>prefix</em>,
     * if the
     * class name starts with this string and the flag <em>chopit</em> is true.
     * Slashes <em>/</em> are converted to dots <em>.</em>.
     *
     * @param str The long class name
     * @param prefix The prefix the get rid off
     * @param chopit Flag that determines whether chopping is executed or not
     * @return Compacted class name
     */
    public static final String compactClassName( String str, String prefix, boolean chopit ) {
        int len = prefix.length();
        str = str.replace('/', '.'); // Is `/' on all systems, even DOS
        if (chopit) {
            // If string starts with `prefix' and contains no further dots
            if (str.startsWith(prefix) && (str.substring(len).indexOf('.') == -1)) {
                str = str.substring(len);
            }
        }
        return str;
    }


    /**
     * Shorten long class names, <em>java/lang/String</em> becomes 
     * <em>java.lang.String</em>,
     * e.g.. If <em>chopit</em> is <em>true</em> the prefix <em>java.lang</em>
     * is also removed.
     *
     * @param str The long class name
     * @param chopit Flag that determines whether chopping is executed or not
     * @return Compacted class name
     */
    public static final String compactClassName( String str, boolean chopit ) {
        return compactClassName(str, "java.lang.", chopit);
    }

    /**
     * A returntype signature represents the return value from a method.
     * It is a series of bytes in the following grammar:
     *
     * <return_signature> ::= <field_type> | V
     *
     * The character V indicates that the method returns no value. Otherwise, the
     * signature indicates the type of the return value.
     * An argument signature represents an argument passed to a method:
     *
     * <argument_signature> ::= <field_type>
     *
     * A method signature represents the arguments that the method expects, and
     * the value that it returns.
     * <method_signature> ::= (<arguments_signature>) <return_signature>
     * <arguments_signature>::= <argument_signature>*
     *
     * This method converts such a string into a Java type declaration like
     * `void main(String[])' and throws a `ClassFormatException' when the parsed 
     * type is invalid.
     *
     * @param  signature    Method signature
     * @param  name         Method name
     * @param  access       Method access rights
     * @param chopit
     * @param vars
     * @return Java type declaration
     * @throws  ClassFormatException  
     */
    public static final String methodSignatureToString( String signature, String name,
            String access, boolean chopit, LocalVariableTable vars ) throws ClassFormatException {
        StringBuilder buf = new StringBuilder("(");
        String type;
        int index;
        int var_index = (access.indexOf("static") >= 0) ? 0 : 1;
        try { // Read all declarations between for `(' and `)'
            if (signature.charAt(0) != '(') {
                throw new ClassFormatException("Invalid method signature: " + signature);
            }
            index = 1; // current string position
            while (signature.charAt(index) != ')') {
                String param_type = signatureToString(signature.substring(index), chopit);
                buf.append(param_type);
                if (vars != null) {
                    LocalVariable l = vars.getLocalVariable(var_index);
                    if (l != null) {
                        buf.append(" ").append(l.getName());
                    }
                } else {
                    buf.append(" arg").append(var_index);
                }
                if ("double".equals(param_type) || "long".equals(param_type)) {
                    var_index += 2;
                } else {
                    var_index++;
                }
                buf.append(", ");
                //corrected concurrent private static field acess
                index += unwrap(consumed_chars); // update position
            }
            index++; // update position
            // Read return type after `)'
            type = signatureToString(signature.substring(index), chopit);
        } catch (StringIndexOutOfBoundsException e) { // Should never occur
            throw new ClassFormatException("Invalid method signature: " + signature, e);
        }
        if (buf.length() > 1) {
            buf.setLength(buf.length() - 2);
        }
        buf.append(")");
        return access + ((access.length() > 0) ? " " : "") + // May be an empty string
                type + " " + name + buf.toString();
    }


    // Guess what this does
    private static final int pow2( int n ) {
        return 1 << n;
    }


    /**
     * Replace all occurrences of <em>old</em> in <em>str</em> with <em>new</em>.
     *
     * @param str String to permute
     * @param old String to be replaced
     * @param new_ Replacement string
     * @return new String object
     */
    public static final String replace( String str, String old, String new_ ) {
        int index, old_index;
        try {
            if (str.indexOf(old) != -1) { // `old' found in str
                StringBuffer buf = new StringBuffer();
                old_index = 0; // String start offset
                // While we have something to replace
                while ((index = str.indexOf(old, old_index)) != -1) {
                    buf.append(str.substring(old_index, index)); // append prefix
                    buf.append(new_); // append replacement
                    old_index = index + old.length(); // Skip `old'.length chars
                }
                buf.append(str.substring(old_index)); // append rest of string
                str = buf.toString();
            }
        } catch (StringIndexOutOfBoundsException e) { // Should not occur
            System.err.println(e);
        }
        return str;
    }


    /**
     * Converts signature to string with all class names compacted.
     *
     * @param signature to convert
     * @return Human readable signature
     */
    public static final String signatureToString( String signature ) {
        return signatureToString(signature, true);
    }


    /**
     * The field signature represents the value of an argument to a function or 
     * the value of a variable. It is a series of bytes generated by the 
     * following grammar:
     *
     * <PRE>
     * <field_signature> ::= <field_type>
     * <field_type>      ::= <base_type>|<object_type>|<array_type>
     * <base_type>       ::= B|C|D|F|I|J|S|Z
     * <object_type>     ::= L<fullclassname>;
     * <array_type>      ::= [<field_type>
     *
     * The meaning of the base types is as follows:
     * B byte signed byte
     * C char character
     * D double double precision IEEE float
     * F float single precision IEEE float
     * I int integer
     * J long long integer
     * L<fullclassname>; ... an object of the given class
     * S short signed short
     * Z boolean true or false
     * [<field sig> ... array
     * </PRE>
     *
     * This method converts this string into a Java type declaration such as
     * `String[]' and throws a `ClassFormatException' when the parsed type is 
     * invalid.
     *
     * @param  signature  Class signature
     * @param chopit Flag that determines whether chopping is executed or not
     * @return Java type declaration
     * @throws ClassFormatException
     */
    public static final String signatureToString( String signature, boolean chopit ) {
        //corrected concurrent private static field acess
        wrap(consumed_chars, 1); // This is the default, read just one char like `B'
        try {
            switch (signature.charAt(0)) {
                case 'B':
                    return "byte";
                case 'C':
                    return "char";
                case 'D':
                    return "double";
                case 'F':
                    return "float";
                case 'I':
                    return "int";
                case 'J':
                    return "long";
                case 'L': { // Full class name
                    int index = signature.indexOf(';'); // Look for closing `;'
                    if (index < 0) {
                        throw new ClassFormatException("Invalid signature: " + signature);
                    }
                    //corrected concurrent private static field acess
                    wrap(consumed_chars, index + 1); // "Lblabla;" `L' and `;' are removed
                    return compactClassName(signature.substring(1, index), chopit);
                }
                case 'S':
                    return "short";
                case 'Z':
                    return "boolean";
                case '[': { // Array declaration
                    int n;
                    StringBuilder brackets;
                    String type;
                    int consumed_chars; // Shadows global var
                    brackets = new StringBuilder(); // Accumulate []'s
                    // Count opening brackets and look for optional size argument
                    for (n = 0; signature.charAt(n) == '['; n++) {
                        brackets.append("[]");
                    }
                    consumed_chars = n; // Remember value
                    // The rest of the string denotes a `<field_type>'
                    type = signatureToString(signature.substring(n), chopit);
                    //corrected concurrent private static field acess
                    //Utility.consumed_chars += consumed_chars; is replaced by:
                    int _temp = unwrap(Utility.consumed_chars) + consumed_chars;
                    wrap(Utility.consumed_chars, _temp);
                    return type + brackets.toString();
                }
                case 'V':
                    return "void";
                default:
                    throw new ClassFormatException("Invalid signature: `" + signature + "'");
            }
        } catch (StringIndexOutOfBoundsException e) { // Should never occur
            throw new ClassFormatException("Invalid signature: " + signature, e);
        }
    }

    /**
     * Convert (signed) byte to (unsigned) short value, i.e., all negative
     * values become positive.
     */
    private static final short byteToShort( byte b ) {
        return (b < 0) ? (short) (256 + b) : (short) b;
    }


    /** Convert bytes into hexadecimal string
     *
     * @param bytes an array of bytes to convert to hexadecimal
     * 
     * @return bytes as hexadecimal string, e.g. 00 FA 12 ...
     */
    public static final String toHexString( byte[] bytes ) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            short b = byteToShort(bytes[i]);
            String hex = Integer.toString(b, 0x10);
            if (b < 0x10) {
                buf.append('0');
            }
            buf.append(hex);
            if (i < bytes.length - 1) {
                buf.append(' ');
            }
        }
        return buf.toString();
    }

    /**
     * Fillup char with up to length characters with char `fill' and justify it left or right.
     *
     * @param str string to format
     * @param length length of desired string
     * @param left_justify format left or right
     * @param fill fill character
     * @return formatted string
     */
    public static final String fillup( String str, int length, boolean left_justify, char fill ) {
        int len = length - str.length();
        char[] buf = new char[(len < 0) ? 0 : len];
        for (int j = 0; j < buf.length; j++) {
            buf[j] = fill;
        }
        if (left_justify) {
            return str + new String(buf);
        }
        return new String(buf) + str;
    }

    // A-Z, g-z, _, $
    private static final int FREE_CHARS = 48;
    static int[] CHAR_MAP = new int[FREE_CHARS];
    static int[] MAP_CHAR = new int[256]; // Reverse map
    static {
        int j = 0;
        for (int i = 'A'; i <= 'Z'; i++) {
            CHAR_MAP[j] = i;
            MAP_CHAR[i] = j;
            j++;
        }
        for (int i = 'g'; i <= 'z'; i++) {
            CHAR_MAP[j] = i;
            MAP_CHAR[i] = j;
            j++;
        }
        CHAR_MAP[j] = '$';
        MAP_CHAR['$'] = j;
        j++;
        CHAR_MAP[j] = '_';
        MAP_CHAR['_'] = j;
    }

    /**
     * Escape all occurences of newline chars '\n', quotes \", etc.
     */
    public static final String convertString( String label ) {
        char[] ch = label.toCharArray();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < ch.length; i++) {
            switch (ch[i]) {
                case '\n':
                    buf.append("\\n");
                    break;
                case '\r':
                    buf.append("\\r");
                    break;
                case '\"':
                    buf.append("\\\"");
                    break;
                case '\'':
                    buf.append("\\'");
                    break;
                case '\\':
                    buf.append("\\\\");
                    break;
                default:
                    buf.append(ch[i]);
                    break;
            }
        }
        return buf.toString();
    }
}
