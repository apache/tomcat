/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.apache.catalina.CometProcessor;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.Cookies;
import org.apache.tomcat.util.http.ServerCookie;


/**
 * Implementation of a request processor which delegates the processing to a
 * Coyote processor.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision: 331249 $ $Date: 2005-11-07 10:57:55 +0100 (lun., 07 nov. 2005) $
 */

public class CoyoteAdapter
    implements Adapter 
 {
    private static Log log = LogFactory.getLog(CoyoteAdapter.class);

    // -------------------------------------------------------------- Constants


    public static final int ADAPTER_NOTES = 1;


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
     * The match string for identifying a session ID parameter.
     */
    private static final String match =
        ";" + Globals.SESSION_PARAMETER_NAME + "=";


    /**
     * The match string for identifying a session ID parameter.
     */
    private static final char[] SESSION_ID = match.toCharArray();


    /**
     * The string manager for this package.
     */
    protected StringManager sm =
        StringManager.getManager(Constants.Package);


    // -------------------------------------------------------- Adapter Methods

    
    /**
     * Event method.
     * 
     * @return false to indicate an error, expected or not
     */
    public boolean event(org.apache.coyote.Request req, 
            org.apache.coyote.Response res, boolean error) {

        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request.getWrapper() != null) {
            
            // Bind the context CL to the current thread
            if (request.getContext().getLoader() != null ) {
                Thread.currentThread().setContextClassLoader
                        (request.getContext().getLoader().getClassLoader());
            }
            
            CometProcessor servlet = null;
            try {
                servlet = (CometProcessor) request.getWrapper().allocate();
            } catch (Throwable t) {
                log.error(sm.getString("coyoteAdapter.service"), t);
                request.removeAttribute("org.apache.tomcat.comet");
                // Restore the context classloader
                Thread.currentThread().setContextClassLoader
                    (CoyoteAdapter.class.getClassLoader());
                return false;
            }
            try {
                if (error) {
                    servlet.error(request.getRequest(), response.getResponse());
                } else {
                    if (!servlet.read(request.getRequest(), response.getResponse())) {
                        error = true;
                        request.removeAttribute("org.apache.tomcat.comet");
                        try {
                            servlet.error(request.getRequest(), response.getResponse());
                        } catch (Throwable th) {
                            log.error(sm.getString("coyoteAdapter.service"), th);
                        }
                    }
                }
                return (!error);
            } catch (Throwable t) {
                if (!(t instanceof IOException)) {
                    log.error(sm.getString("coyoteAdapter.service"), t);
                }
                request.removeAttribute("org.apache.tomcat.comet");
                try {
                    servlet.error(request.getRequest(), response.getResponse());
                } catch (Throwable th) {
                    log.error(sm.getString("coyoteAdapter.service"), th);
                }
                return false;
            } finally {
                // Recycle the wrapper request and response
                if (request.getAttribute("org.apache.tomcat.comet") == null) {
                    request.recycle();
                    response.recycle();
                }
                // Restore the context classloader
                Thread.currentThread().setContextClassLoader
                    (CoyoteAdapter.class.getClassLoader());
            }
        }
        return true;
    }
    

    /**
     * Service method.
     */
    public void service(org.apache.coyote.Request req, 
    	                org.apache.coyote.Response res)
        throws Exception {

        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request == null) {

            // Create objects
            request = (Request) connector.createRequest();
            request.setCoyoteRequest(req);
            response = (Response) connector.createResponse();
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
            response.addHeader("X-Powered-By", "Servlet/2.5");
        }

        boolean comet = false;
        
        try {

            // Parse and set Catalina and configuration specific 
            // request parameters
            if ( postParseRequest(req, request, res, response) ) {
                // Calling the container
                connector.getContainer().getPipeline().getFirst().invoke(request, response);
            }

            if (request.getAttribute("org.apache.tomcat.comet") == Boolean.TRUE
                    && request.getWrapper().allocate() instanceof CometProcessor) {
                comet = true;
            }

            if (!comet) {
                response.finishResponse();
                req.action( ActionCode.ACTION_POST_REQUEST , null);
            }

        } catch (IOException e) {
            ;
        } catch (Throwable t) {
            log.error(sm.getString("coyoteAdapter.service"), t);
        } finally {
            // Recycle the wrapper request and response
            if (!comet) {
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


    // ------------------------------------------------------ Protected Methods


    /**
     * Parse additional request parameters.
     */
    protected boolean postParseRequest(org.apache.coyote.Request req, 
                                       Request request,
    		                       org.apache.coyote.Response res, 
                                       Response response)
            throws Exception {

        // XXX the processor needs to set a correct scheme and port prior to this point, 
        // in ajp13 protocols dont make sense to get the port from the connector..
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

        // URI decoding
        MessageBytes decodedURI = req.decodedURI();
        decodedURI.duplicate(req.requestURI());

        if (decodedURI.getType() == MessageBytes.T_BYTES) {
            // %xx decoding of the URL
            try {
                req.getURLDecoder().convert(decodedURI, false);
            } catch (IOException ioe) {
                res.setStatus(400);
                res.setMessage("Invalid URI");
                throw ioe;
            }
            // Normalization
            if (!normalize(req.decodedURI())) {
                res.setStatus(400);
                res.setMessage("Invalid URI");
                return false;
            }
            // Character decoding
            convertURI(decodedURI, request);
        } else {
            // The URL is chars or String, and has been sent using an in-memory
            // protocol handler, we have to assume the URL has been properly
            // decoded already
            decodedURI.toChars();
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

        // Parse session Id
        parseSessionId(req, request);

        // Remove any remaining parameters (other than session id, which has
        // already been removed in parseSessionId()) from the URI, so they
        // won't be considered by the mapping algorithm.
        CharChunk uriCC = decodedURI.getCharChunk();
        int semicolon = uriCC.indexOf(';');
        if (semicolon > 0) {
            decodedURI.setChars
                (uriCC.getBuffer(), uriCC.getStart(), semicolon);
        }

        // Request mapping.
        MessageBytes serverName;
        if (connector.getUseIPVHosts()) {
            serverName = req.localName();
            if (serverName.isNull()) {
                // well, they did ask for it
                res.action(ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE, null);
            }
        } else {
            serverName = req.serverName();
        }
        connector.getMapper().map(serverName, decodedURI, 
                                  request.getMappingData());
        request.setContext((Context) request.getMappingData().context);
        request.setWrapper((Wrapper) request.getMappingData().wrapper);

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
            return false;
        }

        // Possible redirect
        MessageBytes redirectPathMB = request.getMappingData().redirectPath;
        if (!redirectPathMB.isNull()) {
            String redirectPath = redirectPathMB.toString();
            String query = request.getQueryString();
            if (request.isRequestedSessionIdFromURL()) {
                // This is not optimal, but as this is not very common, it
                // shouldn't matter
                redirectPath = redirectPath + ";jsessionid=" 
                    + request.getRequestedSessionId();
            }
            if (query != null) {
                // This is not optimal, but as this is not very common, it
                // shouldn't matter
                redirectPath = redirectPath + "?" + query;
            }
            response.sendRedirect(redirectPath);
            return false;
        }

        // Parse session Id
        parseSessionCookiesId(req, request);

        return true;
    }


    /**
     * Parse session id in URL.
     */
    protected void parseSessionId(org.apache.coyote.Request req, Request request) {

        CharChunk uriCC = req.decodedURI().getCharChunk();
        int semicolon = uriCC.indexOf(match, 0, match.length(), 0);

        if (semicolon > 0) {

            // Parse session ID, and extract it from the decoded request URI
            int start = uriCC.getStart();
            int end = uriCC.getEnd();

            int sessionIdStart = start + semicolon + match.length();
            int semicolon2 = uriCC.indexOf(';', sessionIdStart);
            if (semicolon2 >= 0) {
                request.setRequestedSessionId
                    (new String(uriCC.getBuffer(), sessionIdStart, 
                                semicolon2 - semicolon - match.length()));
            } else {
                request.setRequestedSessionId
                    (new String(uriCC.getBuffer(), sessionIdStart, 
                                end - sessionIdStart));
            }
            request.setRequestedSessionURL(true);

            // Extract session ID from request URI
            ByteChunk uriBC = req.requestURI().getByteChunk();
            start = uriBC.getStart();
            end = uriBC.getEnd();
            semicolon = uriBC.indexOf(match, 0, match.length(), 0);

            if (semicolon > 0) {
                sessionIdStart = start + semicolon;
                semicolon2 = uriCC.indexOf
                    (';', start + semicolon + match.length());
                uriBC.setEnd(start + semicolon);
                byte[] buf = uriBC.getBuffer();
                if (semicolon2 >= 0) {
                    for (int i = 0; i < end - start - semicolon2; i++) {
                        buf[start + semicolon + i] 
                            = buf[start + i + semicolon2];
                    }
                    uriBC.setBytes(buf, start, semicolon 
                                   + (end - start - semicolon2));
                }
            }

        } else {
            request.setRequestedSessionId(null);
            request.setRequestedSessionURL(false);
        }

    }


    /**
     * Parse session id in URL.
     */
    protected void parseSessionCookiesId(org.apache.coyote.Request req, Request request) {

        // Parse session id from cookies
        Cookies serverCookies = req.getCookies();
        int count = serverCookies.getCookieCount();
        if (count <= 0)
            return;

        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            if (scookie.getName().equals(Globals.SESSION_COOKIE_NAME)) {
                // Override anything requested in the URL
                if (!request.isRequestedSessionIdFromCookie()) {
                    // Accept only the first session id cookie
                    convertMB(scookie.getValue());
                    request.setRequestedSessionId
                        (scookie.getValue().toString());
                    request.setRequestedSessionCookie(true);
                    request.setRequestedSessionURL(false);
                    if (log.isDebugEnabled())
                        log.debug(" Requested cookie session id is " +
                            request.getRequestedSessionId());
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
        CharChunk cc = uri.getCharChunk();
        cc.allocate(bc.getLength(), -1);

        String enc = connector.getURIEncoding();
        if (enc != null) {
            B2CConverter conv = request.getURIConverter();
            try {
                if (conv == null) {
                    conv = new B2CConverter(enc);
                    request.setURIConverter(conv);
                } else {
                    conv.recycle();
                }
            } catch (IOException e) {
                // Ignore
                log.error("Invalid URI encoding; using HTTP default");
                connector.setURIEncoding(null);
            }
            if (conv != null) {
                try {
                    conv.convert(bc, cc);
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
        for (int i = 0; i < bc.getLength(); i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        uri.setChars(cbuf, 0, bc.getLength());

    }


    /**
     * Character conversion of the a US-ASCII MessageBytes.
     */
    protected void convertMB(MessageBytes mb) {

        // This is of course only meaningful for bytes
        if (mb.getType() != MessageBytes.T_BYTES)
            return;
        
        ByteChunk bc = mb.getByteChunk();
        CharChunk cc = mb.getCharChunk();
        cc.allocate(bc.getLength(), -1);

        // Default encoding: fast conversion
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < bc.getLength(); i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        mb.setChars(cbuf, 0, bc.getLength());

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
        byte[] b = uriBC.getBytes();
        int start = uriBC.getStart();
        int end = uriBC.getEnd();

        // URL * is acceptable
        if ((end - start == 1) && b[start] == (byte) '*')
          return true;

        int pos = 0;
        int index = 0;

        // Replace '\' with '/'
        // Check for null byte
        for (pos = start; pos < end; pos++) {
            if (b[pos] == (byte) '\\')
                b[pos] = (byte) '/';
            if (b[pos] == (byte) 0)
                return false;
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
            if (index < 0)
                break;
            copyBytes(b, start + index, start + index + 2, 
                      end - start - index - 2);
            end = end - 2;
            uriBC.setEnd(end);
        }

        index = 0;

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            index = uriBC.indexOf("/../", 0, 4, index);
            if (index < 0)
                break;
            // Prevent from going outside our context
            if (index == 0)
                return false;
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

        uriBC.setBytes(b, start, end);

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
