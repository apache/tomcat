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

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import org.apache.coyote.ContainerThreadMarker;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class UpgradeServletInputStream extends ServletInputStream {

    private static final Log log = LogFactory.getLog(UpgradeServletInputStream.class);
    private static final StringManager sm =
            StringManager.getManager(UpgradeServletInputStream.class);

    private final SocketWrapperBase<?> socketWrapper;

    private volatile boolean closed = false;
    private volatile boolean eof = false;
    // Start in blocking-mode
    private volatile Boolean ready = Boolean.TRUE;
    private volatile ReadListener listener = null;
    private volatile ClassLoader applicationLoader = null;


    public UpgradeServletInputStream(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    @Override
    public final boolean isFinished() {
        if (listener == null) {
            throw new IllegalStateException(
                    sm.getString("upgrade.sis.isFinished.ise"));
        }
        return eof;
    }


    @Override
    public final boolean isReady() {
        if (listener == null) {
            throw new IllegalStateException(
                    sm.getString("upgrade.sis.isReady.ise"));
        }

        if (eof || closed) {
            return false;
        }

        // If we already know the current state, return it.
        if (ready != null) {
            return ready.booleanValue();
        }

        try {
            ready = Boolean.valueOf(socketWrapper.isReadyForRead());
        } catch (IOException e) {
            onError(e);
        }
        return ready.booleanValue();
    }


    @Override
    public final void setReadListener(ReadListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException(
                    sm.getString("upgrade.sis.readListener.null"));
        }
        if (this.listener != null) {
            throw new IllegalArgumentException(
                    sm.getString("upgrade.sis.readListener.set"));
        }
        if (closed) {
            throw new IllegalStateException(sm.getString("upgrade.sis.read.closed"));
        }

        // Container is responsible for first call to onDataAvailable().
        if (ContainerThreadMarker.isContainerThread()) {
            socketWrapper.addDispatch(DispatchType.NON_BLOCKING_READ);
        } else {
            socketWrapper.registerReadInterest();
        }

        this.listener = listener;
        this.applicationLoader = Thread.currentThread().getContextClassLoader();
        // Switching to non-blocking. Don't know if data is available.
        ready = null;
    }


    @Override
    public final int read() throws IOException {
        preReadChecks();

        return readInternal();
    }


    @Override
    public final int readLine(byte[] b, int off, int len) throws IOException {
        preReadChecks();

        if (len <= 0) {
            return 0;
        }
        int count = 0, c;

        while ((c = readInternal()) != -1) {
            b[off++] = (byte) c;
            count++;
            if (c == '\n' || count == len) {
                break;
            }
        }
        return count > 0 ? count : -1;
    }


    @Override
    public final int read(byte[] b, int off, int len) throws IOException {
        preReadChecks();

        try {
            int result = socketWrapper.read(listener == null, b, off, len);
            if (result == -1) {
                eof = true;
            }
            return result;
        } catch (IOException ioe) {
            close();
            throw ioe;
        }
    }



    @Override
    public void close() throws IOException {
        eof = true;
        closed = true;
    }


    private void preReadChecks() {
        if (listener != null && (ready == null || !ready.booleanValue())) {
            throw new IllegalStateException(sm.getString("upgrade.sis.read.ise"));
        }
        if (closed) {
            throw new IllegalStateException(sm.getString("upgrade.sis.read.closed"));
        }
        // No longer know if data is available
        ready = null;
    }


    private int readInternal() throws IOException {
        // Single byte reads for non-blocking need special handling so all
        // single byte reads run through this method.
        byte[] b = new byte[1];
        int result;
        try {
            result = socketWrapper.read(listener == null, b, 0, 1);
        } catch (IOException ioe) {
            close();
            throw ioe;
        }
        if (result == 0) {
            return -1;
        } else if (result == -1) {
            eof = true;
            return -1;
        } else {
            return b[0] & 0xFF;
        }
    }


    final void onDataAvailable() {
        if (listener == null) {
            return;
        }
        ready = Boolean.TRUE;
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(applicationLoader);
            if (!eof) {
                listener.onDataAvailable();
            }
            if (eof) {
                listener.onAllDataRead();
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            onError(t);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
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
            log.warn(sm.getString("upgrade.sis.onErrorFail"), t2);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
        try {
            close();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgrade.sis.errorCloseFail"), ioe);
            }
        }
        ready = Boolean.FALSE;
    }


    final boolean isClosed() {
        return closed;
    }
}
