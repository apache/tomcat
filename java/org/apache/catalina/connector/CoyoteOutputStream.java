/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.connector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

import org.apache.tomcat.util.res.StringManager;

/**
 * Coyote implementation of the servlet output stream.
 *
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public class CoyoteOutputStream extends ServletOutputStream {

    protected static final StringManager sm = StringManager.getManager(CoyoteOutputStream.class);


    // ----------------------------------------------------- Instance Variables

    protected OutputBuffer ob;


    // ----------------------------------------------------------- Constructors


    protected CoyoteOutputStream(OutputBuffer ob) {
        this.ob = ob;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Prevent cloning the facade.
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }


    // -------------------------------------------------------- Package Methods


    /**
     * Clear facade.
     */
    void clear() {
        ob = null;
    }


    // --------------------------------------------------- OutputStream Methods


    @Override
    public void write(int i) throws IOException {
        boolean nonBlocking = checkNonBlockingWrite();
        try {
            ob.writeByte(i);
        } catch (IOException ioe) {
            handleIOException(ioe);
        }
        if (nonBlocking) {
            checkRegisterForWrite();
        }
    }


    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }


    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        boolean nonBlocking = checkNonBlockingWrite();
        try {
            ob.write(b, off, len);
        } catch (IOException ioe) {
            handleIOException(ioe);
        }
        if (nonBlocking) {
            checkRegisterForWrite();
        }
    }


    @Override
    public void write(ByteBuffer from) throws IOException {
        Objects.requireNonNull(from);
        boolean nonBlocking = checkNonBlockingWrite();
        if (from.remaining() == 0) {
            return;
        }
        try {
            ob.write(from);
        } catch (IOException ioe) {
            handleIOException(ioe);
        }
        if (nonBlocking) {
            checkRegisterForWrite();
        }
    }


    /**
     * Will send the buffer to the client.
     */
    @Override
    public void flush() throws IOException {
        boolean nonBlocking = checkNonBlockingWrite();
        try {
            ob.flush();
        } catch (IOException ioe) {
            handleIOException(ioe);
        }
        if (nonBlocking) {
            checkRegisterForWrite();
        }
    }


    /**
     * Checks for concurrent writes which are not permitted. This object has no state information so the call chain is
     * CoyoteOutputStream->OutputBuffer->CoyoteResponse.
     *
     * @return <code>true</code> if this OutputStream is currently in non-blocking mode.
     */
    private boolean checkNonBlockingWrite() {
        boolean nonBlocking = !ob.isBlocking();
        if (nonBlocking && !ob.isReady()) {
            throw new IllegalStateException(sm.getString("coyoteOutputStream.nbNotready"));
        }
        return nonBlocking;
    }


    /**
     * Checks to see if there is data left in the Coyote output buffers (NOT the servlet output buffer) and if so
     * registers the associated socket for write so the buffers will be emptied. The container will take care of this.
     * As far as the app is concerned, there is a non-blocking write in progress. It doesn't have visibility of whether
     * the data is buffered in the socket buffer or the Coyote buffers.
     */
    private void checkRegisterForWrite() {
        ob.checkRegisterForWrite();
    }


    @Override
    public void close() throws IOException {
        try {
            ob.close();
        } catch (IOException ioe) {
            handleIOException(ioe);
        }
    }

    @Override
    public boolean isReady() {
        if (ob == null) {
            throw new IllegalStateException(sm.getString("coyoteOutputStream.null"));
        }
        return ob.isReady();
    }


    @Override
    public void setWriteListener(WriteListener listener) {
        ob.setWriteListener(listener);
    }


    private void handleIOException(IOException ioe) throws IOException {
        try {
            ob.setErrorException(ioe);
        } catch (NullPointerException npe) {
            /*
             * Ignore.
             *
             * An IOException on a non-container thread during asynchronous Servlet processing will trigger a dispatch
             * to a container thread that will complete the asynchronous processing and recycle the request, response
             * and associated objects including the OutputBuffer. Depending on timing it is possible that the
             * OutputBuffer will have been cleared by the time the call above is made - resulting in an NPE.
             *
             * If the OutputBuffer is null then there is no need to call setErrorException(). Catching and ignoring the
             * NPE is (for now at least) a simpler solution than adding locking to OutputBuffer to ensure it is non-null
             * and remains non-null while setErrorException() is called.
             *
             * The longer term solution is likely a refactoring and clean-up of error handling for asynchronous requests
             * but that is potentially a significant piece of work.
             */
        }
        throw ioe;
    }
}

