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

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.apache.tomcat.util.res.StringManager;

/**
 * Coyote implementation of the servlet output stream.
 *
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public class CoyoteOutputStream extends ServletOutputStream {

    protected static final StringManager sm =
            StringManager.getManager(Constants.Package);


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
    protected Object clone()
        throws CloneNotSupportedException {
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
        ob.writeByte(i);
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
        ob.write(b, off, len);
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
        ob.flush();
        if (nonBlocking) {
            checkRegisterForWrite();
        }
    }


    /**
     * Checks for concurrent writes which are not permitted. This object has no
     * state information so the call chain is
     * CoyoyeOutputStream->OutputBuffer->CoyoteResponse.
     *
     * @return <code>true</code> if this OutputStream is currently in
     *         non-blocking mode.
     */
    private boolean checkNonBlockingWrite() {
        boolean nonBlocking = !ob.isBlocking();
        if (nonBlocking && !ob.isReady()) {
            throw new IllegalStateException(
                    sm.getString("coyoteOutputStream.nbNotready"));
        }
        return nonBlocking;
    }


    /**
     * Checks to see if there is data left in the Coyote output buffers (NOT the
     * servlet output buffer) and if so registers the associated socket for
     * write so the buffers will be emptied. The container will take care of
     * this. As far as the app is concerned, there is a non-blocking write in
     * progress. It doesn't have visibility of whether the data is buffered in
     * the socket buffer or the Coyote buffers.
     */
    private void checkRegisterForWrite() {
        ob.checkRegisterForWrite();
    }


    @Override
    public void close()
        throws IOException {
        ob.close();
    }

    @Override
    public boolean isReady() {
        return ob.isReady();
    }


    @Override
    public void setWriteListener(WriteListener listener) {
        ob.setWriteListener(listener);
    }
}

