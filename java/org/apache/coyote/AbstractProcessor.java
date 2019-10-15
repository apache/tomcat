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
package org.apache.coyote;

import java.io.IOException;
import java.util.concurrent.Executor;

import javax.servlet.RequestDispatcher;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.parser.Host;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provides functionality and attributes common to all supported protocols
 * (currently HTTP and AJP).
 */
public abstract class AbstractProcessor<S> implements ActionHook, Processor<S> {

    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    // Used to avoid useless B2C conversion on the host name.
    protected char[] hostNameC = new char[0];

    protected Adapter adapter;
    protected AsyncStateMachine<S> asyncStateMachine;
    protected AbstractEndpoint<S> endpoint;
    protected Request request;
    protected Response response;
    protected SocketWrapper<S> socketWrapper = null;
    private int maxCookieCount = 200;


    /**
     * Error state for the request/response currently being processed.
     */
    private ErrorState errorState = ErrorState.NONE;

    protected final UserDataHelper userDataHelper;

    /**
     * Intended for use by the Upgrade sub-classes that have no need to
     * initialise the request, response, etc.
     */
    protected AbstractProcessor() {
        // NOOP
        userDataHelper = null;
    }

    public AbstractProcessor(AbstractEndpoint<S> endpoint) {
        this.endpoint = endpoint;
        asyncStateMachine = new AsyncStateMachine<S>(this);
        request = new Request();
        response = new Response();
        response.setHook(this);
        request.setResponse(response);
        userDataHelper = new UserDataHelper(getLog());
    }


    /**
     * Update the current error state to the new error state if the new error
     * state is more severe than the current error state.
     * @param errorState The error status details
     * @param t The error which occurred
     */
    protected void setErrorState(ErrorState errorState, Throwable t) {
        // Use the return value to avoid processing more than one async error
        // in a single async cycle.
        boolean setError = response.setError();
        boolean blockIo = this.errorState.isIoAllowed() && !errorState.isIoAllowed();
        this.errorState = this.errorState.getMostSevere(errorState);
        // Don't change the status code for IOException since that is almost
        // certainly a client disconnect in which case it is preferable to keep
        // the original status code http://markmail.org/message/4cxpwmxhtgnrwh7n
        if (response.getStatus() < 400 && !(t instanceof IOException)) {
            response.setStatus(500);
        }
        if (t != null) {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
        }
        if (blockIo && isAsync() && setError) {
            if (asyncStateMachine.asyncError()) {
                getEndpoint().processSocketAsync(socketWrapper, SocketStatus.ERROR);
            }
        }
    }


    protected void resetErrorState() {
        errorState = ErrorState.NONE;
    }


    protected ErrorState getErrorState() {
        return errorState;
    }

    /**
     * @return The endpoint receiving connections that are handled by this
     *         processor.
     */
    protected AbstractEndpoint<S> getEndpoint() {
        return endpoint;
    }


    /**
     * The request associated with this processor.
     */
    @Override
    public Request getRequest() {
        return request;
    }


    /**
     * Set the associated adapter.
     *
     * @param adapter the new adapter
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }


    /**
     * Get the associated adapter.
     *
     * @return the associated adapter
     */
    public Adapter getAdapter() {
        return adapter;
    }


    /**
     * Set the socket wrapper being used.
     * @param socketWrapper The socket wrapper
     */
    protected final void setSocketWrapper(SocketWrapper<S> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    /**
     * @return the socket wrapper being used.
     */
    protected final SocketWrapper<S> getSocketWrapper() {
        return socketWrapper;
    }


    /**
     * @return the Executor used by the underlying endpoint.
     */
    @Override
    public Executor getExecutor() {
        return endpoint.getExecutor();
    }


    @Override
    public boolean isAsync() {
        return (asyncStateMachine != null && asyncStateMachine.isAsync());
    }


    @Override
    public SocketState asyncPostProcess() {
        return asyncStateMachine.asyncPostProcess();
    }


    @Override
    public void errorDispatch() {
        getAdapter().errorDispatch(request, response);
    }

    @Override
    public abstract boolean isComet();

    protected void parseHost(MessageBytes valueMB) {
        if (valueMB == null || valueMB.isNull()) {
            populateHost();
            populatePort();
            return;
        } else if (valueMB.getLength() == 0) {
            // Empty Host header so set sever name to empty string
            request.serverName().setString("");
            populatePort();
            return;
        }

        ByteChunk valueBC = valueMB.getByteChunk();
        byte[] valueB = valueBC.getBytes();
        int valueL = valueBC.getLength();
        int valueS = valueBC.getStart();
        if (hostNameC.length < valueL) {
            hostNameC = new char[valueL];
        }

        try {
            // Validates the host name
            int colonPos = Host.parse(valueMB);

            // Extract the port information first, if any
            if (colonPos != -1) {
                int port = 0;
                for (int i = colonPos + 1; i < valueL; i++) {
                    char c = (char) valueB[i + valueS];
                    if (c < '0' || c > '9') {
                        response.setStatus(400);
                        setErrorState(ErrorState.CLOSE_CLEAN, null);
                        return;
                    }
                    port = port * 10 + c - '0';
                }
                request.setServerPort(port);

                // Only need to copy the host name up to the :
                valueL = colonPos;
            }

            // Extract the host name
            for (int i = 0; i < valueL; i++) {
                hostNameC[i] = (char) valueB[i + valueS];
            }
            request.serverName().setChars(hostNameC, 0, valueL);

        } catch (IllegalArgumentException e) {
            // IllegalArgumentException indicates that the host name is invalid
            UserDataHelper.Mode logMode = userDataHelper.getNextMode();
            if (logMode != null) {
                String message = sm.getString("abstractProcessor.hostInvalid", valueMB.toString());
                switch (logMode) {
                    case INFO_THEN_DEBUG:
                        message += sm.getString("abstractProcessor.fallToDebug");
                        //$FALL-THROUGH$
                    case INFO:
                        getLog().info(message, e);
                        break;
                    case DEBUG:
                        getLog().debug(message, e);
                }
            }

            response.setStatus(400);
            setErrorState(ErrorState.CLOSE_CLEAN, e);
        }
    }


    /**
     * Called when a host header is not present in the request (e.g. HTTP/1.0).
     * It populates the server name with appropriate information. The source is
     * expected to vary by protocol.
     * <p>
     * The default implementation is a NO-OP.
     */
    protected void populateHost() {
        // NO-OP
    }


    /**
     * Called when a host header is not present or is empty in the request (e.g.
     * HTTP/1.0). It populates the server port with appropriate information. The
     * source is expected to vary by protocol.
     * <p>
     * The default implementation is a NO-OP.
     */
    protected void populatePort() {
        // NO-OP
    }


    @Override
    public abstract boolean isUpgrade();

    /**
     * Process HTTP requests. All requests are treated as HTTP requests to start
     * with although they may change type during processing.
     */
    @Override
    public abstract SocketState process(SocketWrapper<S> socket) throws IOException;

    /**
     * Process in-progress Comet requests. These will start as HTTP requests.
     */
    @Override
    public abstract SocketState event(SocketStatus status) throws IOException;

    /**
     * Process in-progress Servlet 3.0 Async requests. These will start as HTTP
     * requests.
     */
    @Override
    public abstract SocketState asyncDispatch(SocketStatus status);

    /**
     * Processes data received on a connection that has been through an HTTP
     * upgrade.
     */
    @Override
    public abstract SocketState upgradeDispatch() throws IOException;


    public int getMaxCookieCount() {
        return maxCookieCount;
    }


    public void setMaxCookieCount(int maxCookieCount) {
        this.maxCookieCount = maxCookieCount;
    }


     /**
     * @deprecated  Will be removed in Tomcat 8.0.x.
     */
    @Deprecated
    @Override
    public abstract org.apache.coyote.http11.upgrade.UpgradeInbound getUpgradeInbound();

    protected abstract Log getLog();


    @Override
    public final AsyncStateMachine<S> getAsyncStateMachine() {
        return asyncStateMachine;
    }
}
