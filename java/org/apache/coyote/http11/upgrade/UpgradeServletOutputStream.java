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
package org.apache.coyote.http11.upgrade;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

public abstract class UpgradeServletOutputStream extends ServletOutputStream {

    // Start in blocking-mode
    private volatile Boolean writeable = null;
    private volatile WriteListener listener = null;
    private byte[] buffer;

    @Override
    public boolean canWrite() {
     // If we already know the current state, return it.
        if (writeable != null) {
            return writeable.booleanValue();
        }

        try {
            writeable = Boolean.valueOf(doCanWrite());
        } catch (IOException e) {
            listener.onError(e);
        }
        return writeable.booleanValue();
    }

    @Override
    public void setWriteListener(WriteListener listener) {
        if (listener == null) {
            // TODO i18n
            throw new IllegalArgumentException();
        }
        this.listener = listener;
        // Switching to non-blocking. Don't know if data is available.
        writeable = null;
    }

    @Override
    public void write(int b) throws IOException {
        preWriteChecks();

        writeInternal(new byte[] { (byte) b }, 0, 1);
    }


    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        preWriteChecks();

        writeInternal(b, off, len);
    }


    private void preWriteChecks() {
        if (writeable == null || !writeable.booleanValue()) {
            // TODO i18n
            throw new IllegalStateException();
        }
        // No longer know if data is available
        writeable = null;
    }


    private void writeInternal(byte[] b, int off, int len) throws IOException {
        if (listener == null) {
            // Simple case - blocking IO
            doWrite(true, b, off, len);
        } else {
            // Non-blocking IO
            int written = doWrite(false, b, off, len);
            if (written < len) {
                // TODO: - Reuse the buffer
                //       - Only reallocate if it gets too big (>8k?)
                buffer = new byte[len - written];
                System.arraycopy(b, off + written, buffer, 0, len - written);
            } else {
                buffer = null;
            }
        }
    }


    protected void onWritePossible() {
        try {
            writeInternal(buffer, 0, buffer.length);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        if (buffer == null) {
            writeable = Boolean.TRUE;
            listener.onWritePossible();
        }
    }


    protected abstract int doWrite(boolean block, byte[] b, int off, int len)
            throws IOException;

    protected abstract boolean doCanWrite() throws IOException;
}
