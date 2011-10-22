/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.io.InputStream;


/**
 * Similar with ServletInputStream - adds readLine(byte[]..), using
 * a IOBuffer.
 *
 *
 *
 * @author Costin Manolache
 */
public class IOInputStream extends InputStream {

    IOBuffer bb;
    long timeout;

    public IOInputStream(IOChannel httpCh, long to) {
        bb = httpCh.getIn();
        this.timeout = to;
    }

    @Override
    public int read() throws IOException {
        // getReadableBucket/peekFirst returns a buffer with at least
        // 1 byte in it.
        if (bb.isClosedAndEmpty()) {
            return -1;
        }
        bb.waitData(timeout);
        if (bb.isClosedAndEmpty()) {
            return -1;
        }

        return bb.read();
    }

    public int read(byte[] buf, int off, int len) throws IOException {
        if (bb.isClosedAndEmpty()) {
            return -1;
        }
        bb.waitData(timeout);
        if (bb.isClosedAndEmpty()) {
            return -1;
        }
        return bb.read(buf, off, len);
    }

    /**
     *  Servlet-style read line: terminator is \n or \r\n, left in buffer.
     */
    public int readLine(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return 0;
        }
        int count = 0, c;

        while ((c = read()) != -1) {
            b[off++] = (byte)c;
            count++;
            if (c == '\n' || count == len) {
                break;
            }
        }
        return count > 0 ? count : -1;
    }
}
