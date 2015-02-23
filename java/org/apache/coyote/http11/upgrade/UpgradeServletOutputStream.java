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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class UpgradeServletOutputStream extends ServletOutputStream {

    private static final Log log = LogFactory.getLog(UpgradeServletOutputStream.class);
    private static final StringManager sm =
            StringManager.getManager(UpgradeServletOutputStream.class);

    private final SocketWrapperBase<?> socketWrapper;

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

    private volatile boolean closed = false;

    // Start in blocking-mode
    private volatile WriteListener listener = null;

    // Guarded by registeredLock
    private boolean registered = false;

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
        if (closed) {
            return false;
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
        if (closed) {
            throw new IllegalStateException(sm.getString("upgrade.sos.write.closed"));
        }
        // Container is responsible for first call to onWritePossible().
        synchronized (registeredLock) {
            registered = true;
            // Need to get writes onto a separate thread as long as WebSocket
            // uses Servlet non-blocking I/O as onOpen can throw an error which
            // closes the WebSocket connection. That uses a blocking write which
            // means onwWritePossible needs to be on a separate thread to clear
            // the latch used in the blocking write. If
            // socketWrapper.addDispatch() was used the current thread would
            // block on the write and would not process the dispatch until the
            // latch timed out triggering more errors.
            socketWrapper.registerWriteInterest();
        }

        this.listener = listener;
        this.applicationLoader = Thread.currentThread().getContextClassLoader();
    }


    final boolean isClosed() {
        return closed;
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
        preWriteChecks();
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
        if (closed) {
            return;
        }
        closed = true;
        socketWrapper.setWriteTimeout(socketWrapper.getConnectiontTimeout());
        flushInternal(true, false);
    }


    private void preWriteChecks() {
        if (listener != null && !socketWrapper.canWrite()) {
            throw new IllegalStateException(sm.getString("upgrade.sos.write.ise"));
        }
        if (closed) {
            throw new IllegalStateException(sm.getString("upgrade.sos.write.closed"));
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


    final void onWritePossible() {
        try {
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
        } catch (IOException ioe) {
            onError(ioe);
            return;
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
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                onError(t);
            } finally {
                thread.setContextClassLoader(originalClassLoader);
            }
        }
    }


    private final void onError(Throwable t) {
        if (listener == null) {
            return;
        }
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(applicationLoader);
            listener.onError(t);
        } catch (Throwable t2) {
            ExceptionUtils.handleThrowable(t2);
            log.warn(sm.getString("upgrade.sos.onErrorFail"), t2);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
        try {
            close();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgrade.sos.errorCloseFail"), ioe);
            }
        }
    }
}
