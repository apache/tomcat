/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.io.Writer;

import org.apache.tomcat.lite.io.IOOutputStream;
import org.apache.tomcat.lite.io.IOWriter;

/**
 * Implement character translation and buffering.
 *
 * The actual buffering happens in the IOBuffer - we translate the
 * chars as soon as we get them.
 *
 * For servlet compat you can set a buffer size and a flush() will happen
 * when the number of chars have been written. Note that writes at a lower
 * layer can be done and are not counted.
 *
 * @author Costin Manolache
 */
public class HttpWriter extends Writer {

    public static final String DEFAULT_ENCODING = "ISO-8859-1";
    public static final int DEFAULT_BUFFER_SIZE = 8*1024;

    // ----------------------------------------------------- Instance Variables
    HttpMessage message;

    /**
     * The byte buffer.
     */
    protected IOOutputStream bb;

    int bufferSize = DEFAULT_BUFFER_SIZE;

    /**
     * Number of chars written.
     */
    protected int wSinceFlush = 0;


    /**
     * Flag which indicates if the output buffer is closed.
     */
    protected boolean closed = false;

    /**
     * Encoding to use.
     * TODO: isn't it redundant ? enc, gotEnc, conv plus the enc in the bb
     */
    protected String enc;


    /**
     * Encoder is set.
     */
    protected boolean gotEnc = false;


    /**
     * List of encoders. The writer is reused - the encoder mapping
     * avoids creating expensive objects. In future it'll contain nio.Charsets
     */
    //protected Map<String, C2BConverter> encoders = new HashMap();


    /**
     * Current char to byte converter. TODO: replace with Charset
     */
    private IOWriter conv;

    /**
     * Suspended flag. All output bytes will be swallowed if this is true.
     */
    protected boolean suspended = false;


    // ----------------------------------------------------------- Constructors


    /**
     * Default constructor. Allocate the buffer with the default buffer size.
     * @param out
     */
    public HttpWriter(HttpMessage message, IOOutputStream out,
            IOWriter conv) {
        this.message = message;
        bb = out;
        this.conv = conv;
    }

    // ------------------------------------------------------------- Properties


    /**
     * Is the response output suspended ?
     *
     * @return suspended flag value
     */
    public boolean isSuspended() {
        return this.suspended;
    }


    /**
     * Set the suspended flag.
     *
     * @param suspended New suspended flag value
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Recycle the output buffer.
     */
    public void recycle() {
        wSinceFlush = 0;
        bb.recycle();
        closed = false;
        suspended = false;

//        if (conv != null) {
//            conv.recycle();
//        }

        gotEnc = false;
        enc = null;
    }

    public void close()
        throws IOException {

        if (closed)
            return;
        if (suspended)
            return;

        push();
        closed = true;

        bb.close();
    }


    /**
     * Flush bytes or chars contained in the buffer.
     *
     * @throws IOException An underlying IOException occurred
     */
    public void flush()
            throws IOException {
        push();
        bb.flush(); // will send the data
        wSinceFlush = 0;
    }

    /**
     * Flush chars to the byte buffer.
     */
    public void push()
        throws IOException {

        if (suspended)
            return;
        getConv().push();

    }


    private void updateSize(int cnt) throws IOException {
        wSinceFlush += cnt;
        if (wSinceFlush > bufferSize) {
            flush();
        }
    }

    public void write(int c)
            throws IOException {
        if (suspended)
            return;
        getConv().write(c);
        updateSize(1);
    }


    public void write(char c[])
            throws IOException {
        write(c, 0, c.length);
    }


    public void write(char c[], int off, int len)
            throws IOException {
        if (suspended)
            return;
        getConv().write(c, off, len);
        updateSize(len);
    }


    public void write(StringBuffer sb)
            throws IOException {
        if (suspended)
            return;
        int len = sb.length();
        getConv().write(sb.toString());
        updateSize(len);
    }


    /**
     * Append a string to the buffer
     */
    public void write(String s, int off, int len)
        throws IOException {
        if (suspended)
            return;
        if (s==null)
            s="null";
        getConv().write( s, off, len );
        updateSize(len);
    }


    public void write(String s)
            throws IOException {
        if (s==null)
            s="null";
        write(s, 0, s.length());
    }

    public void println() throws IOException {
        write("\n");
    }

    public void println(String s) throws IOException {
        write(s);
        write("\n");
    }

    public void print(String s) throws IOException {
        write(s);
    }

    public void checkConverter()
            throws IOException {
//        if (gotEnc) {
//            return;
//        }
//        if (enc == null) {
//            enc = message.getCharacterEncoding();
//        }
//
//        gotEnc = true;
//        if (enc == null)
//            enc = DEFAULT_ENCODING;
//        conv = (C2BConverter) encoders.get(enc);
//
//        if (conv == null) {
//            conv = C2BConverter.newConverter(message.getBodyOutputStream(),
//                    enc);
//            encoders.put(enc, conv);
//
//        }
    }

    public int getWrittenSinceFlush() {
        return wSinceFlush;
    }


    public void setBufferSize(int size) {
        if (size > bufferSize) {
            bufferSize = size;
        }
    }

    /**
     *  Clear any data that was buffered.
     */
    public void reset() {
        if (conv != null) {
            conv.recycle();
        }
        wSinceFlush = 0;
        gotEnc = false;
        enc = null;
        bb.reset();
    }


    public int getBufferSize() {
        return bufferSize;
    }

    protected IOWriter getConv() throws IOException {
        checkConverter();
        return conv;
    }

    public void println(CharSequence key) throws IOException {
        // TODO: direct
        println(key.toString());
    }

}
