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
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.SessionTrackingMode;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Authenticator;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.AuthenticatorBase;
import org.apache.catalina.core.AsyncContextImpl;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.SessionConfig;
import org.apache.catalina.util.URLEncoder;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.ServerCookies;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.res.StringManager;


/**
 * Implementation of a request processor which delegates the processing to a
 * Coyote processor.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class CoyoteAdapter implements Adapter {

    private static final Log log = LogFactory.getLog(CoyoteAdapter.class);

    // -------------------------------------------------------------- Constants

    private static final String POWERED_BY = "Servlet/3.1 JSP/2.3 " +
            "(" + ServerInfo.getServerInfo() + " Java/" +
            System.getProperty("java.vm.vendor") + "/" +
            System.getProperty("java.runtime.version") + ")";

    private static final EnumSet<SessionTrackingMode> SSL_ONLY =
        EnumSet.of(SessionTrackingMode.SSL);

    public static final int ADAPTER_NOTES = 1;


    protected static final boolean ALLOW_BACKSLASH =
        Boolean.valueOf(System.getProperty("org.apache.catalina.connector.CoyoteAdapter.ALLOW_BACKSLASH", "false")).booleanValue();


    private static final ThreadLocal<String> THREAD_NAME =
            new ThreadLocal<String>() {

                @Override
                protected String initialValue() {
                    return Thread.currentThread().getName();
                }

    };

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new CoyoteProcessor associated with the specified connector.
     *
     * @param connector CoyoteConnector that owns this processor
     */
    public CoyoteAdapter(Connector connector) {

        super();
        this.connector = connector;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The CoyoteConnector with which this processor is associated.
     */
    private final Connector connector;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(CoyoteAdapter.class);


    // -------------------------------------------------------- Adapter Methods

    @Override
    public boolean asyncDispatch(org.apache.coyote.Request req,
            org.apache.coyote.Response res, SocketStatus status) throws Exception {
        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request == null) {
            throw new IllegalStateException(
                    "Dispatch may only happen on an existing request.");
        }
        boolean success = true;
        AsyncContextImpl asyncConImpl = (AsyncContextImpl)request.getAsyncContext();
        req.getRequestProcessor().setWorkerThreadName(Thread.currentThread().getName());
        try {
            if (!request.isAsync()) {
                // Error or timeout - need to tell listeners the request is over
                // Have to test this first since state may change while in this
                // method and this is only required if entering this method in
                // this state
                Context ctxt = request.getMappingData().context;
                if (ctxt != null) {
                    ctxt.fireRequestDestroyEvent(request);
                }
                // Lift any suspension (e.g. if sendError() was used by an async
                // request) to allow the response to be written to the client
                response.setSuspended(false);
            }

            if (status==SocketStatus.TIMEOUT) {
                if (!asyncConImpl.timeout()) {
                    asyncConImpl.setErrorState(null, false);
                }
            } else if (status==SocketStatus.ASYNC_READ_ERROR) {
                // A async read error is an IO error which means the socket
                // needs to be closed so set success to false to trigger a
                // close
                success = false;
                Throwable t = (Throwable)req.getAttribute(
                        RequestDispatcher.ERROR_EXCEPTION);
                req.getAttributes().remove(RequestDispatcher.ERROR_EXCEPTION);
                ReadListener readListener = req.getReadListener();
                if (readListener != null) {
                    ClassLoader oldCL = null;
                    try {
                        oldCL = request.getContext().bind(false, null);
                        readListener.onError(t);
                    } finally {
                        request.getContext().unbind(false, oldCL);
                    }
                }
                if (t != null) {
                    asyncConImpl.setErrorState(t, true);
                }
            } else if (status==SocketStatus.ASYNC_WRITE_ERROR) {
                // A async write error is an IO error which means the socket
                // needs to be closed so set success to false to trigger a
                // close
                success = false;
                Throwable t = (Throwable)req.getAttribute(
                        RequestDispatcher.ERROR_EXCEPTION);
                req.getAttributes().remove(RequestDispatcher.ERROR_EXCEPTION);
                if (res.getWriteListener() != null) {
                    ClassLoader oldCL = null;
                    try {
                        oldCL = request.getContext().bind(false, null);
                        res.getWriteListener().onError(t);
                    } finally {
                        request.getContext().unbind(false, oldCL);
                    }
                }
                if (t != null) {
                    asyncConImpl.setErrorState(t, true);
                }
            }

            // Check to see if non-blocking writes or reads are being used
            if (!request.isAsyncDispatching() && request.isAsync()) {
                WriteListener writeListener = res.getWriteListener();
                ReadListener readListener = req.getReadListener();
                if (writeListener != null && status == SocketStatus.OPEN_WRITE) {
                    ClassLoader oldCL = null;
                    try {
                        oldCL = request.getContext().bind(false, null);
                        res.onWritePossible();
                        if (request.isFinished() && req.sendAllDataReadEvent() &&
                                readListener != null) {
                            readListener.onAllDataRead();
                        }
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        writeListener.onError(t);
                        throw t;
                    } finally {
                        request.getContext().unbind(false, oldCL);
                    }
                } else if (readListener != null && status == SocketStatus.OPEN_READ) {
                    ClassLoader oldCL = null;
                    try {
                        oldCL = request.getContext().bind(false, null);
                        // If data is being read on a non-container thread a
                        // dispatch with status OPEN_READ will be used to get
                        // execution back on a container thread for the
                        // onAllDataRead() event. Therefore, make sure
                        // onDataAvailable() is not called in this case.
                        if (!request.isFinished()) {
                            readListener.onDataAvailable();
                        }
                        if (request.isFinished() && req.sendAllDataReadEvent()) {
                            readListener.onAllDataRead();
                        }
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        readListener.onError(t);
                        throw t;
                    } finally {
                        request.getContext().unbind(false, oldCL);
                    }
                }
            }

            // Has an error occurred during async processing that needs to be
            // processed by the application's error page mechanism (or Tomcat's
            // if the application doesn't define one)?
            if (!request.isAsyncDispatching() && request.isAsync() &&
                    response.isErrorReportRequired()) {
                connector.getService().getContainer().getPipeline().getFirst().invoke(request, response);
            }

            if (request.isAsyncDispatching()) {
                connector.getService().getContainer().getPipeline().getFirst().invoke(request, response);
                Throwable t = (Throwable) request.getAttribute(
                        RequestDispatcher.ERROR_EXCEPTION);
                if (t != null) {
                    asyncConImpl.setErrorState(t, true);
                }
            }

            if (!request.isAsync()) {
                request.finishRequest();
                response.finishResponse();
                if (request.getMappingData().context != null) {
                    request.getMappingData().context.logAccess(
                            request, response,
                            System.currentTimeMillis() - req.getStartTime(),
                            false);
                } else {
                    // Should normally not happen
                    log(req, res, System.currentTimeMillis() - req.getStartTime());
                }
            }

            // Check to see if the processor is in an error state. If it is,
            // bail out now.
            AtomicBoolean error = new AtomicBoolean(false);
            res.action(ActionCode.IS_ERROR, error);
            if (error.get()) {
                success = false;
            }
        } catch (IOException e) {
            success = false;
            // Ignore
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            success = false;
            log.error(sm.getString("coyoteAdapter.asyncDispatch"), t);
        } finally {
            if (!success) {
                res.setStatus(500);
                long time = 0;
                if (req.getStartTime() != -1) {
                    time = System.currentTimeMillis() - req.getStartTime();
                }
                log(req, res, time);
            }
            req.getRequestProcessor().setWorkerThreadName(null);
            // Recycle the wrapper request and response
            if (!success || !request.isAsync()) {
                request.recycle();
                response.recycle();
            } else {
                // Clear converters so that the minimum amount of memory
                // is used by this processor
                request.clearEncoders();
                response.clearEncoders();
            }
        }
        return success;
    }

    /**
     * Service method.
     */
    @Override
    public void service(org.apache.coyote.Request req,
                        org.apache.coyote.Response res)
        throws Exception {

        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request == null) {

            // Create objects
            request = connector.createRequest();
            request.setCoyoteRequest(req);
            response = connector.createResponse();
            response.setCoyoteResponse(res);

            // Link objects
            request.setResponse(response);
            response.setRequest(request);

            // Set as notes
            req.setNote(ADAPTER_NOTES, request);
            res.setNote(ADAPTER_NOTES, response);

            // Set query string encoding
            req.getParameters().setQueryStringEncoding
                (connector.getURIEncoding());

        }

        if (connector.getXpoweredBy()) {
            response.addHeader("X-Powered-By", POWERED_BY);
        }

        boolean async = false;

        try {

            // Parse and set Catalina and configuration specific
            // request parameters
            req.getRequestProcessor().setWorkerThreadName(THREAD_NAME.get());
            boolean postParseSuccess = postParseRequest(req, request, res, response);
            if (postParseSuccess) {
                //check valves if we support async
                request.setAsyncSupported(connector.getService().getContainer().getPipeline().isAsyncSupported());
                // Calling the container
                connector.getService().getContainer().getPipeline().getFirst().invoke(request, response);
            }
            AsyncContextImpl asyncConImpl = (AsyncContextImpl)request.getAsyncContext();
            if (asyncConImpl != null) {
                async = true;
                ReadListener readListener = req.getReadListener();
                if (readListener != null && request.isFinished()) {
                    // Possible the all data may have been read during service()
                    // method so this needs to be checked here
                    ClassLoader oldCL = null;
                    try {
                        oldCL = request.getContext().bind(false, null);
                        if (req.sendAllDataReadEvent()) {
                            req.getReadListener().onAllDataRead();
                        }
                    } finally {
                        request.getContext().unbind(false, oldCL);
                    }
                }
            } else {
                request.finishRequest();
                response.finishResponse();
                if (postParseSuccess) {
                    // Log only if processing was invoked.
                    // If postParseRequest() failed, it has already logged it.
                    request.getMappingData().context.logAccess(
                            request, response,
                            System.currentTimeMillis() - req.getStartTime(),
                            false);
                }
            }

        } catch (IOException e) {
            // Ignore
        } finally {
            req.getRequestProcessor().setWorkerThreadName(null);
            AtomicBoolean error = new AtomicBoolean(false);
            res.action(ActionCode.IS_ERROR, error);
            // Recycle the wrapper request and response
            if (!async || error.get()) {
                request.recycle();
                response.recycle();
            } else {
                // Clear converters so that the minimum amount of memory
                // is used by this processor
                request.clearEncoders();
                response.clearEncoders();
            }
        }

    }


    @Override
    public boolean prepare(org.apache.coyote.Request req, org.apache.coyote.Response res)
            throws IOException, ServletException {
        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        return postParseRequest(req, request, res, response);
    }


    @Override
    public void errorDispatch(org.apache.coyote.Request req,
            org.apache.coyote.Response res) {
        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request != null && request.getMappingData().context != null) {
            request.getMappingData().context.logAccess(
                    request, response,
                    System.currentTimeMillis() - req.getStartTime(),
                    false);
        } else {
            log(req, res, System.currentTimeMillis() - req.getStartTime());
        }

        if (request != null) {
            request.recycle();
        }

        if (response != null) {
            response.recycle();
        }

        req.recycle();
        res.recycle();
    }


    @Override
    public void log(org.apache.coyote.Request req,
            org.apache.coyote.Response res, long time) {

        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request == null) {
            // Create objects
            request = connector.createRequest();
            request.setCoyoteRequest(req);
            response = connector.createResponse();
            response.setCoyoteResponse(res);

            // Link objects
            request.setResponse(response);
            response.setRequest(request);

            // Set as notes
            req.setNote(ADAPTER_NOTES, request);
            res.setNote(ADAPTER_NOTES, response);

            // Set query string encoding
            req.getParameters().setQueryStringEncoding
                (connector.getURIEncoding());
        }

        try {
            // Log at the lowest level available. logAccess() will be
            // automatically called on parent containers.
            boolean logged = false;
            if (request.mappingData != null) {
                if (request.mappingData.context != null) {
                    logged = true;
                    request.mappingData.context.logAccess(
                            request, response, time, true);
                } else if (request.mappingData.host != null) {
                    logged = true;
                    request.mappingData.host.logAccess(
                            request, response, time, true);
                }
            }
            if (!logged) {
                connector.getService().getContainer().logAccess(
                        request, response, time, true);
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString("coyoteAdapter.accesslogFail"), t);
        } finally {
            request.recycle();
            response.recycle();
        }
    }


    private static class RecycleRequiredException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Override
    public void checkRecycled(org.apache.coyote.Request req,
            org.apache.coyote.Response res) {
        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);
        String messageKey = null;
        if (request != null && request.getHost() != null) {
            messageKey = "coyoteAdapter.checkRecycled.request";
        } else if (response != null && response.getContentWritten() != 0) {
            messageKey = "coyoteAdapter.checkRecycled.response";
        }
        if (messageKey != null) {
            // Log this request, as it has probably skipped the access log.
            // The log() method will take care of recycling.
            log(req, res, 0L);

            if (connector.getState().isAvailable()) {
                if (log.isInfoEnabled()) {
                    log.info(sm.getString(messageKey),
                            new RecycleRequiredException());
                }
            } else {
                // There may be some aborted requests.
                // When connector shuts down, the request and response will not
                // be reused, so there is no issue to warn about here.
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(messageKey),
                            new RecycleRequiredException());
                }
            }
        }
    }


    @Override
    public String getDomain() {
        return connector.getDomain();
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Perform the necessary processing after the HTTP headers have been parsed
     * to enable the request/response pair to be passed to the start of the
     * container pipeline for processing.
     *
     * @param req      The coyote request object
     * @param request  The catalina request object
     * @param res      The coyote response object
     * @param response The catalina response object
     *
     * @return <code>true</code> if the request should be passed on to the start
     *         of the container pipeline, otherwise <code>false</code>
     *
     * @throws IOException If there is insufficient space in a buffer while
     *                     processing headers
     * @throws ServletException If the supported methods of the target servlet
     *                          cannot be determined
     */
    protected boolean postParseRequest(org.apache.coyote.Request req, Request request,
            org.apache.coyote.Response res, Response response) throws IOException, ServletException {

        // If the processor has set the scheme (AJP will do this) use this to
        // set the secure flag as well. If the processor hasn't set it, use the
        // settings from the connector
        if (! req.scheme().isNull()) {
            // use processor specified scheme to determine secure state
            request.setSecure(req.scheme().equals("https"));
        } else {
            // use connector scheme and secure configuration, (defaults to
            // "http" and false respectively)
            req.scheme().setString(connector.getScheme());
            request.setSecure(connector.getSecure());
        }

        // At this point the Host header has been processed.
        // Override if the proxyPort/proxyHost are set
        String proxyName = connector.getProxyName();
        int proxyPort = connector.getProxyPort();
        if (proxyPort != 0) {
            req.setServerPort(proxyPort);
        }
        if (proxyName != null) {
            req.serverName().setString(proxyName);
        }

        MessageBytes undecodedURI = req.requestURI();

        // Check for ping OPTIONS * request
        if (undecodedURI.equals("*")) {
            if (req.method().equalsIgnoreCase("OPTIONS")) {
                StringBuilder allow = new StringBuilder();
                allow.append("GET, HEAD, POST, PUT, DELETE");
                // Trace if allowed
                if (connector.getAllowTrace()) {
                    allow.append(", TRACE");
                }
                // Always allow options
                allow.append(", OPTIONS");
                res.setHeader("Allow", allow.toString());
            } else {
                res.setStatus(404);
                res.setMessage("Not found");
            }
            connector.getService().getContainer().logAccess(
                    request, response, 0, true);
            return false;
        }

        MessageBytes decodedURI = req.decodedURI();

        if (undecodedURI.getType() == MessageBytes.T_BYTES) {
            // Copy the raw URI to the decodedURI
            decodedURI.duplicate(undecodedURI);

            // Parse the path parameters. This will:
            //   - strip out the path parameters
            //   - convert the decodedURI to bytes
            parsePathParameters(req, request);

            // URI decoding
            // %xx decoding of the URL
            try {
                req.getURLDecoder().convert(decodedURI, false);
            } catch (IOException ioe) {
                res.setStatus(400);
                res.setMessage("Invalid URI: " + ioe.getMessage());
                connector.getService().getContainer().logAccess(
                        request, response, 0, true);
                return false;
            }
            // Normalization
            if (!normalize(req.decodedURI())) {
                res.setStatus(400);
                res.setMessage("Invalid URI");
                connector.getService().getContainer().logAccess(
                        request, response, 0, true);
                return false;
            }
            // Character decoding
            convertURI(decodedURI, request);
            // Check that the URI is still normalized
            if (!checkNormalize(req.decodedURI())) {
                res.setStatus(400);
                res.setMessage("Invalid URI character encoding");
                connector.getService().getContainer().logAccess(
                        request, response, 0, true);
                return false;
            }
        } else {
            /* The URI is chars or String, and has been sent using an in-memory
             * protocol handler. The following assumptions are made:
             * - req.requestURI() has been set to the 'original' non-decoded,
             *   non-normalized URI
             * - req.decodedURI() has been set to the decoded, normalized form
             *   of req.requestURI()
             */
            decodedURI.toChars();
            // Remove all path parameters; any needed path parameter should be set
            // using the request object rather than passing it in the URL
            CharChunk uriCC = decodedURI.getCharChunk();
            int semicolon = uriCC.indexOf(';');
            if (semicolon > 0) {
                decodedURI.setChars
                    (uriCC.getBuffer(), uriCC.getStart(), semicolon);
            }
        }

        // Request mapping.
        MessageBytes serverName;
        if (connector.getUseIPVHosts()) {
            serverName = req.localName();
            if (serverName.isNull()) {
                // well, they did ask for it
                res.action(ActionCode.REQ_LOCAL_NAME_ATTRIBUTE, null);
            }
        } else {
            serverName = req.serverName();
        }

        // Version for the second mapping loop and
        // Context that we expect to get for that version
        String version = null;
        Context versionContext = null;
        boolean mapRequired = true;

        while (mapRequired) {
            // This will map the the latest version by default
            connector.getService().getMapper().map(serverName, decodedURI,
                    version, request.getMappingData());

            // If there is no context at this point, it is likely no ROOT context
            // has been deployed
            if (request.getContext() == null) {
                res.setStatus(404);
                res.setMessage("Not found");
                // No context, so use host
                Host host = request.getHost();
                // Make sure there is a host (might not be during shutdown)
                if (host != null) {
                    host.logAccess(request, response, 0, true);
                }
                return false;
            }

            // Now we have the context, we can parse the session ID from the URL
            // (if any). Need to do this before we redirect in case we need to
            // include the session id in the redirect
            String sessionID;
            if (request.getServletContext().getEffectiveSessionTrackingModes()
                    .contains(SessionTrackingMode.URL)) {

                // Get the session ID if there was one
                sessionID = request.getPathParameter(
                        SessionConfig.getSessionUriParamName(
                                request.getContext()));
                if (sessionID != null) {
                    request.setRequestedSessionId(sessionID);
                    request.setRequestedSessionURL(true);
                }
            }

            // Look for session ID in cookies and SSL session
            parseSessionCookiesId(request);
            parseSessionSslId(request);

            sessionID = request.getRequestedSessionId();

            mapRequired = false;
            if (version != null && request.getContext() == versionContext) {
                // We got the version that we asked for. That is it.
            } else {
                version = null;
                versionContext = null;

                Context[] contexts = request.getMappingData().contexts;
                // Single contextVersion means no need to remap
                // No session ID means no possibility of remap
                if (contexts != null && sessionID != null) {
                    // Find the context associated with the session
                    for (int i = (contexts.length); i > 0; i--) {
                        Context ctxt = contexts[i - 1];
                        if (ctxt.getManager().findSession(sessionID) != null) {
                            // We found a context. Is it the one that has
                            // already been mapped?
                            if (!ctxt.equals(request.getMappingData().context)) {
                                // Set version so second time through mapping
                                // the correct context is found
                                version = ctxt.getWebappVersion();
                                versionContext = ctxt;
                                // Reset mapping
                                request.getMappingData().recycle();
                                mapRequired = true;
                                // Recycle cookies in case correct context is
                                // configured with different settings
                                req.getCookies().recycle();
                            }
                            break;
                        }
                    }
                }
            }

            if (!mapRequired && request.getContext().getPaused()) {
                // Found a matching context but it is paused. Mapping data will
                // be wrong since some Wrappers may not be registered at this
                // point.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Should never happen
                }
                // Reset mapping
                request.getMappingData().recycle();
                mapRequired = true;
            }
        }

        // Possible redirect
        MessageBytes redirectPathMB = request.getMappingData().redirectPath;
        if (!redirectPathMB.isNull()) {
            String redirectPath = URLEncoder.DEFAULT.encode(redirectPathMB.toString());
            String query = request.getQueryString();
            if (request.isRequestedSessionIdFromURL()) {
                // This is not optimal, but as this is not very common, it
                // shouldn't matter
                redirectPath = redirectPath + ";" +
                        SessionConfig.getSessionUriParamName(
                            request.getContext()) +
                    "=" + request.getRequestedSessionId();
            }
            if (query != null) {
                // This is not optimal, but as this is not very common, it
                // shouldn't matter
                redirectPath = redirectPath + "?" + query;
            }
            response.sendRedirect(redirectPath);
            request.getContext().logAccess(request, response, 0, true);
            return false;
        }

        // Filter trace method
        if (!connector.getAllowTrace()
                && req.method().equalsIgnoreCase("TRACE")) {
            Wrapper wrapper = request.getWrapper();
            String header = null;
            if (wrapper != null) {
                String[] methods = wrapper.getServletMethods();
                if (methods != null) {
                    for (int i=0; i<methods.length; i++) {
                        if ("TRACE".equals(methods[i])) {
                            continue;
                        }
                        if (header == null) {
                            header = methods[i];
                        } else {
                            header += ", " + methods[i];
                        }
                    }
                }
            }
            res.setStatus(405);
            res.addHeader("Allow", header);
            res.setMessage("TRACE method is not allowed");
            request.getContext().logAccess(request, response, 0, true);
            return false;
        }

        doConnectorAuthenticationAuthorization(req, request);

        return true;
    }


    private void doConnectorAuthenticationAuthorization(org.apache.coyote.Request req, Request request) {
        // Set the remote principal
        String username = req.getRemoteUser().toString();
        if (username != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("coyoteAdapter.authenticate", username));
            }
            if (req.getRemoteUserNeedsAuthorization()) {
                Authenticator authenticator = request.getContext().getAuthenticator();
                if (authenticator == null) {
                    // No security constraints configured for the application so
                    // no need to authorize the user. Use the CoyotePrincipal to
                    // provide the authenticated user.
                    request.setUserPrincipal(new CoyotePrincipal(username));
                } else if (!(authenticator instanceof AuthenticatorBase)) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("coyoteAdapter.authorize", username));
                    }
                    // Custom authenticator that may not trigger authorization.
                    // Do the authorization here to make sure it is done.
                    request.setUserPrincipal(
                            request.getContext().getRealm().authenticate(username));
                }
                // If the Authenticator is an instance of AuthenticatorBase then
                // it will check req.getRemoteUserNeedsAuthorization() and
                // trigger authorization as necessary. It will also cache the
                // result preventing excessive calls to the Realm.
            } else {
                // The connector isn't configured for authorization. Create a
                // user without any roles using the supplied user name.
                request.setUserPrincipal(new CoyotePrincipal(username));
            }
        }

        // Set the authorization type
        String authtype = req.getAuthType().toString();
        if (authtype != null) {
            request.setAuthType(authtype);
        }
    }


    /**
     * Extract the path parameters from the request. This assumes parameters are
     * of the form /path;name=value;name2=value2/ etc. Currently only really
     * interested in the session ID that will be in this form. Other parameters
     * can safely be ignored.
     *
     * @param req
     * @param request
     */
    protected void parsePathParameters(org.apache.coyote.Request req,
            Request request) {

        // Process in bytes (this is default format so this is normally a NO-OP
        req.decodedURI().toBytes();

        ByteChunk uriBC = req.decodedURI().getByteChunk();
        int semicolon = uriBC.indexOf(';', 0);

        // What encoding to use? Some platforms, eg z/os, use a default
        // encoding that doesn't give the expected result so be explicit
        String enc = connector.getURIEncodingLower();
        if (enc == null) {
            enc = "iso-8859-1";
        }
        Charset charset = null;
        try {
            charset = B2CConverter.getCharsetLower(enc);
        } catch (UnsupportedEncodingException e1) {
            log.warn(sm.getString("coyoteAdapter.parsePathParam",
                    enc));
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("coyoteAdapter.debug", "uriBC",
                    uriBC.toString()));
            log.debug(sm.getString("coyoteAdapter.debug", "semicolon",
                    String.valueOf(semicolon)));
            log.debug(sm.getString("coyoteAdapter.debug", "enc", enc));
        }

        while (semicolon > -1) {
            // Parse path param, and extract it from the decoded request URI
            int start = uriBC.getStart();
            int end = uriBC.getEnd();

            int pathParamStart = semicolon + 1;
            int pathParamEnd = ByteChunk.findBytes(uriBC.getBuffer(),
                    start + pathParamStart, end,
                    new byte[] {';', '/'});

            String pv = null;

            if (pathParamEnd >= 0) {
                if (charset != null) {
                    pv = new String(uriBC.getBuffer(), start + pathParamStart,
                                pathParamEnd - pathParamStart, charset);
                }
                // Extract path param from decoded request URI
                byte[] buf = uriBC.getBuffer();
                for (int i = 0; i < end - start - pathParamEnd; i++) {
                    buf[start + semicolon + i]
                        = buf[start + i + pathParamEnd];
                }
                uriBC.setBytes(buf, start,
                        end - start - pathParamEnd + semicolon);
            } else {
                if (charset != null) {
                    pv = new String(uriBC.getBuffer(), start + pathParamStart,
                                (end - start) - pathParamStart, charset);
                }
                uriBC.setEnd(start + semicolon);
            }

            if (log.isDebugEnabled()) {
                log.debug(sm.getString("coyoteAdapter.debug", "pathParamStart",
                        String.valueOf(pathParamStart)));
                log.debug(sm.getString("coyoteAdapter.debug", "pathParamEnd",
                        String.valueOf(pathParamEnd)));
                log.debug(sm.getString("coyoteAdapter.debug", "pv", pv));
            }

            if (pv != null) {
                int equals = pv.indexOf('=');
                if (equals > -1) {
                    String name = pv.substring(0, equals);
                    String value = pv.substring(equals + 1);
                    request.addPathParameter(name, value);
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("coyoteAdapter.debug", "equals",
                                String.valueOf(equals)));
                        log.debug(sm.getString("coyoteAdapter.debug", "name",
                                name));
                        log.debug(sm.getString("coyoteAdapter.debug", "value",
                                value));
                    }
                }
            }

            semicolon = uriBC.indexOf(';', semicolon);
        }
    }


    /**
     * Look for SSL session ID if required. Only look for SSL Session ID if it
     * is the only tracking method enabled.
     */
    protected void parseSessionSslId(Request request) {
        if (request.getRequestedSessionId() == null &&
                SSL_ONLY.equals(request.getServletContext()
                        .getEffectiveSessionTrackingModes()) &&
                        request.connector.secure) {
            request.setRequestedSessionId(
                    request.getAttribute(SSLSupport.SESSION_ID_KEY).toString());
            request.setRequestedSessionSSL(true);
        }
    }


    /**
     * Parse session id in URL.
     */
    protected void parseSessionCookiesId(Request request) {

        // If session tracking via cookies has been disabled for the current
        // context, don't go looking for a session ID in a cookie as a cookie
        // from a parent context with a session ID may be present which would
        // overwrite the valid session ID encoded in the URL
        Context context = request.getMappingData().context;
        if (context != null && !context.getServletContext()
                .getEffectiveSessionTrackingModes().contains(
                        SessionTrackingMode.COOKIE)) {
            return;
        }

        // Parse session id from cookies
        ServerCookies serverCookies = request.getServerCookies();
        int count = serverCookies.getCookieCount();
        if (count <= 0) {
            return;
        }

        String sessionCookieName = SessionConfig.getSessionCookieName(context);

        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            if (scookie.getName().equals(sessionCookieName)) {
                // Override anything requested in the URL
                if (!request.isRequestedSessionIdFromCookie()) {
                    // Accept only the first session id cookie
                    convertMB(scookie.getValue());
                    request.setRequestedSessionId
                        (scookie.getValue().toString());
                    request.setRequestedSessionCookie(true);
                    request.setRequestedSessionURL(false);
                    if (log.isDebugEnabled()) {
                        log.debug(" Requested cookie session id is " +
                            request.getRequestedSessionId());
                    }
                } else {
                    if (!request.isRequestedSessionIdValid()) {
                        // Replace the session id until one is valid
                        convertMB(scookie.getValue());
                        request.setRequestedSessionId
                            (scookie.getValue().toString());
                    }
                }
            }
        }

    }


    /**
     * Character conversion of the URI.
     */
    protected void convertURI(MessageBytes uri, Request request) throws IOException {

        ByteChunk bc = uri.getByteChunk();
        int length = bc.getLength();
        CharChunk cc = uri.getCharChunk();
        cc.allocate(length, -1);

        String enc = connector.getURIEncoding();
        if (enc != null) {
            B2CConverter conv = request.getURIConverter();
            try {
                if (conv == null) {
                    conv = new B2CConverter(enc, true);
                    request.setURIConverter(conv);
                } else {
                    conv.recycle();
                }
            } catch (IOException e) {
                log.error("Invalid URI encoding; using HTTP default");
                connector.setURIEncoding(null);
            }
            if (conv != null) {
                try {
                    conv.convert(bc, cc, true);
                    uri.setChars(cc.getBuffer(), cc.getStart(), cc.getLength());
                    return;
                } catch (IOException ioe) {
                    // Should never happen as B2CConverter should replace
                    // problematic characters
                    request.getResponse().sendError(
                            HttpServletResponse.SC_BAD_REQUEST);
                }
            }
        }

        // Default encoding: fast conversion for ISO-8859-1
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < length; i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        uri.setChars(cbuf, 0, length);
    }


    /**
     * Character conversion of the a US-ASCII MessageBytes.
     */
    protected void convertMB(MessageBytes mb) {

        // This is of course only meaningful for bytes
        if (mb.getType() != MessageBytes.T_BYTES) {
            return;
        }

        ByteChunk bc = mb.getByteChunk();
        CharChunk cc = mb.getCharChunk();
        int length = bc.getLength();
        cc.allocate(length, -1);

        // Default encoding: fast conversion
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < length; i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        mb.setChars(cbuf, 0, length);

    }


    /**
     * This method normalizes "\", "//", "/./" and "/../".
     *
     * @param uriMB URI to be normalized
     *
     * @return <code>false</code> if normalizing this URI would require going
     *         above the root, or if the URI contains a null byte, otherwise
     *         <code>true</code>
     */
    public static boolean normalize(MessageBytes uriMB) {

        ByteChunk uriBC = uriMB.getByteChunk();
        final byte[] b = uriBC.getBytes();
        final int start = uriBC.getStart();
        int end = uriBC.getEnd();

        // An empty URL is not acceptable
        if (start == end) {
            return false;
        }

        // URL * is acceptable
        if ((end - start == 1) && b[start] == (byte) '*') {
            return true;
        }

        int pos = 0;
        int index = 0;

        // Replace '\' with '/'
        // Check for null byte
        for (pos = start; pos < end; pos++) {
            if (b[pos] == (byte) '\\') {
                if (ALLOW_BACKSLASH) {
                    b[pos] = (byte) '/';
                } else {
                    return false;
                }
            }
            if (b[pos] == (byte) 0) {
                return false;
            }
        }

        // The URL must start with '/'
        if (b[start] != (byte) '/') {
            return false;
        }

        // Replace "//" with "/"
        for (pos = start; pos < (end - 1); pos++) {
            if (b[pos] == (byte) '/') {
                while ((pos + 1 < end) && (b[pos + 1] == (byte) '/')) {
                    copyBytes(b, pos, pos + 1, end - pos - 1);
                    end--;
                }
            }
        }

        // If the URI ends with "/." or "/..", then we append an extra "/"
        // Note: It is possible to extend the URI by 1 without any side effect
        // as the next character is a non-significant WS.
        if (((end - start) >= 2) && (b[end - 1] == (byte) '.')) {
            if ((b[end - 2] == (byte) '/')
                || ((b[end - 2] == (byte) '.')
                    && (b[end - 3] == (byte) '/'))) {
                b[end] = (byte) '/';
                end++;
            }
        }

        uriBC.setEnd(end);

        index = 0;

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            index = uriBC.indexOf("/./", 0, 3, index);
            if (index < 0) {
                break;
            }
            copyBytes(b, start + index, start + index + 2,
                      end - start - index - 2);
            end = end - 2;
            uriBC.setEnd(end);
        }

        index = 0;

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            index = uriBC.indexOf("/../", 0, 4, index);
            if (index < 0) {
                break;
            }
            // Prevent from going outside our context
            if (index == 0) {
                return false;
            }
            int index2 = -1;
            for (pos = start + index - 1; (pos >= 0) && (index2 < 0); pos --) {
                if (b[pos] == (byte) '/') {
                    index2 = pos;
                }
            }
            copyBytes(b, start + index2, start + index + 3,
                      end - start - index - 3);
            end = end + index2 - index - 3;
            uriBC.setEnd(end);
            index = index2;
        }

        return true;

    }


    /**
     * Check that the URI is normalized following character decoding. This
     * method checks for "\", 0, "//", "/./" and "/../".
     *
     * @param uriMB URI to be checked (should be chars)
     *
     * @return <code>false</code> if sequences that are supposed to be
     *         normalized are still present in the URI, otherwise
     *         <code>true</code>
     */
    public static boolean checkNormalize(MessageBytes uriMB) {

        CharChunk uriCC = uriMB.getCharChunk();
        char[] c = uriCC.getChars();
        int start = uriCC.getStart();
        int end = uriCC.getEnd();

        int pos = 0;

        // Check for '\' and 0
        for (pos = start; pos < end; pos++) {
            if (c[pos] == '\\') {
                return false;
            }
            if (c[pos] == 0) {
                return false;
            }
        }

        // Check for "//"
        for (pos = start; pos < (end - 1); pos++) {
            if (c[pos] == '/') {
                if (c[pos + 1] == '/') {
                    return false;
                }
            }
        }

        // Check for ending with "/." or "/.."
        if (((end - start) >= 2) && (c[end - 1] == '.')) {
            if ((c[end - 2] == '/')
                    || ((c[end - 2] == '.')
                    && (c[end - 3] == '/'))) {
                return false;
            }
        }

        // Check for "/./"
        if (uriCC.indexOf("/./", 0, 3, 0) >= 0) {
            return false;
        }

        // Check for "/../"
        if (uriCC.indexOf("/../", 0, 4, 0) >= 0) {
            return false;
        }

        return true;

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Copy an array of bytes to a different position. Used during
     * normalization.
     */
    protected static void copyBytes(byte[] b, int dest, int src, int len) {
        for (int pos = 0; pos < len; pos++) {
            b[pos + dest] = b[pos + src];
        }
    }
}
