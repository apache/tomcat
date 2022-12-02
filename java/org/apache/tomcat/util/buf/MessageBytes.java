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
 */
package org.apache.tomcat.util.buf;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.res.StringManager;

/**
 * This class is used to represent a subarray of bytes in an HTTP message.
 * It represents all request/response elements. The byte/char conversions are
 * delayed and cached. Everything is recyclable.
 *
 * The object can represent a byte[], a char[], or a (sub) String. All
 * operations can be made in case sensitive mode or not.
 *
 * @author dac@eng.sun.com
 * @author James Todd [gonzo@eng.sun.com]
 * @author Costin Manolache
 */
public final class MessageBytes implements Cloneable, Serializable {

    private static final long serialVersionUID = 1L;

    private static final StringManager sm = StringManager.getManager(MessageBytes.class);

    // primary type ( whatever is set as original value )
    private int type = T_NULL;

    public static final int T_NULL = 0;
    /** getType() is T_STR if the the object used to create the MessageBytes
        was a String */
    public static final int T_STR  = 1;
    /** getType() is T_BYTES if the the object used to create the MessageBytes
        was a byte[] */
    public static final int T_BYTES = 2;
    /** getType() is T_CHARS if the the object used to create the MessageBytes
        was a char[] */
    public static final int T_CHARS = 3;

    public static final char[] EMPTY_CHAR_ARRAY = new char[0];

    private int hashCode=0;
    // did we compute the hashcode ?
    private boolean hasHashCode=false;

    // Internal objects to represent array + offset, and specific methods
    private final ByteChunk byteC=new ByteChunk();
    private final CharChunk charC=new CharChunk();

    // String
    private String strValue;

    /**
     * Creates a new, uninitialized MessageBytes object.
     * Use static newInstance() in order to allow
     *   future hooks.
     */
    private MessageBytes() {
    }

    /**
     * Construct a new MessageBytes instance.
     * @return the instance
     */
    public static MessageBytes newInstance() {
        return factory.newInstance();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public boolean isNull() {
        return type == T_NULL;
    }

    /**
     * Resets the message bytes to an uninitialized (NULL) state.
     */
    public void recycle() {
        type=T_NULL;
        byteC.recycle();
        charC.recycle();

        strValue=null;

        hasHashCode=false;
        hasLongValue=false;
    }


    /**
     * Sets the content to the specified subarray of bytes.
     *
     * @param b the bytes
     * @param off the start offset of the bytes
     * @param len the length of the bytes
     */
    public void setBytes(byte[] b, int off, int len) {
        byteC.setBytes( b, off, len );
        type=T_BYTES;
        hasHashCode=false;
        hasLongValue=false;
    }

    /**
     * Sets the content to be a char[]
     *
     * @param c the chars
     * @param off the start offset of the chars
     * @param len the length of the chars
     */
    public void setChars( char[] c, int off, int len ) {
        charC.setChars( c, off, len );
        type=T_CHARS;
        hasHashCode=false;
        hasLongValue=false;
    }

    /**
     * Set the content to be a string
     * @param s The string
     */
    public void setString( String s ) {
        strValue = s;
        hasHashCode = false;
        hasLongValue = false;
        if (s == null) {
            type = T_NULL;
        } else {
            type = T_STR;
        }
    }

    // -------------------- Conversion and getters --------------------

    /**
     * Compute the string value.
     * @return the string
     */
    @Override
    public String toString() {
        switch (type) {
            case T_NULL:
            case T_STR:
                // No conversion required
                break;
            case T_BYTES:
                type = T_STR;
                strValue = byteC.toString();
                break;
            case T_CHARS:
                type = T_STR;
                strValue = charC.toString();
                break;
        }

        return strValue;
    }

    //----------------------------------------
    /**
     * Return the type of the original content. Can be
     * T_STR, T_BYTES, T_CHARS or T_NULL
     * @return the type
     */
    public int getType() {
        return type;
    }

    /**
     * Returns the byte chunk, representing the byte[] and offset/length.
     * Valid only if T_BYTES or after a conversion was made.
     * @return the byte chunk
     */
    public ByteChunk getByteChunk() {
        return byteC;
    }

    /**
     * Returns the char chunk, representing the char[] and offset/length.
     * Valid only if T_CHARS or after a conversion was made.
     * @return the char chunk
     */
    public CharChunk getCharChunk() {
        return charC;
    }

    /**
     * Returns the string value.
     * Valid only if T_STR or after a conversion was made.
     * @return the string
     */
    public String getString() {
        return strValue;
    }

    /**
     * @return the Charset used for string&lt;-&gt;byte conversions.
     */
    public Charset getCharset() {
        return byteC.getCharset();
    }

    /**
     * Set the Charset used for string&lt;-&gt;byte conversions.
     * @param charset The charset
     */
    public void setCharset(Charset charset) {
        byteC.setCharset(charset);
    }


    /**
     * Convert to bytes and fill the ByteChunk with the converted value.
     */
    public void toBytes() {
        if (type == T_NULL) {
            byteC.recycle();
            return;
        }

        if (type == T_BYTES) {
            // No conversion required
            return;
        }

        if (!JreCompat.isJre16Available() && getCharset() == ByteChunk.DEFAULT_CHARSET) {
            if (type == T_CHARS) {
                toBytesSimple(charC.getChars(), charC.getStart(), charC.getLength());
            } else {
                // Must be T_STR
                char[] chars = strValue.toCharArray();
                toBytesSimple(chars, 0, chars.length);
            }
            return;
        }

        ByteBuffer bb;
        CharsetEncoder encoder = getCharset().newEncoder();
        encoder.onMalformedInput(CodingErrorAction.REPORT);
        encoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            if (type == T_CHARS) {
                bb = encoder.encode(CharBuffer.wrap(charC));
            } else {
                // Must be T_STR
                bb = encoder.encode(CharBuffer.wrap(strValue));
            }
        } catch (CharacterCodingException cce) {
            // Some calls to this conversion originate in application code and
            // the Servlet API methods do not declare a suitable exception that
            // can be thrown. Therefore stick with the uncaught exception type
            // used by the old, pre-Java 16 optimised version of this code.
            throw new IllegalArgumentException(cce);
        }

        byteC.setBytes(bb.array(), bb.arrayOffset(), bb.limit());
        type = T_BYTES;
    }


    /**
     * Simple conversion of chars to bytes.
     *
     * @throws IllegalArgumentException if any of the characters to convert are
     *                                  above code point 0xFF.
     */
    private void toBytesSimple(char[] chars, int start, int len) {
        byteC.recycle();
        byteC.allocate(len, byteC.getLimit());
        byte[] bytes = byteC.getBuffer();

        for (int i = 0; i < len; i++) {
            if (chars[i + start] > 255) {
                throw new IllegalArgumentException(sm.getString("messageBytes.illegalCharacter",
                        Character.toString(chars[i + start]), Integer.valueOf(chars[i + start])));
            } else {
                bytes[i] = (byte) chars[i + start];
            }
        }

        byteC.setEnd(len);
        type = T_BYTES;
    }


    /**
     * Convert to char[] and fill the CharChunk.
     *
     * Note: The conversion from bytes is not optimised - it converts to String
     *       first. However, Tomcat doesn't call this method to convert from
     *       bytes so there is no benefit from optimising that path.
     */
    public void toChars() {
        switch (type) {
            case T_NULL:
                charC.recycle();
                //$FALL-THROUGH$
            case T_CHARS:
                // No conversion required
                return;
            case T_BYTES:
                toString();
                //$FALL-THROUGH$
            case T_STR: {
                type = T_CHARS;
                char cc[] = strValue.toCharArray();
                charC.setChars(cc, 0, cc.length);
            }
        }
    }


    /**
     * Returns the length of the original buffer.
     * Note that the length in bytes may be different from the length
     * in chars.
     * @return the length
     */
    public int getLength() {
        if(type==T_BYTES) {
            return byteC.getLength();
        }
        if(type==T_CHARS) {
            return charC.getLength();
        }
        if(type==T_STR) {
            return strValue.length();
        }
        toString();
        if( strValue==null ) {
            return 0;
        }
        return strValue.length();
    }

    // -------------------- equals --------------------

    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return <code>true</code> if the comparison succeeded, <code>false</code> otherwise
     */
    public boolean equals(String s) {
        switch (type) {
        case T_STR:
            if (strValue == null) {
                return s == null;
            }
            return strValue.equals( s );
        case T_CHARS:
            return charC.equals( s );
        case T_BYTES:
            return byteC.equals( s );
        default:
            return false;
        }
    }

    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return <code>true</code> if the comparison succeeded, <code>false</code> otherwise
     */
    public boolean equalsIgnoreCase(String s) {
        switch (type) {
        case T_STR:
            if (strValue == null) {
                return s == null;
            }
            return strValue.equalsIgnoreCase( s );
        case T_CHARS:
            return charC.equalsIgnoreCase( s );
        case T_BYTES:
            return byteC.equalsIgnoreCase( s );
        default:
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageBytes) {
            return equals((MessageBytes) obj);
        }
        return false;
    }

    public boolean equals(MessageBytes mb) {
        switch (type) {
        case T_STR:
            return mb.equals( strValue );
        }

        if( mb.type != T_CHARS &&
            mb.type!= T_BYTES ) {
            // it's a string or int/date string value
            return equals( mb.toString() );
        }

        // mb is either CHARS or BYTES.
        // this is either CHARS or BYTES
        // Deal with the 4 cases ( in fact 3, one is symmetric)

        if( mb.type == T_CHARS && type==T_CHARS ) {
            return charC.equals( mb.charC );
        }
        if( mb.type==T_BYTES && type== T_BYTES ) {
            return byteC.equals( mb.byteC );
        }
        if( mb.type== T_CHARS && type== T_BYTES ) {
            return byteC.equals( mb.charC );
        }
        if( mb.type== T_BYTES && type== T_CHARS ) {
            return mb.byteC.equals( charC );
        }
        // can't happen
        return true;
    }


    /**
     * @return <code>true</code> if the message bytes starts with the specified string.
     * @param s the string
     * @param pos The start position
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
        switch (type) {
        case T_STR:
            if( strValue==null ) {
                return false;
            }
            if( strValue.length() < pos + s.length() ) {
                return false;
            }

            for( int i=0; i<s.length(); i++ ) {
                if( Ascii.toLower( s.charAt( i ) ) !=
                    Ascii.toLower( strValue.charAt( pos + i ))) {
                    return false;
                }
            }
            return true;
        case T_CHARS:
            return charC.startsWithIgnoreCase( s, pos );
        case T_BYTES:
            return byteC.startsWithIgnoreCase( s, pos );
        default:
            return false;
        }
    }


    // -------------------- Hash code  --------------------
    @Override
    public  int hashCode() {
        if( hasHashCode ) {
            return hashCode;
        }
        int code = 0;

        code=hash();
        hashCode=code;
        hasHashCode=true;
        return code;
    }

    // normal hash.
    private int hash() {
        int code=0;
        switch (type) {
        case T_STR:
            // We need to use the same hash function
            for (int i = 0; i < strValue.length(); i++) {
                code = code * 37 + strValue.charAt( i );
            }
            return code;
        case T_CHARS:
            return charC.hash();
        case T_BYTES:
            return byteC.hash();
        default:
            return 0;
        }
    }

    // Inefficient initial implementation. Will be replaced on the next
    // round of tune-up
    public int indexOf(String s, int starting) {
        toString();
        return strValue.indexOf( s, starting );
    }

    // Inefficient initial implementation. Will be replaced on the next
    // round of tune-up
    public int indexOf(String s) {
        return indexOf( s, 0 );
    }

    public int indexOfIgnoreCase(String s, int starting) {
        toString();
        String upper=strValue.toUpperCase(Locale.ENGLISH);
        String sU=s.toUpperCase(Locale.ENGLISH);
        return upper.indexOf( sU, starting );
    }

    /**
     * Copy the src into this MessageBytes, allocating more space if needed.
     * @param src The source
     * @throws IOException Writing overflow data to the output channel failed
     */
    public void duplicate( MessageBytes src ) throws IOException
    {
        switch( src.getType() ) {
        case MessageBytes.T_BYTES:
            type=T_BYTES;
            ByteChunk bc=src.getByteChunk();
            byteC.allocate( 2 * bc.getLength(), -1 );
            byteC.append( bc );
            break;
        case MessageBytes.T_CHARS:
            type=T_CHARS;
            CharChunk cc=src.getCharChunk();
            charC.allocate( 2 * cc.getLength(), -1 );
            charC.append( cc );
            break;
        case MessageBytes.T_STR:
            type=T_STR;
            String sc=src.getString();
            this.setString( sc );
            break;
        }
        setCharset(src.getCharset());
    }

    // -------------------- Deprecated code --------------------
    // efficient long
    // XXX used only for headers - shouldn't be stored here.
    private long longValue;
    private boolean hasLongValue=false;

    /**
     * Set the buffer to the representation of a long.
     * @param l The long
     */
    public void setLong(long l) {
        byteC.allocate(32, 64);
        long current = l;
        byte[] buf = byteC.getBuffer();
        int start = 0;
        int end = 0;
        if (l == 0) {
            buf[end++] = (byte) '0';
        }
        if (l < 0) {
            current = -l;
            buf[end++] = (byte) '-';
        }
        while (current > 0) {
            int digit = (int) (current % 10);
            current = current / 10;
            buf[end++] = HexUtils.getHex(digit);
        }
        byteC.setOffset(0);
        byteC.setEnd(end);
        // Inverting buffer
        end--;
        if (l < 0) {
            start++;
        }
        while (end > start) {
            byte temp = buf[start];
            buf[start] = buf[end];
            buf[end] = temp;
            start++;
            end--;
        }
        longValue=l;
        hasHashCode=false;
        hasLongValue=true;
        type=T_BYTES;
    }

    // Used for headers conversion
    /**
     * Convert the buffer to a long, cache the value.
     * @return the long value
     */
    public long getLong() {
        if( hasLongValue ) {
            return longValue;
        }

        switch (type) {
        case T_BYTES:
            longValue=byteC.getLong();
            break;
        default:
            longValue=Long.parseLong(toString());
        }

        hasLongValue=true;
        return longValue;

     }

    // -------------------- Future may be different --------------------

    private static final MessageBytesFactory factory=new MessageBytesFactory();

    private static class MessageBytesFactory {
        protected MessageBytesFactory() {
        }
        public MessageBytes newInstance() {
            return new MessageBytes();
        }
    }
}
