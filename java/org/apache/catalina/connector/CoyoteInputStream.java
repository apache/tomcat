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

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import org.apache.tomcat.util.res.StringManager;

/**
 * This class handles reading bytes.
 *
 * @author Remy Maucherat
 */
public class CoyoteInputStream extends ServletInputStream {

    protected static final StringManager sm = StringManager.getManager(CoyoteInputStream.class);


    protected InputBuffer ib;


    protected CoyoteInputStream(InputBuffer ib) {
        this.ib = ib;
    }


    /**
     * Clear facade.
     */
    void clear() {
        ib = null;
    }


    /**
     * Prevent cloning the facade.
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }


    @Override
    public int read() throws IOException {
        checkNonBlockingRead();
        return ib.readByte();
    }

    @Override
    public int available() throws IOException {
        return ib.available();
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }


    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        checkNonBlockingRead();
        return ib.read(b, off, len);
    }


    @Override
    public int read(final ByteBuffer b) throws IOException {
        Objects.requireNonNull(b);
        checkNonBlockingRead();
        return ib.read(b);
    }


    /**
     * Close the stream Since we re-cycle, we can't allow the call to super.close() which would permanently disable us.
     */
    @Override
    public void close() throws IOException {
        ib.close();
    }

    @Override
    public boolean isFinished() {
        return ib.isFinished();
    }


    @Override
    public boolean isReady() {
        if (ib == null) {
            throw new IllegalStateException(sm.getString("coyoteInputStream.null"));
        }
        return ib.isReady();
    }


    @Override
    public void setReadListener(ReadListener listener) {
        ib.setReadListener(listener);
    }


    private void checkNonBlockingRead() {
        if (!ib.isBlocking() && !ib.isReady()) {
            throw new IllegalStateException(sm.getString("coyoteInputStream.nbNotready"));
        }
    }
}
