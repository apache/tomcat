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

package org.apache.coyote.servlet;

import java.io.IOException;
import java.io.Writer;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.C2BConverter;
import org.apache.tomcat.util.buf.CharChunk;

/**
 * Implement buffering and character translation acording to the
 * servlet spec.
 *
 * This class handles both chars and bytes.
 *
 * It is tightly integrated with servlet response, sending headers
 * and updating the commit state.
 *
 * TODO: add 'extension' interface that allows direct access to
 * the async connector non-copy non-blocking queue. Same for the
 * OutputStream. Maybe switch the buffer to the brigade.
 *
 * @author Costin Manolache
 */
public class BodyWriter extends Writer {

    // used in getWriter, until a method is added to res.
    protected static final int WRITER_NOTE = 3;


    private ByteChunk.ByteOutputChannel byteFlusher =
        new ByteChunk.ByteOutputChannel() {

        @Override
        public void realWriteBytes(byte[] cbuf, int off, int len)
                throws IOException {
            BodyWriter.this.realWriteBytes(cbuf, off, len);
        }
    };

    private CharChunk.CharOutputChannel charFlusher =
        new CharChunk.CharOutputChannel() {
        @Override
        public void realWriteChars(char[] cbuf, int off, int len)
                throws IOException {
            BodyWriter.this.realWriteChars(cbuf, off, len);
        }
    };


    public static final String DEFAULT_ENCODING =
        org.apache.coyote.Constants.DEFAULT_CHARACTER_ENCODING;
    public static final int DEFAULT_BUFFER_SIZE = 8*1024;


    // The buffer can be used for byte[] and char[] writing
    // ( this is needed to support ServletOutputStream and for
    // efficient implementations of templating systems )
    public final int CHAR_STATE = 1;
    public final int BYTE_STATE = 2;

    boolean headersSent = false;
    // ----------------------------------------------------- Instance Variables
    ServletResponseImpl res;

    /**
     * The byte buffer.
     */
    protected ByteChunk bb;


    /**
     * The chunk buffer.
     */
    protected CharChunk cb;


    /**
     * State of the output buffer.
     */
    protected int state = 0;


    /**
     * Number of bytes written.
     */
    protected int bytesWritten = 0;


    /**
     * Number of chars written.
     */
    protected int charsWritten = 0;


    /**
     * Flag which indicates if the output buffer is closed.
     */
    protected boolean closed = false;


    /**
     * Do a flush on the next operation.
     */
    protected boolean doFlush = false;


    /**
     * Byte chunk used to output bytes. This is just used to wrap the byte[]
     * to match the coyote OutputBuffer interface
     */
    protected ByteChunk outputChunk = new ByteChunk();


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
    protected HashMap encoders = new HashMap();


    /**
     * Current char to byte converter. TODO: replace with Charset
     */
    protected C2BConverter conv;

    /**
     * Suspended flag. All output bytes will be swallowed if this is true.
     */
    protected boolean suspended = false;

    private Connector connector;


    // ----------------------------------------------------------- Constructors


    /**
     * Default constructor. Allocate the buffer with the default buffer size.
     */
    public BodyWriter() {

        this(DEFAULT_BUFFER_SIZE);

    }


    /**
     * Alternate constructor which allows specifying the initial buffer size.
     *
     * @param size Buffer size to use
     */
    public BodyWriter(int size) {

        bb = new ByteChunk(size);
        bb.setLimit(size);
        bb.setByteOutputChannel(byteFlusher);
        cb = new CharChunk(size);
        cb.setCharOutputChannel(charFlusher);
        cb.setLimit(size);

    }

    public void setConnector(Connector c, ServletResponseImpl res) {
        this.res = res;
        this.connector = c;
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

        state = BYTE_STATE;
        headersSent = false;
        bytesWritten = 0;
        charsWritten = 0;

        cb.recycle();
        bb.recycle();
        closed = false;
        suspended = false;

        if (conv!= null) {
            conv.recycle();
        }

        gotEnc = false;
        enc = null;

    }


    /**
     * Close the output buffer. This tries to calculate the response size if
     * the response has not been committed yet.
     *
     * @throws IOException An underlying IOException occurred
     */
    public void close()
        throws IOException {

        if (closed)
            return;
        if (suspended)
            return;

        if (state == CHAR_STATE) {
            cb.flushBuffer();
            state = BYTE_STATE;
        }
        connector.beforeClose(res, bb.getLength());

        doFlush(false);
        closed = true;

        connector.finishResponse(res);
    }


    /**
     * Flush bytes or chars contained in the buffer.
     *
     * @throws IOException An underlying IOException occurred
     */
    public void flush()
        throws IOException {
        doFlush(true);
    }

    /**
     * Flush bytes or chars contained in the buffer.
     *
     * @throws IOException An underlying IOException occurred
     */
    protected void doFlush(boolean realFlush)
        throws IOException {

        if (suspended)
            return;

        doFlush = true;
        if (!headersSent) {
            // If the buffers are empty, commit the response header
            connector.sendHeaders(res);
            headersSent = true;
        }
        if (state == CHAR_STATE) {
            cb.flushBuffer();
            state = BYTE_STATE;
        }
        if (state == BYTE_STATE) {
            bb.flushBuffer();
        }
        doFlush = false;

        if (realFlush) {
            connector.realFlush(res);
        }

    }


    // ------------------------------------------------- Bytes Handling Methods


    /**
     * Sends the buffer data to the client output, checking the
     * state of Response and calling the right interceptors.
     *
     * @param buf Byte buffer to be written to the response
     * @param off Offset
     * @param cnt Length
     *
     * @throws IOException An underlying IOException occurred
     */
    private void realWriteBytes(byte buf[], int off, int cnt)
        throws IOException {

        if (closed)
            return;

        // If we really have something to write
        if (cnt > 0) {
            // real write to the adapter
            outputChunk.setBytes(buf, off, cnt);
            try {
                connector.doWrite(res, outputChunk);
            } catch (IOException e) {
                // An IOException on a write is almost always due to
                // the remote client aborting the request.  Wrap this
                // so that it can be handled better by the error dispatcher.
                throw new ClientAbortException(e);
            }
        }

    }


    public void write(byte b[], int off, int len) throws IOException {

        if (suspended)
            return;

        if (state == CHAR_STATE)
            cb.flushBuffer();
        state = BYTE_STATE;
        writeBytes(b, off, len);

    }


    private void writeBytes(byte b[], int off, int len)
        throws IOException {

        if (closed)
            return;

        bb.append(b, off, len);
        bytesWritten += len;

        // if called from within flush(), then immediately flush
        // remaining bytes
        if (doFlush) {
            bb.flushBuffer();
        }

    }


    public void writeByte(int b)
        throws IOException {

        if (suspended)
            return;

        if (state == CHAR_STATE)
            cb.flushBuffer();
        state = BYTE_STATE;

        bb.append( (byte)b );
        bytesWritten++;

    }


    // ------------------------------------------------- Chars Handling Methods


    public void write(int c)
        throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;

        cb.append((char) c);
        charsWritten++;

    }


    public void write(char c[])
        throws IOException {

        if (suspended)
            return;

        write(c, 0, c.length);

    }


    public void write(char c[], int off, int len)
        throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;

        cb.append(c, off, len);
        charsWritten += len;

    }


    public void write(StringBuilder sb)
        throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;

        int len = sb.length();
        charsWritten += len;
        cb.append(sb.toString());

    }


    /**
     * Append a string to the buffer
     */
    public void write(String s, int off, int len)
        throws IOException {

        if (suspended)
            return;

        state=CHAR_STATE;

        charsWritten += len;
        if (s==null)
            s="null";
        cb.append( s, off, len );

    }


    public void write(String s)
        throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;
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

    public void flushChars()
        throws IOException {

        cb.flushBuffer();
        state = BYTE_STATE;

    }


    public boolean flushCharsNeeded() {
        return state == CHAR_STATE;
    }


    public void setEncoding(String s) {
        enc = s;
    }


    private void realWriteChars(char c[], int off, int len)
        throws IOException {

        if (!gotEnc)
            setConverter();

        conv.convert(c, off, len);
        conv.flushBuffer();     // ???

    }


    public void checkConverter()
        throws IOException {

        if (!gotEnc)
            setConverter();

    }


    protected void setConverter()
        throws IOException {

        enc = res.getCharacterEncoding();

        gotEnc = true;
        if (enc == null)
            enc = DEFAULT_ENCODING;
        conv = (C2BConverter) encoders.get(enc);
        if (conv == null) {

            if (System.getSecurityManager() != null){
                try{
                    conv = (C2BConverter)AccessController.doPrivileged(
                            new PrivilegedExceptionAction(){

                                public Object run() throws IOException{
                                    return new C2BConverter(bb, enc);
                                }

                            }
                    );
                }catch(PrivilegedActionException ex){
                    Exception e = ex.getException();
                    if (e instanceof IOException)
                        throw (IOException)e;
                }
            } else {
                conv = new C2BConverter(bb, enc);
            }

            encoders.put(enc, conv);

        }
    }


    // --------------------  BufferedOutputStream compatibility


    /**
     * Real write - this buffer will be sent to the client
     */
    public void flushBytes()
        throws IOException {

        bb.flushBuffer();

    }


    public int getBytesWritten() {
        return bytesWritten;
    }


    public int getCharsWritten() {
        return charsWritten;
    }


    public int getContentWritten() {
        return bytesWritten + charsWritten;
    }


    /**
     * True if this buffer hasn't been used ( since recycle() ) -
     * i.e. no chars or bytes have been added to the buffer.
     */
    public boolean isNew() {
        return (bytesWritten == 0) && (charsWritten == 0);
    }


    public void setBufferSize(int size) {
        if (size > bb.getLimit()) {// ??????
            bb.setLimit(size);
        }
    }


    public void reset() {

        //count=0;
        bb.recycle();
        bytesWritten = 0;
        cb.recycle();
        charsWritten = 0;
        gotEnc = false;
        enc = null;
        state = BYTE_STATE;
    }


    public int getBufferSize() {
        return bb.getLimit();
    }

    public ByteChunk getByteBuffer() {
      return outputChunk;
    }

}
//{
//    public abstract int getBytesWritten();
//    public abstract int getCharsWritten();
//    public abstract void recycle();
//    public abstract void setSuspended(boolean suspended);
//    public abstract boolean isSuspended();
//
//    public abstract void reset();
//    public abstract int getBufferSize();
//    public abstract void setBufferSize(int n);
//    public abstract void checkConverter() throws IOException;
//    public boolean isNew() {
//        return getBytesWritten() == 0 && getCharsWritten() == 0;
//    }
//    public abstract void write(byte[] b, int off, int len) throws IOException;
//    public abstract void writeByte(int b) throws IOException;
//
//}