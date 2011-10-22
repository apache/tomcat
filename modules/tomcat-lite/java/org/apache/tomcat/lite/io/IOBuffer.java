/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.logging.Logger;


// TODO: append() will trigger callbacks - do it explicitely !!!
// TODO: queue() shouldn't modify the buffer


/**
 * A list of data buckets.
 *
 * @author Costin Manolache
 */
public class IOBuffer {
    static Logger log = Logger.getLogger("IOBrigade");

    static int ALLOC_SIZE = 8192;
    long defaultTimeout = Long.MAX_VALUE;

    private LinkedList<BBucket> buffers = new LinkedList<BBucket>();

    // close() has been called for out,
    // or EOF/FIN received for in. It may still have data.
    boolean closeQueued;

    // Will be signalled (open) when there is data in the buffer.
    // also used to sync on.
    FutureCallbacks<IOBuffer> hasDataLock = new FutureCallbacks<IOBuffer>() {
        protected boolean isSignaled() {
            return hasData();
        }
    };

    // may be null
    protected IOChannel ch;

    // Support for appending - needs improvements.
    // appendable buffer is part of the buffer list if it has
    // data, and kept here if empty.
    BBuffer appendable;
    boolean appending = false;
    ByteBuffer writeBuffer;


    public IOBuffer() {
    }

    public IOBuffer(IOChannel ch) {
        this.ch = ch;
    }

    public IOChannel getChannel() {
        return ch;
    }

    // ===== Buffer access =====


    /**
     * Return first non-empty buffer.
     *
     * The append buffer is part of the buffer list, and is left alone and
     * empty.
     *
     * @return
     */
    public BBucket peekFirst() {
        synchronized (buffers) {
            BBucket o = (buffers.size() == 0) ? null : buffers.getFirst();

            while (true) {
                boolean empty = o == null || isEmpty(o);
                if (o == null) {
                    //hasDataLock.reset();
                    return null; // no data in buffers
                }
                // o != null
                if (empty) {
                    buffers.removeFirst();
                    o = (buffers.size() == 0) ? null : buffers.getFirst();
                } else {
                    return o;
                }
            }
        }
    }

    public BBucket peekBucket(int idx) {
        synchronized (buffers) {
            return buffers.get(idx);
        }
    }


    public void advance(int len) {
        while (len > 0) {
            BBucket first = peekFirst();
            if (first == null) {
                return;
            }
            if (len > first.remaining()) {
                len -= first.remaining();
                first.position(first.limit());
            } else {
                first.position(first.position() + len);
                len = 0;
            }
        }
    }

    public void queue(String s) throws IOException {
        // TODO: decode with prober charset
        byte[] bytes = s.getBytes("UTF8");
        queueInternal(BBuffer.wrapper(bytes, 0, bytes.length));
    }

    public void queue(BBuffer bc) throws IOException {
        queueInternal(bc);
    }

    public void queue(Object bb) throws IOException {
        queueInternal(bb);
    }

    private void queueInternal(Object bb) throws IOException {
        if (closeQueued) {
            throw new IOException("Closed");
        }
        synchronized (buffers) {
            if (appending) {
                throw new RuntimeException("Unexpected queue while " +
                                "appending");
            }
            BBucket add = wrap(bb);
            buffers.add(add);
            //log.info("QUEUED: " + add.remaining() + " " + this);
            notifyDataAvailable(add);
        }

    }

    public int getBufferCount() {
        peekFirst();
        synchronized (buffers) {
            return buffers.size();
        }
    }

    public void clear() {
        synchronized (buffers) {
            buffers.clear();
        }
    }

    public void recycle() {
        closeQueued = false;
        clear();
        // Normally unlocked
        hasDataLock.recycle();

        appending = false;
        appendable = null;
    }

    // ===================
    /**
     * Closed for append. It may still have data.
     * @return
     */
    public boolean isClosedAndEmpty() {
        return closeQueued && 0 == getBufferCount();
    }


    /**
     * Mark as closed - but will not send data.
     */
    public void close() throws IOException {
        if (closeQueued) {
            return;
        }
        closeQueued = true;
        notifyDataAvailable(null);
    }


    private boolean isEmpty(BBucket o) {
        if (o instanceof BBucket &&
                ((BBucket) o).remaining() == 0) {
            return true;
        }
        return false;
    }

    private BBucket wrap(Object src) {
        if (src instanceof byte[]) {
            return BBuffer.wrapper((byte[]) src, 0, ((byte[]) src).length);
        }
        if (src instanceof ByteBuffer) {
            //return src;
            ByteBuffer bb = (ByteBuffer) src;
            return BBuffer.wrapper(bb.array(), bb.position(),
                        bb.remaining());
        }
        if (src instanceof byte[]) {
            byte[] bb = (byte[]) src;
            return BBuffer.wrapper(bb, 0, bb.length);
        }
        return (BBucket) src;
    }

    protected void notifyDataAvailable(Object bb) throws IOException {
        synchronized (hasDataLock) {
            hasDataLock.signal(this); // or bb ?
        }
    }

    public boolean hasData() {
        return closeQueued || peekFirst() != null;
    }

    public void waitData(long timeMs) throws IOException {
        if (timeMs == 0) {
            timeMs = defaultTimeout;
        }
        synchronized (hasDataLock) {
            if (hasData()) {
                return;
            }
            hasDataLock.reset();
        }
        hasDataLock.waitSignal(timeMs);
    }


    public boolean isAppendClosed() {
        return closeQueued;
    }

    // =================== Helper methods ==================

    /**
     * Non-blocking read.
     *
     * @return -1 if EOF, -2 if no data available, or 0..255 for normal read.
     */
    public int read() throws IOException {
        if (isClosedAndEmpty()) {
            return -1;
        }
        BBucket bucket = peekFirst();
        if (bucket == null) {
            return -2;
        }
        int res = bucket.array()[bucket.position()];
        bucket.position(bucket.position() + 1);
        return res & 0xFF;
    }

    public int peek() throws IOException {
        BBucket bucket = peekFirst();
        if (bucket == null) {
            return -1;
        }
        int res = bucket.array()[bucket.position()];
        return res;
    }

    public int find(char c) {
        int pos = 0;
        for (int i = 0; i < buffers.size(); i++) {
            BBucket bucket = buffers.get(i);
            if (bucket == null || bucket.remaining() == 0) {
                continue;
            }
            int found= BBuffer.findChar(bucket.array(), bucket.position(),
                    bucket.limit(), c);
            if (found >= 0) {
                return pos + found;
            }
            pos += bucket.remaining();
        }
        return -1;
    }

    public int readLine(BBuffer bc) throws IOException {
        return readToDelim(bc, '\n');
    }

    /**
     * Copy up to and including "delim".
     *
     * @return number of bytes read, or -1 for end of stream.
     */
    int readToDelim(BBuffer bc, int delim) throws IOException {
        int len = 0;
        for (int idx = 0; idx < buffers.size(); idx++) {
            BBucket bucket = buffers.get(idx);
            if (bucket == null || bucket.remaining() == 0) {
                continue;
            }
            byte[] data = bucket.array();
            int end = bucket.limit();
            int start = bucket.position();
            for (int i = start; i < end; i++) {
                byte chr = data[i];
                bc.put(chr);
                if (chr == delim) {
                    bucket.position(i + 1);
                    len += (i - start + 1);
                    return len;
                }
            }
            bucket.position(end); // empty - should be removed
        }
        if (len == 0 && isClosedAndEmpty()) {
            return -1;
        }
        return len;
    }


    public int write(ByteBuffer bb) throws IOException {
        int len = bb.remaining();
        int pos = bb.position();
        if (len == 0) {
            return 0;
        }
        append(bb);
        bb.position(pos + len);
        return len;
    }

    public int read(byte[] buf, int off, int len) throws IOException {
        if (isClosedAndEmpty()) {
            return -1;
        }
        int rd = 0;
        while (true) {
            BBucket bucket = peekFirst();
            if (bucket == null) {
                return rd;
            }
            int toCopy = Math.min(len, bucket.remaining());
            System.arraycopy(bucket.array(), bucket.position(),
                    buf, off + rd, toCopy);
            bucket.position(bucket.position() + toCopy);
            rd += toCopy;
            len -= toCopy;
            if (len == 0) {
                return rd;
            }
        }

    }

    public int read(BBuffer bb, int len) throws IOException {
        bb.makeSpace(len);
        int rd = read(bb.array(), bb.limit(), len);
        if (rd < 0) {
            return rd;
        }
        bb.limit(bb.limit() + rd);
        return rd;
    }

    /**
     * Non-blocking read.
     */
    public int read(ByteBuffer bb) {
        if (isClosedAndEmpty()) {
            return -1;
        }
        int len = 0;
        while (true) {
            int space = bb.remaining(); // to append
            if (space == 0) {
                return len;
            }
            BBucket first = peekFirst();
            if (first == null) {
                return len;
            }
            BBucket iob = ((BBucket) first);
            if (space > iob.remaining()) {
                space = iob.remaining();
            }
            bb.put(iob.array(), iob.position(), space);

            iob.position(iob.position() + space);
            iob.release();
            len += space;
        }
    }


    public BBuffer readAll(BBuffer chunk) throws IOException {
        if (chunk == null) {
            chunk = allocate();
        }
        while (true) {
            if (isClosedAndEmpty()) {
                return chunk;
            }
            BBucket first = peekFirst();
            if (first == null) {
                return chunk;
            }
            BBucket iob = ((BBucket) first);
            chunk.append(iob.array(), iob.position(), iob.remaining());
            iob.position(iob.position() + iob.remaining());
            iob.release();

        }
    }

    private BBuffer allocate() {
        int size = 0;
        for (int i = 0; i < getBufferCount(); i++) {
            BBucket first = peekBucket(i);
            if (first != null) {
                size += first.remaining();
            }
        }
        return BBuffer.allocate(size);
    }

    public BBuffer copyAll(BBuffer chunk) throws IOException {
        if (chunk == null) {
            chunk = allocate();
        }
        for (int i = 0; i < getBufferCount(); i++) {
            BBucket iob = peekBucket(i);
            chunk.append(iob.array(), iob.position(), iob.remaining());
        }
        return chunk;
    }

    public IOBuffer append(InputStream is) throws IOException {
        while (true) {
            ByteBuffer bb = getWriteBuffer();
            int rd = is.read(bb.array(), bb.position(), bb.remaining());
            if (rd <= 0) {
                return this;
            }
            bb.position(bb.position() + rd);
            releaseWriteBuffer(rd);
        }
    }

    public IOBuffer append(BBuffer bc) throws IOException {
        return append(bc.array(), bc.getStart(), bc.getLength());
    }

    public IOBuffer append(byte[] data) throws IOException {
        return append(data, 0, data.length);
    }

    public IOBuffer append(byte[] data, int start, int len) throws IOException {
        if (closeQueued) {
            throw new IOException("Closed");
        }
        ByteBuffer bb = getWriteBuffer();

        int i = start;
        int end = start + len;
        while (i < end) {
            int rem = Math.min(end - i, bb.remaining());
            // to write
            bb.put(data, i, rem);
            i += rem;
            if (bb.remaining() < 8) {
                releaseWriteBuffer(1);
                bb = getWriteBuffer();
            }
        }

        releaseWriteBuffer(1);
        return this;
    }

    public IOBuffer append(int data) throws IOException {
        if (closeQueued) {
            throw new IOException("Closed");
        }
        ByteBuffer bb = getWriteBuffer();
        bb.put((byte) data);
        releaseWriteBuffer(1);
        return this;
    }

    public IOBuffer append(ByteBuffer cs) throws IOException {
        return append(cs.array(), cs.position() + cs.arrayOffset(),
                cs.remaining());
    }

    /**
     *  Append a buffer. The buffer will not be modified.
     */
    public IOBuffer append(BBucket cs) throws IOException {
        append(cs.array(), cs.position(), cs.remaining());
        return this;
    }

    /**
     *  Append a buffer. The buffer will not be modified.
     */
    public IOBuffer append(BBucket cs, int len) throws IOException {
        append(cs.array(), cs.position(), len);
        return this;
    }

    public IOBuffer append(IOBuffer cs) throws IOException {
        for (int i = 0; i < cs.getBufferCount(); i++) {
            BBucket o = cs.peekBucket(i);
            append(o);
        }

        return this;
    }

    public IOBuffer append(IOBuffer cs, int len) throws IOException {
        for (int i = 0; i < cs.getBufferCount(); i++) {
            BBucket o = cs.peekBucket(i);
            append(o);
        }

        return this;
    }

    public IOBuffer append(CharSequence cs) throws IOException {
        byte[] data = cs.toString().getBytes();
        append(data, 0, data.length);
        return this;
    }

    public IOBuffer append(char c) throws IOException {
        ByteBuffer bb = getWriteBuffer();
        bb.put((byte) c);
        releaseWriteBuffer(1);
        return this;
    }

    /**
     * All operations that iterate over buffers must be
     * sync
     * @return
     */
    public synchronized int available() {
        int a = 0;
        int cnt = buffers.size();
        for (int i = 0; i < cnt; i++) {
            a += buffers.get(i).remaining();
        }
        return a;
    }

    public String toString() {
        return "IOB:{c:" + getBufferCount() +
          ", b:" + available() +
          (isAppendClosed() ? ", C}" : " }");
    }

    public BBucket popLen(int lenToConsume) {
        BBucket o = peekFirst(); // skip empty
        if (o == null) {
            return null;
        }
        BBucket sb = BBuffer.wrapper(o.array(),
                o.position(), lenToConsume);
        o.position(o.position() + lenToConsume);
        return sb;
    }

    public BBucket popFirst() {
        BBucket o = peekFirst(); // skip empty
        if (o == null) {
            return null;
        }
        if (o == appendable) {
            synchronized (buffers) {
                    // TODO: concurrency ???
                    BBucket sb =
                        BBuffer.wrapper(appendable.array(),
                                appendable.position(),
                                appendable.limit() - appendable.position());
                    appendable.position(appendable.limit());
                    return sb;
            }
        } else {
            buffers.removeFirst();
        }
        return o;
    }


    public ByteBuffer getWriteBuffer() throws IOException {
        synchronized (buffers) {
            if (closeQueued) {
                throw new IOException("Closed");
            }
            BBucket last = (buffers.size() == 0) ?
                    null : buffers.getLast();
            if (last == null || last != appendable ||
                    last.array().length - last.limit() < 16) {
                last = BBuffer.allocate(ALLOC_SIZE);
            }
            appending = true;
            appendable = (BBuffer) last;

            if (writeBuffer == null || writeBuffer.array() != appendable.array()) {
                writeBuffer = ByteBuffer.wrap(appendable.array());
            }
            writeBuffer.position(appendable.limit());
            writeBuffer.limit(appendable.array().length);
            return writeBuffer;
        }
    }

    public void releaseWriteBuffer(int read) throws IOException {
        synchronized (buffers) {
            if (!appending) {
                throw new IOException("Not appending");
            }
            if (writeBuffer != null) {
                if (appendable.limit() != writeBuffer.position()) {
                    appendable.limit(writeBuffer.position());
                    // We have some more data.
                    if (buffers.size() == 0 ||
                            buffers.getLast() != appendable) {
                        buffers.add(appendable);
                    }
                    notifyDataAvailable(appendable);
                }
            }
            appending = false;
        }
    }


    // ------ More utilities - for parsing request ( later )-------
//  public final int skipBlank(ByteBuffer bb, int start) {
//  // Skipping blank lines
//  byte chr = 0;
//  do {
//    if (!bb.hasRemaining()) {
//      return -1;
//    }
//    chr = bb.get();
//  } while ((chr == HttpParser.CR) || (chr == HttpParser.LF));
//  return bb.position();
//}

//public final int readToDelimAndLowerCase(ByteBuffer bb,
//                                         byte delim,
//                                         boolean lower) {
//  boolean space = false;
//  byte chr = 0;
//  while (!space) {
//    if (!bb.hasRemaining()) {
//      return -1;
//    }
//    chr = bb.get();
//    if (chr == delim) {
//      space = true;
//    }
//    if (lower && (chr >= HttpParser.A) && (chr <= HttpParser.Z)) {
//      bb.put(bb.position() - 1,
//          (byte) (chr - HttpParser.LC_OFFSET));
//    }
//  }
//  return bb.position();
//}

//public boolean skipSpace(ByteBuffer bb) {
//  boolean space = true;
//  while (space) {
//    if (!bb.hasRemaining()) {
//      return false;
//    }
//    byte chr = bb.get();
//    if ((chr == HttpParser.SP) || (chr == HttpParser.HT)) {
//      //
//    } else {
//      space = false;
//      bb.position(bb.position() -1); // move back
//    }
//  }
//  return true;
//}
}
