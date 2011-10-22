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

package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * Extension of {@link GZIPOutputStream} to workaround for a couple of long
 * standing JDK bugs
 * (<a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4255743">Bug
 * 4255743</a> and
 * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4813885">Bug
 * 4813885</a>) so the GZIP'd output can be flushed.
 */
public class FlushableGZIPOutputStream extends GZIPOutputStream {
    public FlushableGZIPOutputStream(OutputStream os) throws IOException {
        super(os);
    }

    private static final byte[] EMPTYBYTEARRAY = new byte[0];
    private boolean hasData = false;

    /**
     * Here we make sure we have received data, so that the header has been for
     * sure written to the output stream already.
     */
    @Override
    public synchronized void write(byte[] bytes, int i, int i1)
            throws IOException {
        super.write(bytes, i, i1);
        hasData = true;
    }

    @Override
    public synchronized void write(int i) throws IOException {
        super.write(i);
        hasData = true;
    }

    @Override
    public synchronized void write(byte[] bytes) throws IOException {
        super.write(bytes);
        hasData = true;
    }

    @Override
    public synchronized void flush() throws IOException {
        if (!hasData) {
            return; // do not allow the gzip header to be flushed on its own
        }

        // trick the deflater to flush
        /**
         * Now this is tricky: We force the Deflater to flush its data by
         * switching compression level. As yet, a perplexingly simple workaround
         * for
         * http://developer.java.sun.com/developer/bugParade/bugs/4255743.html
         */
        if (!def.finished()) {
            def.setInput(EMPTYBYTEARRAY, 0, 0);

            def.setLevel(Deflater.NO_COMPRESSION);
            deflate();

            def.setLevel(Deflater.DEFAULT_COMPRESSION);
            deflate();

            out.flush();
        }

        hasData = false; // no more data to flush
    }

    /*
     * Keep on calling deflate until it runs dry. The default implementation
     * only does it once and can therefore hold onto data when they need to be
     * flushed out.
     */
    @Override
    protected void deflate() throws IOException {
        int len;
        do {
            len = def.deflate(buf, 0, buf.length);
            if (len > 0) {
                out.write(buf, 0, len);
            }
        } while (len != 0);
    }

}
