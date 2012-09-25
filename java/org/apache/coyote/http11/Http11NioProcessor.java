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
package org.apache.coyote.http11;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLEngine;
import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.WriteListener;

import org.apache.coyote.ActionCode;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.KeyAttachment;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SecureNioChannel;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Processes HTTP requests.
 *
 * @author Remy Maucherat
 * @author Filip Hanik
 */
public class Http11NioProcessor extends AbstractHttp11Processor<NioChannel> {

    private static final Log log = LogFactory.getLog(Http11NioProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }


    /**
     * SSL information.
     */
    protected SSLSupport sslSupport;

    // ----------------------------------------------------------- Constructors


    public Http11NioProcessor(int maxHttpHeaderSize, NioEndpoint endpoint,
            int maxTrailerSize) {

        super(endpoint);

        inputBuffer = new InternalNioInputBuffer(request, maxHttpHeaderSize);
        request.setInputBuffer(inputBuffer);

        outputBuffer = new InternalNioOutputBuffer(response, maxHttpHeaderSize);
        response.setOutputBuffer(outputBuffer);

        initializeFilters(maxTrailerSize);
    }


    // ----------------------------------------------------- Instance Variables
    /**
     * Input.
     */
    protected final InternalNioInputBuffer inputBuffer;


    /**
     * Output.
     */
    protected final InternalNioOutputBuffer outputBuffer;


    /**
     * Sendfile data.
     */
    protected NioEndpoint.SendfileData sendfileData = null;


    /**
     * Socket associated with the current connection.
     */
    protected SocketWrapper<NioChannel> socket = null;


    // --------------------------------------------------------- Public Methods

    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @throws IOException error during an I/O operation
     */
    @Override
    public SocketState event(SocketStatus status)
        throws IOException {

        long soTimeout = endpoint.getSoTimeout();

        RequestInfo rp = request.getRequestProcessor();
        final NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            error = !getAdapter().event(request, response, status);
            if ( !error ) {
                if (attach != null) {
                    attach.setComet(comet);
                    if (comet) {
                        Integer comettimeout = (Integer) request.getAttribute(
                                org.apache.coyote.Constants.COMET_TIMEOUT_ATTR);
                        if (comettimeout != null) {
                            attach.setTimeout(comettimeout.longValue());
                        }
                    } else {
                        //reset the timeout
                        if (keepAlive) {
                            attach.setTimeout(keepAliveTimeout);
                        } else {
                            attach.setTimeout(soTimeout);
                        }
                    }

                }
            }
        } catch (InterruptedIOException e) {
            error = true;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("http11processor.request.process"), t);
            // 500 - Internal Server Error
            response.setStatus(500);
            getAdapter().log(request, response, 0);
            error = true;
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (error || status==SocketStatus.STOP) {
            return SocketState.CLOSED;
        } else if (!comet) {
            if (keepAlive) {
                inputBuffer.nextRequest();
                outputBuffer.nextRequest();
                return SocketState.OPEN;
            } else {
                return SocketState.CLOSED;
            }
        } else {
            return SocketState.LONG;
        }
    }




    @Override
    public SocketState asyncDispatch(SocketStatus status) {
        final NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);


        if (status == SocketStatus.OPEN_WRITE) {
            try {
                asyncStateMachine.asyncOperation();
                try {
                    if (outputBuffer.hasDataToWrite()) {
                        //System.out.println("Attempting data flush!!");
                        outputBuffer.flushBuffer(false);
                    }
                }catch (IOException x) {
                    if (log.isDebugEnabled()) log.debug("Unable to write async data.",x);
                    status = SocketStatus.ASYNC_WRITE_ERROR;
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, x);
                }
                //return if we have more data to write
                if (status == SocketStatus.OPEN_WRITE && isRegisteredForWrite(attach)) {
                    return SocketState.LONG;
                }
            }catch (IllegalStateException x) {
                attach.interestOps(attach.interestOps() | SelectionKey.OP_WRITE);
            }
        } else if (status == SocketStatus.OPEN_READ) {
            try {
                try {
                    if (inputBuffer.nbRead()>0) {
                        asyncStateMachine.asyncOperation();
                    }
                }catch (IOException x) {
                    if (log.isDebugEnabled()) log.debug("Unable to read async data.",x);
                    status = SocketStatus.ASYNC_READ_ERROR;
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, x);
                }
                //return if we have more data to write
            }catch (IllegalStateException x) {
                attach.interestOps(attach.interestOps() | SelectionKey.OP_READ);
            }
        }

        SocketState state = super.asyncDispatch(status);
        //return if we have more data to write
        if (state == SocketState.LONG && isRegisteredForWrite(attach)) {
            return SocketState.LONG;
        } else {
            return state;
        }
    }



    @Override
    public SocketState process(SocketWrapper<NioChannel> socketWrapper) throws IOException {
        SocketState state = super.process(socketWrapper);
        final NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
        //return if we have more data to write
        if (isRegisteredForWrite(attach)) {
            return SocketState.LONG;
        } else {
            return state;
        }
    }




    protected boolean isRegisteredForWrite(KeyAttachment attach) {
        //return if we have more data to write
        if (outputBuffer.hasDataToWrite()) {
            attach.interestOps(attach.interestOps() | SelectionKey.OP_WRITE);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void resetTimeouts() {
        final NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
        if (!error && attach != null &&
                asyncStateMachine.isAsyncDispatching()) {
            long soTimeout = endpoint.getSoTimeout();

            //reset the timeout
            if (keepAlive) {
                attach.setTimeout(keepAliveTimeout);
            } else {
                attach.setTimeout(soTimeout);
            }
        }
    }


    @Override
    protected boolean disableKeepAlive() {
        return false;
    }


    @Override
    protected void setRequestLineReadTimeout() throws IOException {
        // socket.setTimeout()
        //     - timeout used by poller
        // socket.getSocket().getIOChannel().socket().setSoTimeout()
        //     - timeout used for blocking reads

        // When entering the processing loop there will always be data to read
        // so no point changing timeouts at this point

        // For the second and subsequent executions of the processing loop, a
        // non-blocking read is used so again no need to set the timeouts

        // Because NIO supports non-blocking reading of the request line and
        // headers the timeouts need to be set when returning the socket to
        // the poller rather than here.

        // NO-OP
    }


    @Override
    protected boolean handleIncompleteRequestLineRead() {
        // Haven't finished reading the request so keep the socket
        // open
        openSocket = true;
        // Check to see if we have read any of the request line yet
        if (inputBuffer.getParsingRequestLinePhase() < 2) {
            if (socket.getLastAccess() > -1 || keptAlive) {
                // Haven't read the request line and have previously processed a
                // request. Must be keep-alive. Make sure poller uses keepAlive.
                socket.setTimeout(endpoint.getKeepAliveTimeout());
            }
        } else {
            // Started to read request line. Need to keep processor
            // associated with socket
            readComplete = false;
            // Make sure poller uses soTimeout from here onwards
            socket.setTimeout(endpoint.getSoTimeout());
        }
        if (endpoint.isPaused()) {
            // 503 - Service unavailable
            response.setStatus(503);
            getAdapter().log(request, response, 0);
            error = true;
        } else {
            return true;
        }
        return false;
    }


    @Override
    protected void setSocketTimeout(int timeout) throws IOException {
        socket.getSocket().getIOChannel().socket().setSoTimeout(timeout);
    }


    @Override
    protected void setCometTimeouts(SocketWrapper<NioChannel> socketWrapper) {
        // Comet support
        SelectionKey key = socketWrapper.getSocket().getIOChannel().keyFor(
                socketWrapper.getSocket().getPoller().getSelector());
        if (key != null) {
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment) key.attachment();
            if (attach != null)  {
                attach.setComet(comet);
                if (comet) {
                    Integer comettimeout = (Integer) request.getAttribute(
                            org.apache.coyote.Constants.COMET_TIMEOUT_ATTR);
                    if (comettimeout != null) {
                        attach.setTimeout(comettimeout.longValue());
                    }
                }
            }
        }
    }


    @Override
    protected boolean breakKeepAliveLoop(
            SocketWrapper<NioChannel> socketWrapper) {
        // Do sendfile as needed: add socket to sendfile and end
        if (sendfileData != null && !error) {
            ((KeyAttachment) socketWrapper).setSendfileData(sendfileData);
            sendfileData.keepAlive = keepAlive;
            SelectionKey key = socketWrapper.getSocket().getIOChannel().keyFor(
                    socketWrapper.getSocket().getPoller().getSelector());
            //do the first write on this thread, might as well
            openSocket = socketWrapper.getSocket().getPoller().processSendfile(key,
                    (KeyAttachment) socketWrapper, true);
            return true;
        }
        return false;
    }




    @Override
    public void recycleInternal() {
        socket = null;
        sendfileData = null;
    }


    // ----------------------------------------------------- ActionHook Methods


    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @Override
    public void actionInternal(ActionCode actionCode, Object param) {

        if (actionCode == ActionCode.REQ_HOST_ADDR_ATTRIBUTE) {

            if (socket == null) {
                request.remoteAddr().recycle();
            } else {
                if (socket.getRemoteAddr() == null) {
                    InetAddress inetAddr = socket.getSocket().getIOChannel().socket().getInetAddress();
                    if (inetAddr != null) {
                        socket.setRemoteAddr(inetAddr.getHostAddress());
                    }
                }
                request.remoteAddr().setString(socket.getRemoteAddr());
            }

        } else if (actionCode == ActionCode.REQ_LOCAL_NAME_ATTRIBUTE) {

            if (socket == null) {
                request.localName().recycle();
            } else {
                if (socket.getLocalName() == null) {
                    InetAddress inetAddr = socket.getSocket().getIOChannel().socket().getLocalAddress();
                    if (inetAddr != null) {
                        socket.setLocalName(inetAddr.getHostName());
                    }
                }
                request.localName().setString(socket.getLocalName());
            }

        } else if (actionCode == ActionCode.REQ_HOST_ATTRIBUTE) {

            if (socket == null) {
                request.remoteHost().recycle();
            } else {
                if (socket.getRemoteHost() == null) {
                    InetAddress inetAddr = socket.getSocket().getIOChannel().socket().getInetAddress();
                    if (inetAddr != null) {
                        socket.setRemoteHost(inetAddr.getHostName());
                    }
                    if (socket.getRemoteHost() == null) {
                        if (socket.getRemoteAddr() == null &&
                                inetAddr != null) {
                            socket.setRemoteAddr(inetAddr.getHostAddress());
                        }
                        if (socket.getRemoteAddr() != null) {
                            socket.setRemoteHost(socket.getRemoteAddr());
                        }
                    }
                }
                request.remoteHost().setString(socket.getRemoteHost());
            }

        } else if (actionCode == ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE) {

            if (socket == null) {
                request.localAddr().recycle();
            } else {
                if (socket.getLocalAddr() == null) {
                    socket.setLocalAddr(
                            socket.getSocket().getIOChannel().socket().getLocalAddress().getHostAddress());
                }
                request.localAddr().setString(socket.getLocalAddr());
            }

        } else if (actionCode == ActionCode.REQ_REMOTEPORT_ATTRIBUTE) {

            if (socket == null) {
                request.setRemotePort(0);
            } else {
                if (socket.getRemotePort() == -1) {
                    socket.setRemotePort(socket.getSocket().getIOChannel().socket().getPort());
                }
                request.setRemotePort(socket.getRemotePort());
            }

        } else if (actionCode == ActionCode.REQ_LOCALPORT_ATTRIBUTE) {

            if (socket == null) {
                request.setLocalPort(0);
            } else {
                if (socket.getLocalPort() == -1) {
                    socket.setLocalPort(socket.getSocket().getIOChannel().socket().getLocalPort());
                }
                request.setLocalPort(socket.getLocalPort());
            }

        } else if (actionCode == ActionCode.REQ_SSL_ATTRIBUTE ) {

            try {
                if (sslSupport != null) {
                    Object sslO = sslSupport.getCipherSuite();
                    if (sslO != null) {
                        request.setAttribute
                            (SSLSupport.CIPHER_SUITE_KEY, sslO);
                    }
                    sslO = sslSupport.getPeerCertificateChain(false);
                    if (sslO != null) {
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                    sslO = sslSupport.getKeySize();
                    if (sslO != null) {
                        request.setAttribute
                            (SSLSupport.KEY_SIZE_KEY, sslO);
                    }
                    sslO = sslSupport.getSessionId();
                    if (sslO != null) {
                        request.setAttribute
                            (SSLSupport.SESSION_ID_KEY, sslO);
                    }
                    request.setAttribute(SSLSupport.SESSION_MGR, sslSupport);
                }
            } catch (Exception e) {
                log.warn(sm.getString("http11processor.socket.ssl"), e);
            }

        } else if (actionCode == ActionCode.REQ_SSL_CERTIFICATE) {

            if( sslSupport != null) {
                /*
                 * Consume and buffer the request body, so that it does not
                 * interfere with the client's handshake messages
                 */
                InputFilter[] inputFilters = inputBuffer.getFilters();
                ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER])
                    .setLimit(maxSavePostSize);
                inputBuffer.addActiveFilter
                    (inputFilters[Constants.BUFFERED_FILTER]);
                SecureNioChannel sslChannel = (SecureNioChannel) socket.getSocket();
                SSLEngine engine = sslChannel.getSslEngine();
                if (!engine.getNeedClientAuth()) {
                    // Need to re-negotiate SSL connection
                    engine.setNeedClientAuth(true);
                    try {
                        sslChannel.rehandshake(endpoint.getSoTimeout());
                        sslSupport = ((NioEndpoint)endpoint).getHandler()
                                .getSslImplementation().getSSLSupport(
                                        engine.getSession());
                    } catch (IOException ioe) {
                        log.warn(sm.getString("http11processor.socket.sslreneg",ioe));
                    }
                }

                try {
                    // use force=false since re-negotiation is handled above
                    // (and it is a NO-OP for NIO anyway)
                    Object sslO = sslSupport.getPeerCertificateChain(false);
                    if( sslO != null) {
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.ssl"), e);
                }
            }

        } else if (actionCode == ActionCode.AVAILABLE) {
            request.setAvailable(inputBuffer.available());
        } else if (actionCode == ActionCode.COMET_BEGIN) {
            comet = true;
        } else if (actionCode == ActionCode.COMET_END) {
            comet = false;
        }  else if (actionCode == ActionCode.COMET_CLOSE) {
            if (socket==null || socket.getSocket().getAttachment(false)==null) {
                return;
            }
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
            attach.setCometOps(NioEndpoint.OP_CALLBACK);
            RequestInfo rp = request.getRequestProcessor();
            if (rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE) {
                // Close event for this processor triggered by request
                // processing in another processor, a non-Tomcat thread (i.e.
                // an application controlled thread) or similar.
                socket.getSocket().getPoller().add(socket.getSocket());
            }
        } else if (actionCode == ActionCode.COMET_SETTIMEOUT) {
            if (param==null) {
                return;
            }
            if (socket==null || socket.getSocket().getAttachment(false)==null) {
                return;
            }
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
            long timeout = ((Long)param).longValue();
            //if we are not piggy backing on a worker thread, set the timeout
            RequestInfo rp = request.getRequestProcessor();
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) {
                attach.setTimeout(timeout);
            }
        } else if (actionCode == ActionCode.ASYNC_COMPLETE) {
            if (asyncStateMachine.asyncComplete()) {
                ((NioEndpoint)endpoint).dispatchForEvent(this.socket.getSocket(),SocketStatus.OPEN_READ, true);
            }
        } else if (actionCode == ActionCode.ASYNC_SETTIMEOUT) {
            if (param==null) {
                return;
            }
            if (socket==null || socket.getSocket().getAttachment(false)==null) {
                return;
            }
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
            long timeout = ((Long)param).longValue();
            //if we are not piggy backing on a worker thread, set the timeout
            attach.setTimeout(timeout);
        } else if (actionCode == ActionCode.ASYNC_DISPATCH) {
            if (asyncStateMachine.asyncDispatch()) {
                ((NioEndpoint)endpoint).dispatchForEvent(this.socket.getSocket(),SocketStatus.OPEN_READ, true);
            }
        } else if (actionCode == ActionCode.SET_READ_LISTENER) {
            ReadListener listener = (ReadListener)param;
            request.setReadListener(listener);
        } else if (actionCode == ActionCode.SET_WRITE_LISTENER) {
            WriteListener listener = (WriteListener)param;
            response.setWriteListener(listener);
            outputBuffer.setBlocking(listener==null);
        } else if (actionCode == ActionCode.NB_READ_INTEREST) {
            if (socket==null || socket.getSocket().getAttachment(false)==null) {
                return;
            }
            RequestInfo rp = request.getRequestProcessor();
            if (rp.getStage() == org.apache.coyote.Constants.STAGE_SERVICE) {
                NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
                attach.interestOps(attach.interestOps() | SelectionKey.OP_READ);
            } else {
                throw new IllegalStateException("Calling isReady asynchronously is illegal.");
            }
        } else if (actionCode == ActionCode.NB_WRITE_INTEREST) {
            if (socket==null || socket.getSocket().getAttachment(false)==null) {
                return;
        }
            AtomicBoolean canWrite = (AtomicBoolean)param;
            RequestInfo rp = request.getRequestProcessor();
            if (rp.getStage() == org.apache.coyote.Constants.STAGE_SERVICE) {
                if (outputBuffer.isWritable()) {
                    canWrite.set(true);
                } else {
                    canWrite.set(false);
    }
            } else {
                throw new IllegalStateException("Calling canWrite asynchronously is illegal.");
            }
        } else if (actionCode == ActionCode.ASYNC_DISPATCH_FOR_OPERATION) {
            asyncStateMachine.asyncOperation();
        }
    }


    // ------------------------------------------------------ Protected Methods


    @Override
    protected void prepareRequestInternal() {
        sendfileData = null;
    }

    @Override
    protected boolean prepareSendfile(OutputFilter[] outputFilters) {
        String fileName = (String) request.getAttribute(
                org.apache.coyote.Constants.SENDFILE_FILENAME_ATTR);
        if (fileName != null) {
            // No entity body sent here
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
            sendfileData = new NioEndpoint.SendfileData();
            sendfileData.fileName = fileName;
            sendfileData.pos = ((Long) request.getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_START_ATTR)).longValue();
            sendfileData.length = ((Long) request.getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_END_ATTR)).longValue() - sendfileData.pos;
            return true;
        }
        return false;
    }

    @Override
    protected void setSocketWrapper(SocketWrapper<NioChannel> socketWrapper) {
        this.socket = socketWrapper;
    }

    @Override
    protected AbstractInputBuffer<NioChannel> getInputBuffer() {
        return inputBuffer;
    }

    @Override
    protected AbstractOutputBuffer<NioChannel> getOutputBuffer() {
        return outputBuffer;
    }

    /**
     * Set the SSL information for this HTTP connection.
     */
    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }
}
