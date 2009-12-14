package org.apache.coyote.lite;



import java.io.IOException;
import java.util.Iterator;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.MultiMap;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.MultiMap.Entry;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.SocketConnector;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;

/**
 * Work in progress - use the refactored http as a coyote connector.
 * Just basic requests work right now - need to implement all the 
 * methods of coyote.
 * 
 * 
 * @author Costin Manolache
 */
public class LiteProtocolHandler implements ProtocolHandler {

    Adapter adapter;
    
    @Override
    public void destroy() throws Exception {
    }

    @Override
    public Adapter getAdapter() {
        return adapter;
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return null;
    }

    @Override
    public void init() throws Exception {
    }

    @Override
    public void pause() throws Exception {
    }

    @Override
    public void resume() throws Exception {
    }

    @Override
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
        
    }

    int port = 8999;
    
    public void setPort(int port) {
        this.port = port;
    }
    
    @Override
    public void setAttribute(String name, Object value) {
        System.err.println("setAttribute " + name + " " + value);
    }

    @Override
    public void start() throws Exception {
        HttpConnector c = new HttpConnector(new SocketConnector());
        c.setPort(port);
        
//        c.setDebug(true);
//        c.setDebugHttp(true);
        
        c.getDispatcher().setDefaultService(new HttpService() {
            @Override
            public void service(HttpRequest httpReq, HttpResponse httpRes)
                    throws IOException {
                coyoteService(httpReq, httpRes);
            }

        });
        c.start();
    }
    
    private void wrap(MessageBytes dest, CBuffer buffer) {
        dest.setChars(buffer.array(), buffer.position(), 
                buffer.length());
    }

    private void coyoteService(final HttpRequest httpReq, final HttpResponse httpRes) {
        RequestData rc = new RequestData();
        rc.init(httpReq, httpRes);
        
        try {
            adapter.service(rc.req, rc.res);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    /** 
     * Per request data.
     */
    public class RequestData implements ActionHook {
        private final class LiteOutputBuffer implements OutputBuffer {
            @Override
            public int doWrite(org.apache.tomcat.util.buf.ByteChunk chunk,
                    Response response) throws IOException {
                httpRes.getBody().append(chunk.getBuffer(), chunk.getStart(),
                        chunk.getLength());
                return chunk.getLength();
            }
        }

        OutputBuffer outputBuffer = new LiteOutputBuffer();
        // TODO: recycle, etc.
        Request req = new Request();

        Response res = new Response();
        HttpResponse httpRes;
        HttpRequest httpReq;
        InputBuffer inputBuffer = new InputBuffer() {
            @Override
            public int doRead(ByteChunk bchunk, Request request)
                    throws IOException {
                httpReq.getBody().waitData(httpReq.getHttpChannel().getIOTimeout());
                int rd = 
                    httpReq.getBody().read(bchunk.getBytes(), 
                        bchunk.getStart(), bchunk.getBytes().length);
                if (rd > 0) {
                    bchunk.setEnd(bchunk.getEnd() + rd);
                }
                return rd;
            }
        };
        
        public RequestData() {
            req.setInputBuffer(inputBuffer);
            res.setOutputBuffer(outputBuffer);
            req.setResponse(res);
            res.setRequest(req);
            res.setHook(this);
        }
        
        public void init(HttpRequest httpReq, HttpResponse httpRes) {
            this.httpRes = httpRes;
            this.httpReq = httpReq;
            // TODO: turn http request into a coyote request - copy all fields, 
            // add hooks where needed.
            
            wrap(req.decodedURI(), httpReq.decodedURI());
            wrap(req.method(), httpReq.method());
            wrap(req.protocol(), httpReq.protocol());
            wrap(req.requestURI(), httpReq.requestURI());
            wrap(req.queryString(), httpReq.queryString());
           
            req.setServerPort(httpReq.getServerPort());
            req.serverName().setString(req.localName().toString());
            
            MultiMap mimeHeaders = httpReq.getMimeHeaders();
            MimeHeaders coyoteHeaders = req.getMimeHeaders();
            for (int i = 0; i < mimeHeaders.size(); i++ ) {
                Entry entry = mimeHeaders.getEntry(i);
                MessageBytes val = 
                    coyoteHeaders.addValue(entry.getName().toString());
                val.setString(entry.getValue().toString());
            }
        }

        /**
         * Send an action to the connector.
         *
         * @param actionCode Type of the action
         * @param param Action parameter
         */
        public void action(ActionCode actionCode, Object param) {

            if (actionCode == ActionCode.ACTION_COMMIT) {
                if (res.isCommitted())
                    return;
                
                // TODO: copy headers, fields
                httpRes.setStatus(res.getStatus());
                httpRes.setMessage(res.getMessage());
                MultiMap mimeHeaders = httpRes.getMimeHeaders();
                MimeHeaders coyoteHeaders = res.getMimeHeaders();
                for (int i = 0; i < coyoteHeaders.size(); i++ ) {
                    MessageBytes name = coyoteHeaders.getName(i);
                    MessageBytes val = coyoteHeaders.getValue(i);
                    Entry entry = mimeHeaders.addEntry(name.toString());
                    entry.getValue().set(val.toString());
                }
                String contentType = res.getContentType();
                if (contentType != null) {
                    mimeHeaders.addEntry("Content-Type").getValue().set(contentType);
                }
                String contentLang = res.getContentType();
                if (contentLang != null) {
                    mimeHeaders.addEntry("Content-Language").getValue().set(contentLang);
                }
                long contentLength = res.getContentLengthLong();
                if (contentLength != -1) {
                    httpRes.setContentLength(contentLength);
                }
                String lang = res.getContentLanguage();
                if (lang != null) {
                    httpRes.setHeader("Content-Language", lang);
                }
                
                try {
                    httpReq.send();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (actionCode == ActionCode.ACTION_ACK) {
                // Done automatically by http connector
            } else if (actionCode == ActionCode.ACTION_CLIENT_FLUSH) {
                try {
                    httpReq.send();
                } catch (IOException e) {
                    httpReq.getHttpChannel().abort(e);
                    res.setErrorException(e);
                }

            } else if (actionCode == ActionCode.ACTION_CLOSE) {
                // Close

                // End the processing of the current request, and stop any further
                // transactions with the client

//                comet = false;
//                try {
//                    outputBuffer.endRequest();
//                } catch (IOException e) {
//                    // Set error flag
//                    error = true;
//                }

            } else if (actionCode == ActionCode.ACTION_RESET) {
                // Reset response
                // Note: This must be called before the response is committed
                httpRes.getBody().clear();

            } else if (actionCode == ActionCode.ACTION_CUSTOM) {

                // Do nothing

            } else if (actionCode == ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE) {
                req.remoteAddr().setString(httpReq.remoteAddr().toString());
            } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE) {
                req.localName().setString(httpReq.localName().toString());
            } else if (actionCode == ActionCode.ACTION_REQ_HOST_ATTRIBUTE) {
                req.remoteHost().setString(httpReq.remoteHost().toString());
            } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE) {
                req.localAddr().setString(httpReq.localAddr().toString());
            } else if (actionCode == ActionCode.ACTION_REQ_REMOTEPORT_ATTRIBUTE) {
                req.setRemotePort(httpReq.getRemotePort());
            } else if (actionCode == ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE) {
                req.setLocalPort(httpReq.getLocalPort());
            } else if (actionCode == ActionCode.ACTION_REQ_SSL_ATTRIBUTE ) {

//                if (ssl && (socket != 0)) {
//                    try {
//                        // Cipher suite
//                        Object sslO = SSLSocket.getInfoS(socket, SSL.SSL_INFO_CIPHER);
//                        if (sslO != null) {
//                            request.setAttribute(AprEndpoint.CIPHER_SUITE_KEY, sslO);
//                        }
//                        // Get client certificate and the certificate chain if present
//                        // certLength == -1 indicates an error
//                        int certLength = SSLSocket.getInfoI(socket, SSL.SSL_INFO_CLIENT_CERT_CHAIN);
//                        byte[] clientCert = SSLSocket.getInfoB(socket, SSL.SSL_INFO_CLIENT_CERT);
//                        X509Certificate[] certs = null;
//                        if (clientCert != null  && certLength > -1) {
//                            certs = new X509Certificate[certLength + 1];
//                            CertificateFactory cf = CertificateFactory.getInstance("X.509");
//                            certs[0] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(clientCert));
//                            for (int i = 0; i < certLength; i++) {
//                                byte[] data = SSLSocket.getInfoB(socket, SSL.SSL_INFO_CLIENT_CERT_CHAIN + i);
//                                certs[i+1] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data));
//                            }
//                        }
//                        if (certs != null) {
//                            request.setAttribute(AprEndpoint.CERTIFICATE_KEY, certs);
//                        }
//                        // User key size
//                        sslO = new Integer(SSLSocket.getInfoI(socket, SSL.SSL_INFO_CIPHER_USEKEYSIZE));
//                        request.setAttribute(AprEndpoint.KEY_SIZE_KEY, sslO);
//
//                        // SSL session ID
//                        sslO = SSLSocket.getInfoS(socket, SSL.SSL_INFO_SESSION_ID);
//                        if (sslO != null) {
//                            request.setAttribute(AprEndpoint.SESSION_ID_KEY, sslO);
//                        }
//                        //TODO provide a hook to enable the SSL session to be
//                        // invalidated. Set AprEndpoint.SESSION_MGR req attr
//                    } catch (Exception e) {
//                        log.warn(sm.getString("http11processor.socket.ssl"), e);
//                    }
//                }

            } else if (actionCode == ActionCode.ACTION_REQ_SSL_CERTIFICATE) {

//                if (ssl && (socket != 0)) {
//                    // Consume and buffer the request body, so that it does not
//                    // interfere with the client's handshake messages
//                    InputFilter[] inputFilters = inputBuffer.getFilters();
//                    ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER]).setLimit(maxSavePostSize);
//                    inputBuffer.addActiveFilter(inputFilters[Constants.BUFFERED_FILTER]);
//                    try {
//                        // Configure connection to require a certificate
//                        SSLSocket.setVerify(socket, SSL.SSL_CVERIFY_REQUIRE,
//                                endpoint.getSSLVerifyDepth());
//                        // Renegotiate certificates
//                        if (SSLSocket.renegotiate(socket) == 0) {
//                            // Don't look for certs unless we know renegotiation worked.
//                            // Get client certificate and the certificate chain if present
//                            // certLength == -1 indicates an error 
//                            int certLength = SSLSocket.getInfoI(socket,SSL.SSL_INFO_CLIENT_CERT_CHAIN);
//                            byte[] clientCert = SSLSocket.getInfoB(socket, SSL.SSL_INFO_CLIENT_CERT);
//                            X509Certificate[] certs = null;
//                            if (clientCert != null && certLength > -1) {
//                                certs = new X509Certificate[certLength + 1];
//                                CertificateFactory cf = CertificateFactory.getInstance("X.509");
//                                certs[0] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(clientCert));
//                                for (int i = 0; i < certLength; i++) {
//                                    byte[] data = SSLSocket.getInfoB(socket, SSL.SSL_INFO_CLIENT_CERT_CHAIN + i);
//                                    certs[i+1] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data));
//                                }
//                            }
//                            if (certs != null) {
//                                request.setAttribute(AprEndpoint.CERTIFICATE_KEY, certs);
//                            }
//                        }
//                    } catch (Exception e) {
//                        log.warn(sm.getString("http11processor.socket.ssl"), e);
//                    }
//                }

            } else if (actionCode == ActionCode.ACTION_REQ_SET_BODY_REPLAY) {
//                ByteChunk body = (ByteChunk) param;
//
//                InputFilter savedBody = new SavedRequestInputFilter(body);
//                savedBody.setRequest(request);
//
//                InternalAprInputBuffer internalBuffer = (InternalAprInputBuffer)
//                request.getInputBuffer();
//                internalBuffer.addActiveFilter(savedBody);

            } else if (actionCode == ActionCode.ACTION_AVAILABLE) {
                req.setAvailable(httpReq.getBody().available());
            } else if (actionCode == ActionCode.ACTION_COMET_BEGIN) {
//                comet = true;
            } else if (actionCode == ActionCode.ACTION_COMET_END) {
//                comet = false;
            } else if (actionCode == ActionCode.ACTION_COMET_CLOSE) {
                //no op
            } else if (actionCode == ActionCode.ACTION_COMET_SETTIMEOUT) {
                //no op
//            } else if (actionCode == ActionCode.ACTION_ASYNC_START) {
//                //TODO SERVLET3 - async
//            } else if (actionCode == ActionCode.ACTION_ASYNC_COMPLETE) {
//                //TODO SERVLET3 - async
//            } else if (actionCode == ActionCode.ACTION_ASYNC_SETTIMEOUT) {
//                //TODO SERVLET3 - async
            }


        }

    }
}
