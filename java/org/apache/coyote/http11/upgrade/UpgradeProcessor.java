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
import java.util.concurrent.Executor;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.ProtocolHandler;
import javax.servlet.http.WebConnection;

import org.apache.coyote.Processor;
import org.apache.coyote.Request;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.res.StringManager;

public abstract class UpgradeProcessor<S>
        implements Processor<S>, WebConnection {

    protected static final StringManager sm =
            StringManager.getManager(Constants.Package);

    private final ProtocolHandler httpUpgradeHandler;
    private final ServletInputStream upgradeServletInputStream;
    private final ServletOutputStream upgradeServletOutputStream;

    protected UpgradeProcessor (ProtocolHandler httpUpgradeHandler,
            UpgradeServletInputStream upgradeServletInputStream,
            UpgradeServletOutputStream upgradeServletOutputStream) {
        this.httpUpgradeHandler = httpUpgradeHandler;
        this.upgradeServletInputStream = upgradeServletInputStream;
        this.upgradeServletOutputStream = upgradeServletOutputStream;
        this.httpUpgradeHandler.init(this);
    }


    // --------------------------------------------------- WebConnection methods

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return upgradeServletInputStream;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return upgradeServletOutputStream;
    }


    // ------------------------------------------- Implemented Processor methods

    @Override
    public final boolean isUpgrade() {
        return true;
    }

    @Override
    public ProtocolHandler getHttpUpgradeHandler() {
        return httpUpgradeHandler;
    }

    @Override
    public final SocketState upgradeDispatch(SocketStatus status)
            throws IOException {

        // TODO Handle read/write ready for non-blocking IO
        return SocketState.UPGRADED;
    }

    @Override
    public final void recycle(boolean socketClosing) {
        // Currently a NO-OP as upgrade processors are not recycled.
    }


    // ---------------------------- Processor methods that are NO-OP for upgrade

    @Override
    public final Executor getExecutor() {
        return null;
    }

    @Override
    public final SocketState process(SocketWrapper<S> socketWrapper)
            throws IOException {
        return null;
    }

    @Override
    public final SocketState event(SocketStatus status) throws IOException {
        return null;
    }

    @Override
    public final SocketState asyncDispatch(SocketStatus status) {
        return null;
    }

    @Override
    public final SocketState asyncPostProcess() {
        return null;
    }

    @Override
    public final boolean isComet() {
        return false;
    }

    @Override
    public final boolean isAsync() {
        return false;
    }

    @Override
    public final Request getRequest() {
        return null;
    }

    @Override
    public final void setSslSupport(SSLSupport sslSupport) {
        // NOOP
    }


    // ----------------------------------------------------------- Inner classes

    protected abstract static class UpgradeServletInputStream extends
            ServletInputStream {

        private volatile ReadListener readListener = null;

        @Override
        public final boolean isFinished() {
            if (readListener == null) {
                throw new IllegalStateException(
                        sm.getString("upgrade.sis.isFinished.ise"));
            }

            // TODO Support non-blocking IO
            return false;
        }

        @Override
        public final boolean isReady() {
            if (readListener == null) {
                throw new IllegalStateException(
                        sm.getString("upgrade.sis.isReady.ise"));
            }

            // TODO Support non-blocking IO
            return false;
        }

        @Override
        public final void setReadListener(ReadListener listener) {
            if (listener == null) {
                throw new NullPointerException(
                        sm.getString("upgrade.sis.readListener.null"));
            }
            this.readListener = listener;
        }

        @Override
        public final int read() throws IOException {
            return doRead();
        }

        @Override
        public final int read(byte[] b, int off, int len) throws IOException {
            return doRead(b, off, len);
        }

        protected abstract int doRead() throws IOException;
        protected abstract int doRead(byte[] b, int off, int len)
                throws IOException;
    }

    protected abstract static class UpgradeServletOutputStream extends
            ServletOutputStream {

        private volatile WriteListener writeListener = null;

        @Override
        public boolean canWrite() {
            if (writeListener == null) {
                throw new IllegalStateException(
                        sm.getString("upgrade.sos.canWrite.ise"));
            }

            // TODO Support non-blocking IO
            return false;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            if (listener == null) {
                throw new NullPointerException(
                        sm.getString("upgrade.sos.writeListener.null"));
            }
            this.writeListener = listener;
        }

        @Override
        public void write(int b) throws IOException {
            doWrite(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            doWrite(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            doFlush();
        }

        protected abstract void doWrite(int b) throws IOException;
        protected abstract void doWrite(byte[] b, int off, int len)
                throws IOException;
        protected abstract void doFlush() throws IOException;
    }
}
