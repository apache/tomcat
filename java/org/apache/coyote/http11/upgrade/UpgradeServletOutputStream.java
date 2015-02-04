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

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class UpgradeServletOutputStream extends ServletOutputStream {

    protected static final StringManager sm =
            StringManager.getManager(UpgradeServletOutputStream.class);

    protected final SocketWrapperBase<?> socketWrapper;

    // Used to ensure that isReady() and onWritePossible() have a consistent
    // view of buffer and registered.
    private final Object registeredLock = new Object();

    // Used to ensure that only one thread writes to the socket at a time and
    // that buffer is consistently updated with any unwritten data after the
    // write. Note it is not necessary to hold this lock when checking if buffer
    // contains data but, depending on how the result is used, some form of
    // synchronization may be required (see fireListenerLock for an example).
    private final Object writeLock = new Object();

    private volatile boolean flushing = false;

    private volatile boolean closeRequired = false;

    // Start in blocking-mode
    private volatile WriteListener listener = null;

    // Guarded by registeredLock
    private volatile boolean registered = false;

    // Use to track if a dispatch needs to be arranged to trigger the first call
    // to onWritePossible. If the socket gets registered for write while this is
    // set then this will be ignored.
    private volatile boolean writeDispatchRequired = false;

    private volatile ClassLoader applicationLoader = null;


    public UpgradeServletOutputStream(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    @Override
    public final boolean isReady() {
        if (listener == null) {
            throw new IllegalStateException(
                    sm.getString("upgrade.sos.canWrite.is"));
        }

        // Make sure isReady() and onWritePossible() have a consistent view of
        // fireListener when determining if the listener should fire
        synchronized (registeredLock) {
            if (flushing) {
                // Since flushing is true the socket must already be registered
                // for write and multiple registrations will cause problems.
                registered = true;
                return false;
            } else if (registered){
                // The socket is already registered for write and multiple
                // registrations will cause problems.
                return false;
            } else {
                boolean result = socketWrapper.isReadyForWrite();
                registered = !result;
                return result;
            }
        }
    }


    @Override
    public final void setWriteListener(WriteListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException(
                    sm.getString("upgrade.sos.writeListener.null"));
        }
        if (this.listener != null) {
            throw new IllegalArgumentException(
                    sm.getString("upgrade.sos.writeListener.set"));
        }
        // Container is responsible for first call to onWritePossible() but only
        // need to do this if setting the listener for the first time.
        writeDispatchRequired = true;

        this.listener = listener;
        this.applicationLoader = Thread.currentThread().getContextClassLoader();
    }


    protected final boolean isCloseRequired() {
        return closeRequired;
    }


    @Override
    public void write(int b) throws IOException {
        synchronized (writeLock) {
            preWriteChecks();
            writeInternal(new byte[] { (byte) b }, 0, 1);
        }
    }


    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        synchronized (writeLock) {
            preWriteChecks();
            writeInternal(b, off, len);
        }
    }


    @Override
    public void flush() throws IOException {
        flushInternal(listener == null, true);
    }


    private void flushInternal(boolean block, boolean updateFlushing) throws IOException {
        try {
            synchronized (writeLock) {
                if (updateFlushing) {
                    flushing = socketWrapper.flush(block);
                    if (flushing) {
                        socketWrapper.registerWriteInterest();
                    }
                } else {
                    socketWrapper.flush(block);
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            onError(t);
            if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException(t);
            }
        }
    }

    @Override
    public void close() throws IOException {
        closeRequired = true;
        socketWrapper.close();
    }


    private void preWriteChecks() {
        if (listener != null && !socketWrapper.canWrite()) {
            throw new IllegalStateException(sm.getString("upgrade.sis.write.ise"));
        }
    }


    /**
     * Must hold writeLock to call this method.
     */
    private void writeInternal(byte[] b, int off, int len) throws IOException {
        if (listener == null) {
            // Simple case - blocking IO
            socketWrapper.write(true, b, off, len);
        } else {
            socketWrapper.write(false, b, off, len);
        }
    }


    protected final void onWritePossible() throws IOException {
        if (flushing) {
            flushInternal(false, true);
            if (flushing) {
                return;
            }
        } else {
            // This may fill the write buffer in which case the
            // isReadyForWrite() call below will re-register the socket for
            // write
            flushInternal(false, false);
        }

        // Make sure isReady() and onWritePossible() have a consistent view
        // of buffer and fireListener when determining if the listener
        // should fire
        boolean fire = false;
        synchronized (registeredLock) {
            if (socketWrapper.isReadyForWrite()) {
                registered = false;
                fire = true;
            } else {
                registered = true;
            }
        }

        if (fire) {
            Thread thread = Thread.currentThread();
            ClassLoader originalClassLoader = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(applicationLoader);
                listener.onWritePossible();
            } finally {
                thread.setContextClassLoader(originalClassLoader);
            }
        }
    }


    protected final void onError(Throwable t) {
        if (listener == null) {
            return;
        }
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(applicationLoader);
            listener.onError(t);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }


    void checkWriteDispatch() {
        synchronized (registeredLock) {
            if (writeDispatchRequired) {
                writeDispatchRequired = false;
                if (!registered) {
                    socketWrapper.addDispatch(DispatchType.NON_BLOCKING_WRITE);
                }
            }
        }
    }
}
