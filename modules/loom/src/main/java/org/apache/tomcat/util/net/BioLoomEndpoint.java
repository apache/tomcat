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
package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.net.ServerSocketFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

public class BioLoomEndpoint extends AbstractEndpoint<Socket, Socket> {

    private static final Log log = LogFactory.getLog(BioLoomEndpoint.class);
    //private static final Log logHandshake = LogFactory.getLog(JioLoomEndpoint.class.getName() + ".handshake");

    private volatile ServerSocket serverSocket = null;

    private SocketAddress previousAcceptedSocketRemoteAddress = null;
    private long previousAcceptedSocketNanoTime = 0;

    private Thread.Builder threadBuilder;


    @Override
    public void bind() throws Exception {
        initServerSocket();

        // Initialize SSL if needed
        initialiseSsl();
    }


    protected void initServerSocket() throws Exception {
        try {
            ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
            if (getAddress() == null) {
                serverSocket = serverSocketFactory.createServerSocket(getPort(), getAcceptCount());
            } else {
                serverSocket = serverSocketFactory.createServerSocket(getPort(), getAcceptCount(), getAddress());
            }
        } catch (BindException orig) {
            String msg;
            if (getAddress() == null) {
              msg = orig.getMessage() + " <null>:" + getPort();
            } else {
              msg = orig.getMessage() + " " + getAddress().toString() + ":" + getPort();
            }
            BindException be = new BindException(msg);
            be.initCause(orig);
            throw be;
        }
    }


    @Override
    public void startInternal() throws Exception {
        if (!running) {
            running = true;
            paused = false;

            threadBuilder = Thread.ofVirtual().name(getName() + "-", 0);

            initializeConnectionLatch();

            startAcceptorThread();
        }
    }


    @Override
    public void stopInternal() throws Exception {
        if (!paused) {
            pause();
        }
        if (running) {
            running = false;
            acceptor.stop(10);
        }
    }


    @Override
    public void unbind() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Destroy initiated for " +
                    new InetSocketAddress(getAddress(),getPortWithOffset()));
        }
        if (running) {
            stop();
        }
        try {
            doCloseServerSocket();
        } catch (IOException ioe) {
            getLog().warn(sm.getString("endpoint.serverSocket.closeFailed", getName()), ioe);
        }
        destroySsl();
        super.unbind();
        if (getHandler() != null ) {
            getHandler().recycle();
        }
        if (log.isDebugEnabled()) {
            log.debug("Destroy completed for " +
                    new InetSocketAddress(getAddress(), getPortWithOffset()));
        }
    }


    @Override
    protected void doCloseServerSocket() throws IOException {
        ServerSocket serverSocket = this.serverSocket;

        if (serverSocket != null) {
            serverSocket.close();
            serverSocket = null;
        }
    }


    @Override
    protected Socket serverSocketAccept() throws Exception {
        Socket result = serverSocket.accept();

        SocketAddress currentRemoteAddress = result.getRemoteSocketAddress();
        long currentNanoTime = System.nanoTime();
        if (currentRemoteAddress.equals(previousAcceptedSocketRemoteAddress) &&
                currentNanoTime - previousAcceptedSocketNanoTime < 1000) {
            throw new IOException(sm.getString("endpoint.err.duplicateAccept"));
        }
        previousAcceptedSocketRemoteAddress = currentRemoteAddress;
        previousAcceptedSocketNanoTime = currentNanoTime;

        return result;
    }


    @Override
    protected boolean setSocketOptions(Socket socket) {
        try {
            socketProperties.setProperties(socket);

        } catch (SocketException s) {
            // Error here is common if the client has reset the connection
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.err.unexpected"), s);
            }
            // Close the socket
            return false;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("endpoint.err.unexpected"), t);
            // Close the socket
            return false;
        }

        // Process the request from this socket
        try {
            BioLoomSocketWrapper wrapper = new BioLoomSocketWrapper(socket, this);

            connections.put(socket, wrapper);
            wrapper.setKeepAliveLeft(getMaxKeepAliveRequests());

            SocketProcessor socketProcessor = new SocketProcessor(wrapper);
            threadBuilder.start(socketProcessor);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }

    @Override
    protected void destroySocket(Socket socket) {
        countDownConnection();
        try {
            socket.close();
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.err.close"), ioe);
            }
        }
    }


    @Override
    protected InetSocketAddress getLocalAddress() throws IOException {

        ServerSocket serverSocket = this.serverSocket;

        if (serverSocket == null) {
            return null;
        }

        SocketAddress sa = serverSocket.getLocalSocketAddress();
        if (sa instanceof InetSocketAddress) {
            return (InetSocketAddress) sa;
        }

        return null;
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected SocketProcessorBase<Socket> createSocketProcessor(SocketWrapperBase<Socket> socketWrapper,
            SocketEvent event) {
        // This method should never be called for this Endpoint.
        // TODO i18n message
        throw new IllegalStateException();
    }


    @Override
    public boolean getUseSendfile() {
        // Disable sendfile
        return false;
    }


    public static class BioLoomSocketWrapper extends SocketWrapperBase<Socket> {

        public BioLoomSocketWrapper(Socket socket, AbstractEndpoint<Socket, ?> endpoint) {
            super(socket, endpoint);
            socketBufferHandler = new SocketBufferHandler(
                    endpoint.getSocketProperties().getAppReadBufSize(),
                    endpoint.getSocketProperties().getAppWriteBufSize(),
                    false);
        }

        @Override
        protected void populateRemoteHost() {
            // TODO Auto-generated method stub

        }

        @Override
        protected void populateRemoteAddr() {
            // TODO Auto-generated method stub

        }

        @Override
        protected void populateRemotePort() {
            // TODO Auto-generated method stub

        }

        @Override
        protected void populateLocalName() {
            // TODO Auto-generated method stub

        }

        @Override
        protected void populateLocalAddr() {
            // TODO Auto-generated method stub

        }

        @Override
        protected void populateLocalPort() {
            // TODO Auto-generated method stub

        }

        @Override
        public int read(boolean block, byte[] b, int off, int len) throws IOException {
            // TODO Auto-generated method stub
            return 0;
        }


        @Override
        public int read(boolean block, ByteBuffer to) throws IOException {
            if (to.hasArray()) {
                int read = getSocket().getInputStream().read(
                        to.array(), to.arrayOffset() + to.position(), to.remaining());
                if (read > 0) {
                    to.position(to.position() + read);
                }
                return read;
            } else {
                throw new IllegalStateException();
            }
        }


        @Override
        public boolean isReadyForRead() throws IOException {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void setAppReadBufHandler(ApplicationBufferHandler handler) {
            // TODO Auto-generated method stub

        }


        @Override
        protected void doClose() {
            if (log.isDebugEnabled()) {
                log.debug("Calling [" + getEndpoint() + "].closeSocket([" + this + "])");
            }
            try {
                getEndpoint().connections.remove(getSocket());
                getSocket().close();
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                if (log.isDebugEnabled()) {
                    // TODO i18n
                    log.error("Socket close fail", e);
                }
            } finally {
                socketBufferHandler = SocketBufferHandler.EMPTY;
                nonBlockingWriteBuffer.clear();
            }
        }


        @Override
        protected boolean flushNonBlocking() throws IOException {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        protected void doWrite(boolean block, ByteBuffer from) throws IOException {
            if (from.hasArray()) {
                getSocket().getOutputStream().write(from.array(), from.arrayOffset(), from.remaining());
                from.position(from.limit());
            } else {
                throw new IllegalStateException();
            }
        }

        @Override
        public void registerReadInterest() {
            // TODO Auto-generated method stub

        }

        @Override
        public void registerWriteInterest() {
            // TODO Auto-generated method stub

        }

        @Override
        public SendfileDataBase createSendfileData(String filename, long pos, long length) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public SendfileState processSendfile(SendfileDataBase sendfileData) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void doClientAuth(SSLSupport sslSupport) throws IOException {
            // TODO Auto-generated method stub

        }

        @Override
        public SSLSupport getSslSupport() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        protected <A> SocketWrapperBase<Socket>.OperationState<A> newOperationState(boolean read, ByteBuffer[] buffers,
                int offset, int length, BlockingMode block, long timeout, TimeUnit unit, A attachment,
                CompletionCheck check, CompletionHandler<Long, ? super A> handler, Semaphore semaphore,
                SocketWrapperBase<Socket>.VectoredIOCompletionHandler<A> completion) {
            // TODO Auto-generated method stub
            return null;
        }
    }


    protected class SocketProcessor implements Runnable {

        private final SocketWrapperBase<Socket> socketWrapper;

        public SocketProcessor(SocketWrapperBase<Socket> socketWrapper) {
            this.socketWrapper = socketWrapper;
        }

        @Override
        public void run() {
            try {
                if (isSSLEnabled()) {
                    // Need to do handshake
                    Socket s = socketWrapper.getSocket();

                    // TODO
                    socketWrapper.reset(s);
                    /*
                    try {
                    } catch (IOException ioe) {
                        if (logHandshake.isDebugEnabled()) {
                            logHandshake.debug(sm.getString("endpoint.err.handshake",
                                    socketWrapper.getRemoteAddr(), Integer.toString(socketWrapper.getRemotePort())), ioe);
                        }
                    }
                    */
                }

                getHandler().process(socketWrapper, SocketEvent.OPEN_READ);

            } catch (VirtualMachineError vme) {
                ExceptionUtils.handleThrowable(vme);
            } catch (Throwable t) {
                log.error(sm.getString("endpoint.processing.fail"), t);
            } finally {
                socketWrapper.close();
            }
        }
    }
}