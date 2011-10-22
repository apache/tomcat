/*
 */
package org.apache.tomcat.lite.io;

import java.io.Serializable;
import java.nio.CharBuffer;

/**
 * Wraps a char[].
 *
 * Doesn't provide any mutation methods. Classes in this package
 * have access to the buffer, for conversions.
 *
 *
 * @author Costin Manolache
 */
public class CBucket implements CharSequence, Comparable, Serializable {
    protected char value[];

    protected int start;

    protected int end;

    // Reused.
    protected CharBuffer cb;

    // cache
    protected String strValue;
    protected int hash;

    public CBucket() {
    }

    /**
     * Used by IOWriter for conversion. Will not modify the content.
     */
    CharBuffer getNioBuffer() {
        if (cb == null || cb.array() != value) {
            cb = CharBuffer.wrap(value, start, end - start);
        } else {
            cb.position(start);
            cb.limit(end);
        }
        return cb;
    }

    public void recycle() {
        start = 0;
        end = 0;
        value = null;
        strValue = null;
        hash = 0;
    }

    public String toString() {
        if (null == value) {
            return null;
        } else if (end - start == 0) {
            return "";
        }
        if (strValue == null) {
            strValue = new String(value, start, end - start);
        }
        return strValue;
    }

    /**
     * Same as String
     */
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            int off = start;
            char val[] = value;

            for (int i = start; i < end; i++) {
                h = 31*h + val[off++];
            }
            hash = h;
        }
        return h;
    }

    public long getLong() {
        return parseLong(value, start, end - start);
    }

    public int getInt() {
        return parseInt(value, start, end - start);
    }

    public static int parseInt(char[] b, int off, int len)
        throws NumberFormatException
    {
        int c;

        if (b == null || len <= 0 || !BBuffer.isDigit(c = b[off++])) {
            throw new NumberFormatException();
        }

        int n = c - '0';

        while (--len > 0) {
            if (!BBuffer.isDigit(c = b[off++])) {
                throw new NumberFormatException();
            }
            n = n * 10 + c - '0';
        }

        return n;
    }


    public static long parseLong(char[] b, int off, int len)
        throws NumberFormatException
    {
        int c;

        if (b == null || len <= 0 || !BBuffer.isDigit(c = b[off++])) {
            throw new NumberFormatException();
        }

        long n = c - '0';
        long m;

        while (--len > 0) {
            if (!BBuffer.isDigit(c = b[off++])) {
                throw new NumberFormatException();
            }
            m = n * 10 + c - '0';

            if (m < n) {
                // Overflow
                throw new NumberFormatException();
            } else {
                n = m;
            }
        }

        return n;
    }


    /**
     * Compares the message bytes to the specified String object.
     *
     * @param s
     *            the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(String s) {
        char[] c = value;
        int len = end - start;
        if (c == null || len != s.length()) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares the message bytes to the specified String object.
     *
     * @param s
     *            the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(String s) {
        char[] c = value;
        int len = end - start;
        if (c == null || len != s.length()) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (BBuffer.toLower(c[off++]) != BBuffer.toLower(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj instanceof CBuffer) {
            CBuffer cc = (CBuffer) obj;
            return equals(cc.value, cc.start, cc.length());
        } else if (obj instanceof String) {
            return equals((String)obj);
        }
        return false;
    }

    public boolean equals(char b2[], int off2, int len2) {
        char b1[] = value;
        if (b1 == null && b2 == null)
            return true;

        if (b1 == null || b2 == null || end - start != len2) {
            return false;
        }
        int off1 = start;
        int len = end - start;
        while (len-- > 0) {
            if (b1[off1++] != b2[off2++]) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(byte b2[], int off2, int len2) {
        char b1[] = value;
        if (b2 == null && b1 == null)
            return true;

        if (b1 == null || b2 == null || end - start != len2) {
            return false;
        }
        int off1 = start;
        int len = end - start;

        while (len-- > 0) {
            if (b1[off1++] != (char) b2[off2++]) {
                return false;
            }
        }
        return true;
    }


    /**
     * Returns true if the message bytes starts with the specified string.
     *
     * @param s
     *            the string
     */
    public boolean startsWith(String s) {
        char[] c = value;
        int len = s.length();
        if (c == null || len > end - start) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     *
     * @param s
     *            the string
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
        char[] c = value;
        int len = s.length();
        if (c == null || len + pos > end - start) {
            return false;
        }
        int off = start + pos;
        for (int i = 0; i < len; i++) {
            if (BBuffer.toLower(c[off++]) != BBuffer.toLower(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public int indexOf(char c) {
        return indexOf(c, start);
    }

    public int lastIndexOf(char c) {
        return lastIndexOf(c, 0, end - start);
    }

    /**
     */
    public int lastIndexOf(char c, int off, int len) {
        char[] buf = value;
        int slash = -1;
        for (int i = start + len - 1; i >= start + off; i--) {
            if (buf[i] == c) {
                slash = i - start;
                break;
            }
        }
        return slash;
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     *
     * @param c
     *            the character
     */
    public int indexOf(char c, int starting) {
        int ret = indexOf(value, start + starting, end, c);
        return (ret >= start) ? ret - start : -1;
    }

    public static int indexOf(char chars[], int off, int cend, char qq) {
        while (off < cend) {
            char b = chars[off];
            if (b == qq)
                return off;
            off++;
        }
        return -1;
    }

    public int indexOf(String src) {
        return indexOf(src, 0, src.length(), 0);
    }

    public int indexOf(String src, int srcOff, int srcLen, int myOff) {
        char first = src.charAt(srcOff);

        // Look for first char
        int srcEnd = srcOff + srcLen;

        for (int i = myOff + start; i <= (end - srcLen); i++) {
            if (value[i] != first)
                continue;
            // found first char, now look for a match
            int myPos = i + 1;
            for (int srcPos = srcOff + 1; srcPos < srcEnd;) {
                if (value[myPos++] != src.charAt(srcPos++))
                    break;
                if (srcPos == srcEnd)
                    return i - start; // found it
            }
        }
        return -1;
    }

    public char lastChar() {
        return value[end - 1];
    }

    public char charAt(int index) {
        return value[index + start];
    }

    public void wrap(char[] buff, int start, int end) {
        if (value != null) {
            throw new RuntimeException("Can wrap only once");
        }
        this.value = buff;
        this.start = start;
        this.end = end;
    }

    public CharSequence subSequence(int sstart, int send) {
        CBucket seq = new CBucket();
        seq.wrap(this.value, start + sstart, start + send);
        return seq;
    }

    public int length() {
        return end - start;
    }

    @Override
    public int compareTo(Object o) {
        // Code based on Harmony
        if (o instanceof CBuffer) {
            CBuffer dest = (CBuffer) o;
            int o1 = start, o2 = dest.start, result;
            int len = end - start;
            int destLen = dest.end - dest.start;
            int fin = (len < destLen ?
                    end : start + destLen);
            char[] target = dest.value;
            while (o1 < fin) {
                if ((result = value[o1++] - target[o2++]) != 0) {
                    return result;
                }
            }
            return len - destLen;

        } else if (o instanceof CharSequence) {
            CharSequence dest = (CharSequence) o;
            int o1 = start, o2 = 0, result;
            int len = end - start;
            int destLen = dest.length();
            int fin = (len < destLen ?
                    end : start + destLen);
            while (o1 < fin) {
                if ((result = value[o1++] - dest.charAt(o2++)) != 0) {
                    return result;
                }
            }
            return len - destLen;

        } else {
            throw new RuntimeException("CompareTo not supported " + o);
        }
    }

    /**
     * Compare given char chunk with String ignoring case.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    public final int compareIgnoreCase(String compareTo) {
        int result = 0;
        char[] c = value;
        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (BBuffer.toLower(c[i + start]) > BBuffer.toLower(compareTo.charAt(i))) {
                result = 1;
            } else if (BBuffer.toLower(c[i + start]) < BBuffer.toLower(compareTo.charAt(i))) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }

    /**
     * Compare given char chunk with String.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    public final int compare(String compareTo) {
        int result = 0;
        char[] c = value;
        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (c[i + start] > compareTo.charAt(i)) {
                result = 1;
            } else if (c[i + start] < compareTo.charAt(i)) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }

    public int getExtension(CBuffer ext, char slashC, char dotC) {
        int slash = lastIndexOf(slashC);
        if (slash < 0) {
            slash = 0;
        }
        int dot = lastIndexOf(dotC, slash, length());
        if (dot < 0) {
            return -1;
        }
        ext.wrap(this, dot + 1, length());
        return dot;
    }

    /**
     * Find the position of the nth slash, in the given char chunk.
     */
    public final int nthSlash(int n) {
        char[] c = value;
        int pos = start;
        int count = 0;

        while (pos < end) {
            if ((c[pos++] == '/') && ((++count) == n)) {
                pos--;
                break;
            }
        }

        return pos - start;
    }


    public boolean hasUpper() {
        for (int i = start; i < end; i++) {
            char c = value[i];
            if (c < 0x7F && BBuffer.isUpper(c)) {
                return true;
            }
        }
        return false;
    }

}
