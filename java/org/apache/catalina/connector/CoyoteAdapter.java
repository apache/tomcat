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

import javax.servlet.RequestDispatcher;
import javax.servlet.SessionTrackingMode;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.comet.CometEvent.EventType;
import org.apache.catalina.core.ApplicationSessionCookieConfig;
import org.apache.catalina.core.AsyncContextImpl;
import org.apache.catalina.util.ServerInfo;
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
import org.apache.tomcat.util.http.Cookies;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.res.StringManager;


/**
 * Implementation of a request processor which delegates the processing to a
 * Coyote processor.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Id$
 */
public class CoyoteAdapter implements Adapter {

    private static final Log log = LogFactory.getLog(CoyoteAdapter.class);

    // -------------------------------------------------------------- Constants

    private static final String POWERED_BY = "Servlet/3.0 JSP/2.2 " +
            "(" + ServerInfo.getServerInfo() + " Java/" +
            System.getProperty("java.vm.vendor") + "/" +
            System.getProperty("java.runtime.version") + ")";

    private static final EnumSet<SessionTrackingMode> SSL_ONLY =
        EnumSet.of(SessionTrackingMode.SSL);

    public static final int ADAPTER_NOTES = 1;


    protected static final boolean ALLOW_BACKSLASH =
        Boolean.valueOf(System.getProperty("org.apache.catalina.connector.CoyoteAdapter.ALLOW_BACKSLASH", "false")).booleanValue();


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
    private Connector connector = null;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * Encoder for the Location URL in HTTP redirects.
     */
    protected static URLEncoder urlEncoder;


    // ----------------------------------------------------- Static Initializer


    /**
     * The safe character set.
     */
    static {
        urlEncoder = new URLEncoder();
        urlEncoder.addSafeCharacter('-');
        urlEncoder.addSafeCharacter('_');
        urlEncoder.addSafeCharacter('.');
        urlEncoder.addSafeCharacter('*');
        urlEncoder.addSafeCharacter('/');
    }


    // -------------------------------------------------------- Adapter Methods


    /**
     * Event method.
     *
     * @return false to indicate an error, expected or not
     */
    @Override
    public boolean event(org.apache.coyote.Request req,
            org.apache.coyote.Response res, SocketStatus status) {

        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request.getWrapper() == null) {
            return false;
        }

        boolean error = false;
        boolean read = false;
        try {
            if (status == SocketStatus.OPEN) {
                if (response.isClosed()) {
                    // The event has been closed asynchronously, so call end instead of
                    // read to cleanup the pipeline
                    request.getEvent().setEventType(CometEvent.EventType.END);
                    request.getEvent().setEventSubType(null);
                } else {
                    try {
                        // Fill the read buffer of the servlet layer
                        if (request.read()) {
                            read = true;
                        }
                    } catch (IOException e) {
                        error = true;
                    }
                    if (read) {
                        request.getEvent().setEventType(CometEvent.EventType.READ);
                        request.getEvent().setEventSubType(null);
                    } else if (error) {
                        request.getEvent().setEventType(CometEvent.EventType.ERROR);
                        request.getEvent().setEventSubType(CometEvent.EventSubType.CLIENT_DISCONNECT);
                    } else {
                        request.getEvent().setEventType(CometEvent.EventType.END);
                        request.getEvent().setEventSubType(null);
                    }
                }
            } else if (status == SocketStatus.DISCONNECT) {
                request.getEvent().setEventType(CometEvent.EventType.ERROR);
                request.getEvent().setEventSubType(CometEvent.EventSubType.CLIENT_DISCONNECT);
                error = true;
            } else if (status == SocketStatus.ERROR) {
                request.getEvent().setEventType(CometEvent.EventType.ERROR);
                request.getEvent().setEventSubType(CometEvent.EventSubType.IOEXCEPTION);
                error = true;
            } else if (status == SocketStatus.STOP) {
                request.getEvent().setEventType(CometEvent.EventType.END);
                request.getEvent().setEventSubType(CometEvent.EventSubType.SERVER_SHUTDOWN);
            } else if (status == SocketStatus.TIMEOUT) {
                if (response.isClosed()) {
                    // The event has been closed asynchronously, so call end instead of
                    // read to cleanup the pipeline
                    request.getEvent().setEventType(CometEvent.EventType.END);
                    request.getEvent().setEventSubType(null);
                } else {
                    request.getEvent().setEventType(CometEvent.EventType.ERROR);
                    request.getEvent().setEventSubType(CometEvent.EventSubType.TIMEOUT);
                }
            }

            req.getRequestProcessor().setWorkerThreadName(Thread.currentThread().getName());

            // Calling the container
            connector.getService().getContainer().getPipeline().getFirst().event(request, response, request.getEvent());

            if (!error && !response.isClosed() && (request.getAttribute(
                    RequestDispatcher.ERROR_EXCEPTION) != null)) {
                // An unexpected exception occurred while processing the event, so
                // error should be called
                request.getEvent().setEventType(CometEvent.EventType.ERROR);
                request.getEvent().setEventSubType(null);
                error = true;
                connector.getService().getContainer().getPipeline().getFirst().event(request, response, request.getEvent());
            }
            if (response.isClosed() || !request.isComet()) {
                if (status==SocketStatus.OPEN &&
                        request.getEvent().getEventType() != EventType.END) {
                    //CometEvent.close was called during an event other than END
                    request.getEvent().setEventType(CometEvent.EventType.END);
                    request.getEvent().setEventSubType(null);
                    error = true;
                    connector.getService().getContainer().getPipeline().getFirst().event(request, response, request.getEvent());
                }
                res.action(ActionCode.COMET_END, null);
            } else if (!error && read && request.getAvailable()) {
                // If this was a read and not all bytes have been read, or if no data
                // was read from the connector, then it is an error
                request.getEvent().setEventType(CometEvent.EventType.ERROR);
                request.getEvent().setEventSubType(CometEvent.EventSubType.IOEXCEPTION);
                error = true;
                connector.getService().getContainer().getPipeline().getFirst().event(request, response, request.getEvent());
            }
            return (!error);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            if (!(t instanceof IOException)) {
                log.error(sm.getString("coyoteAdapter.service"), t);
            }
            error = true;
            return false;
        } finally {
            req.getRequestProcessor().setWorkerThreadName(null);
            // Recycle the wrapper request and response
            if (error || response.isClosed() || !request.isComet()) {
                ((Context) request.getMappingData().context).logAccess(
                        request, response,
                        System.currentTimeMillis() - req.getStartTime(),
                        false);
                request.recycle();
                request.setFilterChain(null);
                response.recycle();
            }
        }
    }

    @Override
    public boolean asyncDispatch(org.apache.coyote.Request req,
            org.apache.coyote.Response res, SocketStatus status) throws Exception {
        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request == null) {
            throw new IllegalStateException(
                    "Dispatch may only happen on an existing request.");
        }
        boolean comet = false;
        boolean success = true;
        AsyncContextImpl asyncConImpl = (AsyncContextImpl)request.getAsyncContext();
        req.getRequestProcessor().setWorkerThreadName(Thread.currentThread().getName());
        try {
            if (!request.isAsync() && !comet) {
                // Error or timeout - need to tell listeners the request is over
                // Have to test this first since state may change while in this
                // method and this is only required if entering this method in
                // this state
                Context ctxt = (Context) request.getMappingData().context;
                if (ctxt != null) {
                    ctxt.fireRequestDestroyEvent(request);
                }
                // Lift any suspension (e.g. if sendError() was used by an async
                // request
                response.setSuspended(false);
            }

            if (status==SocketStatus.TIMEOUT) {
                success = true;
                if (!asyncConImpl.timeout()) {
                    asyncConImpl.setErrorState(null);
                }
            }
            if (request.isAsyncDispatching()) {
                success = true;
                connector.getService().getContainer().getPipeline().getFirst().invoke(request, response);
                Throwable t = (Throwable) request.getAttribute(
                        RequestDispatcher.ERROR_EXCEPTION);
                if (t != null) {
                    asyncConImpl.setErrorState(t);
                }
            }

            if (request.isComet()) {
                if (!response.isClosed() && !response.isError()) {
                    if (request.getAvailable() || (request.getContentLength() > 0 && (!request.isParametersParsed()))) {
                        // Invoke a read event right away if there are available bytes
                        if (event(req, res, SocketStatus.OPEN)) {
                            comet = true;
                            res.action(ActionCode.COMET_BEGIN, null);
                        }
                    } else {
                        comet = true;
                        res.action(ActionCode.COMET_BEGIN, null);
                    }
                } else {
                    // Clear the filter chain, as otherwise it will not be reset elsewhere
                    // since this is a Comet request
                    request.setFilterChain(null);
                }
            }
            if (!request.isAsync() && !comet) {
                request.finishRequest();
                response.finishResponse();
                req.action(ActionCode.POST_REQUEST , null);
                ((Context) request.getMappingData().context).logAccess(
                        request, response,
                        System.currentTimeMillis() - req.getStartTime(),
                        false);
            }

        } catch (IOException e) {
            success = false;
            // Ignore
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            success = false;
            log.error(sm.getString("coyoteAdapter.service"), t);
        } finally {
            req.getRequestProcessor().setWorkerThreadName(null);
            // Recycle the wrapper request and response
            if (!success || (!comet && !request.isAsync())) {
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

        boolean comet = false;
        boolean async = false;

        try {

            // Parse and set Catalina and configuration specific
            // request parameters
            req.getRequestProcessor().setWorkerThreadName(Thread.currentThread().getName());
            boolean postParseSuccess = postParseRequest(req, request, res, response);
            if (postParseSuccess) {
                //check valves if we support async
                request.setAsyncSupported(connector.getService().getContainer().getPipeline().isAsyncSupported());
                // Calling the container
                connector.getService().getContainer().getPipeline().getFirst().invoke(request, response);

                if (request.isComet()) {
                    if (!response.isClosed() && !response.isError()) {
                        if (request.getAvailable() || (request.getContentLength() > 0 && (!request.isParametersParsed()))) {
                            // Invoke a read event right away if there are available bytes
                            if (event(req, res, SocketStatus.OPEN)) {
                                comet = true;
                                res.action(ActionCode.COMET_BEGIN, null);
                            }
                        } else {
                            comet = true;
                            res.action(ActionCode.COMET_BEGIN, null);
                        }
                    } else {
                        // Clear the filter chain, as otherwise it will not be reset elsewhere
                        // since this is a Comet request
                        request.setFilterChain(null);
                    }
                }

            }
            AsyncContextImpl asyncConImpl = (AsyncContextImpl)request.getAsyncContext();
            if (asyncConImpl != null) {
                async = true;
            } else if (!comet) {
                request.finishRequest();
                response.finishResponse();
                if (postParseSuccess &&
                        request.getMappingData().context != null) {
                    // Log only if processing was invoked.
                    // If postParseRequest() failed, it has already logged it.
                    // If context is null this was the start of a comet request
                    // that failed and has already been logged.
                    ((Context) request.getMappingData().context).logAccess(
                            request, response,
                            System.currentTimeMillis() - req.getStartTime(),
                            false);
                }
                req.action(ActionCode.POST_REQUEST , null);
            }

        } catch (IOException e) {
            // Ignore
        } finally {
            req.getRequestProcessor().setWorkerThreadName(null);
            // Recycle the wrapper request and response
            if (!comet && !async) {
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
                    ((Context) request.mappingData.context).logAccess(
                            request, response, time, true);
                } else if (request.mappingData.host != null) {
                    logged = true;
                    ((Host) request.mappingData.host).logAccess(
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


    @Override
    public String getDomain() {
        return connector.getDomain();
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Parse additional request parameters.
     */
    protected boolean postParseRequest(org.apache.coyote.Request req,
                                       Request request,
                                       org.apache.coyote.Response res,
                                       Response response)
            throws Exception {

        // XXX the processor may have set a correct scheme and port prior to this point,
        // in ajp13 protocols dont make sense to get the port from the connector...
        // otherwise, use connector configuration
        if (! req.scheme().isNull()) {
            // use processor specified scheme to determine secure state
            request.setSecure(req.scheme().equals("https"));
        } else {
            // use connector scheme and secure configuration, (defaults to
            // "http" and false respectively)
            req.scheme().setString(connector.getScheme());
            request.setSecure(connector.getSecure());
        }

        // FIXME: the code below doesnt belongs to here,
        // this is only have sense
        // in Http11, not in ajp13..
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

        // Copy the raw URI to the decodedURI
        MessageBytes decodedURI = req.decodedURI();
        decodedURI.duplicate(req.requestURI());

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

        // Set the remote principal
        String principal = req.getRemoteUser().toString();
        if (principal != null) {
            request.setUserPrincipal(new CoyotePrincipal(principal));
        }

        // Set the authorization type
        String authtype = req.getAuthType().toString();
        if (authtype != null) {
            request.setAuthType(authtype);
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
        if (request.isAsyncStarted()) {
            //TODO SERVLET3 - async
            //reset mapping data, should prolly be done elsewhere
            request.getMappingData().recycle();
        }

        boolean mapRequired = true;
        String version = null;

        while (mapRequired) {
            if (version != null) {
                // Once we have a version - that is it
                mapRequired = false;
            }
            // This will map the the latest version by default
            connector.getMapper().map(serverName, decodedURI, version,
                                      request.getMappingData());
            request.setContext((Context) request.getMappingData().context);
            request.setWrapper((Wrapper) request.getMappingData().wrapper);

            // Single contextVersion therefore no possibility of remap
            if (request.getMappingData().contexts == null) {
                mapRequired = false;
            }

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
            String sessionID = null;
            if (request.getServletContext().getEffectiveSessionTrackingModes()
                    .contains(SessionTrackingMode.URL)) {

                // Get the session ID if there was one
                sessionID = request.getPathParameter(
                        ApplicationSessionCookieConfig.getSessionUriParamName(
                                request.getContext()));
                if (sessionID != null) {
                    request.setRequestedSessionId(sessionID);
                    request.setRequestedSessionURL(true);
                }
            }

            // Look for session ID in cookies and SSL session
            parseSessionCookiesId(req, request);
            parseSessionSslId(request);

            sessionID = request.getRequestedSessionId();

            if (mapRequired) {
                if (sessionID == null) {
                    // No session means no possibility of needing to remap
                    mapRequired = false;
                } else {
                    // Find the context associated with the session
                    Object[] objs = request.getMappingData().contexts;
                    for (int i = (objs.length); i > 0; i--) {
                        Context ctxt = (Context) objs[i - 1];
                        if (ctxt.getManager().findSession(sessionID) != null) {
                            // Was the correct context already mapped?
                            if (ctxt.equals(request.getMappingData().context)) {
                                mapRequired = false;
                            } else {
                                // Set version so second time through mapping the
                                // correct context is found
                                version = ctxt.getWebappVersion();
                                // Reset mapping
                                request.getMappingData().recycle();
                                break;
                            }
                        }
                    }
                    if (version == null) {
                        // No matching context found. No need to re-map
                        mapRequired = false;
                    }
                }
            }

        }

        // Possible redirect
        MessageBytes redirectPathMB = request.getMappingData().redirectPath;
        if (!redirectPathMB.isNull()) {
            String redirectPath = urlEncoder.encode(redirectPathMB.toString());
            String query = request.getQueryString();
            if (request.isRequestedSessionIdFromURL()) {
                // This is not optimal, but as this is not very common, it
                // shouldn't matter
                redirectPath = redirectPath + ";" +
                    ApplicationSessionCookieConfig.getSessionUriParamName(
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

        return true;
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
        String enc = connector.getURIEncoding();
        if (enc == null) {
            enc = "ISO-8859-1";
        }
        Charset charset = null;
        try {
            charset = B2CConverter.getCharset(enc);
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
            // TODO Is there a better way to map SSL sessions to our sesison ID?
            // TODO The request.getAttribute() will cause a number of other SSL
            //      attribute to be populated. Is this a performance concern?
            request.setRequestedSessionId(
                    request.getAttribute(SSLSupport.SESSION_ID_KEY).toString());
            request.setRequestedSessionSSL(true);
        }
    }


    /**
     * Parse session id in URL.
     */
    protected void parseSessionCookiesId(org.apache.coyote.Request req, Request request) {

        // If session tracking via cookies has been disabled for the current
        // context, don't go looking for a session ID in a cookie as a cookie
        // from a parent context with a session ID may be present which would
        // overwrite the valid session ID encoded in the URL
        Context context = (Context) request.getMappingData().context;
        if (context != null && !context.getServletContext()
                .getEffectiveSessionTrackingModes().contains(
                        SessionTrackingMode.COOKIE)) {
            return;
        }

        // Parse session id from cookies
        Cookies serverCookies = req.getCookies();
        int count = serverCookies.getCookieCount();
        if (count <= 0) {
            return;
        }

        String sessionCookieName =
            ApplicationSessionCookieConfig.getSessionCookieName(context);

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
    protected void convertURI(MessageBytes uri, Request request)
        throws Exception {

        ByteChunk bc = uri.getByteChunk();
        int length = bc.getLength();
        CharChunk cc = uri.getCharChunk();
        cc.allocate(length, -1);

        String enc = connector.getURIEncoding();
        if (enc != null) {
            B2CConverter conv = request.getURIConverter();
            try {
                if (conv == null) {
                    conv = new B2CConverter(enc);
                    request.setURIConverter(conv);
                }
            } catch (IOException e) {
                // Ignore
                log.error("Invalid URI encoding; using HTTP default");
                connector.setURIEncoding(null);
            }
            if (conv != null) {
                try {
                    conv.convert(bc, cc, cc.getBuffer().length - cc.getEnd());
                    uri.setChars(cc.getBuffer(), cc.getStart(),
                                 cc.getLength());
                    return;
                } catch (IOException e) {
                    log.error("Invalid URI character encoding; trying ascii");
                    cc.recycle();
                }
            }
        }

        // Default encoding: fast conversion
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
     * Normalize URI.
     * <p>
     * This method normalizes "\", "//", "/./" and "/../". This method will
     * return false when trying to go above the root, or if the URI contains
     * a null byte.
     *
     * @param uriMB URI to be normalized
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
     * Check that the URI is normalized following character decoding.
     * <p>
     * This method checks for "\", 0, "//", "/./" and "/../". This method will
     * return false if sequences that are supposed to be normalized are still
     * present in the URI.
     *
     * @param uriMB URI to be checked (should be chars)
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
