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

package org.apache.coyote.ajp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.AsyncContextCallback;
import org.apache.coyote.AsyncStateMachine;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.res.StringManager;

/**
 * Base class for AJP Processor implementations.
 */
public abstract class AbstractAjpProcessor extends AbstractProcessor {

    protected abstract Log getLog();

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    // ----------------------------------------------------- Instance Variables


    /**
     * Associated adapter.
     */
    protected Adapter adapter = null;


    /**
     * AJP packet size.
     */
    protected int packetSize;

    /**
     * Header message. Note that this header is merely the one used during the
     * processing of the first message of a "request", so it might not be a
     * request header. It will stay unchanged during the processing of the whole
     * request.
     */
    protected AjpMessage requestHeaderMessage = null;


    /**
     * Message used for response header composition.
     */
    protected AjpMessage responseHeaderMessage = null;


    /**
     * Body message.
     */
    protected AjpMessage bodyMessage = null;

    
    /**
     * Body message.
     */
    protected MessageBytes bodyBytes = MessageBytes.newInstance();


    /**
     * Error flag.
     */
    protected boolean error = false;


    /**
     * Host name (used to avoid useless B2C conversion on the host name).
     */
    protected char[] hostNameC = new char[0];


    /**
     * Temp message bytes used for processing.
     */
    protected MessageBytes tmpMB = MessageBytes.newInstance();


    /**
     * Byte chunk for certs.
     */
    protected MessageBytes certificates = MessageBytes.newInstance();


    /**
     * End of stream flag.
     */
    protected boolean endOfStream = false;


    /**
     * Body empty flag.
     */
    protected boolean empty = true;


    /**
     * First read.
     */
    protected boolean first = true;


    /**
     * Replay read.
     */
    protected boolean replay = false;


    /**
     * Finished response.
     */
    protected boolean finished = false;
    
    
    /**
     * Track changes in state for async requests.
     */
    protected AsyncStateMachine asyncStateMachine = new AsyncStateMachine(this);


    /**
     * Bytes written to client for the current request
     */
    protected long byteCount = 0;
    
    
    // ------------------------------------------------------------- Properties


    /**
     * The number of milliseconds Tomcat will wait for a subsequent request
     * before closing the connection. The default is the same as for
     * Apache HTTP Server (15 000 milliseconds).
     */
    protected int keepAliveTimeout = -1;
    public int getKeepAliveTimeout() { return keepAliveTimeout; }
    public void setKeepAliveTimeout(int timeout) { keepAliveTimeout = timeout; }


    /**
     * Use Tomcat authentication ?
     */
    protected boolean tomcatAuthentication = true;
    public boolean getTomcatAuthentication() { return tomcatAuthentication; }
    public void setTomcatAuthentication(boolean tomcatAuthentication) {
        this.tomcatAuthentication = tomcatAuthentication;
    }


    /**
     * Required secret.
     */
    protected String requiredSecret = null;
    public void setRequiredSecret(String requiredSecret) {
        this.requiredSecret = requiredSecret;
    }


    /**
     * When client certificate information is presented in a form other than
     * instances of {@link java.security.cert.X509Certificate} it needs to be
     * converted before it can be used and this property controls which JSSE
     * provider is used to perform the conversion. For example it is used with
     * the AJP connectors, the HTTP APR connector and with the
     * {@link org.apache.catalina.valves.SSLValve}. If not specified, the
     * default provider will be used. 
     */
    protected String clientCertProvider = null;
    public String getClientCertProvider() { return clientCertProvider; }
    public void setClientCertProvider(String s) { this.clientCertProvider = s; }

    // --------------------------------------------------------- Public Methods


   /**
    * Send an action to the connector.
    *
    * @param actionCode Type of the action
    * @param param Action parameter
    */
   @Override
   public final void action(ActionCode actionCode, Object param) {
       
       if (actionCode == ActionCode.COMMIT) {

           if (response.isCommitted())
               return;

           // Validate and write response headers
           try {
               prepareResponse();
           } catch (IOException e) {
               // Set error flag
               error = true;
           }

           try {
               flush(false);
           } catch (IOException e) {
               // Set error flag
               error = true;
           }

       } else if (actionCode == ActionCode.CLIENT_FLUSH) {

           if (!response.isCommitted()) {
               // Validate and write response headers
               try {
                   prepareResponse();
               } catch (IOException e) {
                   // Set error flag
                   error = true;
                   return;
               }
           }

           try {
               flush(true);
           } catch (IOException e) {
               // Set error flag
               error = true;
           }

       } else if (actionCode == ActionCode.DISABLE_SWALLOW_INPUT) {
           // TODO: Do not swallow request input but
           // make sure we are closing the connection
           error = true;

       } else if (actionCode == ActionCode.CLOSE) {
           // Close
           // End the processing of the current request, and stop any further
           // transactions with the client

           try {
               finish();
           } catch (IOException e) {
               // Set error flag
               error = true;
           }

       } else if (actionCode == ActionCode.REQ_SSL_ATTRIBUTE ) {

           if (!certificates.isNull()) {
               ByteChunk certData = certificates.getByteChunk();
               X509Certificate jsseCerts[] = null;
               ByteArrayInputStream bais =
                   new ByteArrayInputStream(certData.getBytes(),
                           certData.getStart(),
                           certData.getLength());
               // Fill the  elements.
               try {
                   CertificateFactory cf;
                   if (clientCertProvider == null) {
                       cf = CertificateFactory.getInstance("X.509");
                   } else {
                       cf = CertificateFactory.getInstance("X.509",
                               clientCertProvider);
                   }
                   while(bais.available() > 0) {
                       X509Certificate cert = (X509Certificate)
                           cf.generateCertificate(bais);
                       if(jsseCerts == null) {
                           jsseCerts = new X509Certificate[1];
                           jsseCerts[0] = cert;
                       } else {
                           X509Certificate [] temp = new X509Certificate[jsseCerts.length+1];
                           System.arraycopy(jsseCerts,0,temp,0,jsseCerts.length);
                           temp[jsseCerts.length] = cert;
                           jsseCerts = temp;
                       }
                   }
               } catch (java.security.cert.CertificateException e) {
                   getLog().error(sm.getString("ajpprocessor.certs.fail"), e);
                   return;
               } catch (NoSuchProviderException e) {
                   getLog().error(sm.getString("ajpprocessor.certs.fail"), e);
                   return;
               }
               request.setAttribute(SSLSupport.CERTIFICATE_KEY, jsseCerts);
           }

       } else if (actionCode == ActionCode.REQ_HOST_ATTRIBUTE) {

           // Get remote host name using a DNS resolution
           if (request.remoteHost().isNull()) {
               try {
                   request.remoteHost().setString(InetAddress.getByName
                           (request.remoteAddr().toString()).getHostName());
               } catch (IOException iex) {
                   // Ignore
               }
           }

       } else if (actionCode == ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE) {

           // Copy from local name for now, which should simply be an address
           request.localAddr().setString(request.localName().toString());

       } else if (actionCode == ActionCode.REQ_SET_BODY_REPLAY) {

           // Set the given bytes as the content
           ByteChunk bc = (ByteChunk) param;
           int length = bc.getLength();
           bodyBytes.setBytes(bc.getBytes(), bc.getStart(), length);
           request.setContentLength(length);
           first = false;
           empty = false;
           replay = true;

       } else if (actionCode == ActionCode.ASYNC_START) {
           asyncStateMachine.asyncStart((AsyncContextCallback) param);
       } else if (actionCode == ActionCode.ASYNC_DISPATCHED) {
           asyncStateMachine.asyncDispatched();
       } else if (actionCode == ActionCode.ASYNC_TIMEOUT) {
           AtomicBoolean result = (AtomicBoolean) param;
           result.set(asyncStateMachine.asyncTimeout());
       } else if (actionCode == ActionCode.ASYNC_RUN) {
           asyncStateMachine.asyncRun((Runnable) param);
       } else if (actionCode == ActionCode.ASYNC_ERROR) {
           asyncStateMachine.asyncError();
       } else if (actionCode == ActionCode.ASYNC_IS_STARTED) {
           ((AtomicBoolean) param).set(asyncStateMachine.isAsyncStarted());
       } else if (actionCode == ActionCode.ASYNC_IS_DISPATCHING) {
           ((AtomicBoolean) param).set(asyncStateMachine.isAsyncDispatching());
       } else if (actionCode == ActionCode.ASYNC_IS_ASYNC) {
           ((AtomicBoolean) param).set(asyncStateMachine.isAsync());
       } else if (actionCode == ActionCode.ASYNC_IS_TIMINGOUT) {
           ((AtomicBoolean) param).set(asyncStateMachine.isAsyncTimingOut());
       }  else {
           actionInternal(actionCode, param);
       }
   }
   
   // Methods called by action()
   protected abstract void actionInternal(ActionCode actionCode, Object param);
   protected abstract void flush(boolean tbd) throws IOException;
   protected abstract void finish() throws IOException;
   
   
   public void recycle() {
       asyncStateMachine.recycle();

       // Recycle Request object
       first = true;
       endOfStream = false;
       empty = true;
       replay = false;
       finished = false;
       request.recycle();
       response.recycle();
       certificates.recycle();
       byteCount = 0;
   }
   
   // ------------------------------------------------------ Connector Methods


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
   
   
   // ------------------------------------------------------ Protected Methods


   /**
    * After reading the request headers, we have to setup the request filters.
    */
   protected void prepareRequest() {

       // Translate the HTTP method code to a String.
       byte methodCode = requestHeaderMessage.getByte();
       if (methodCode != Constants.SC_M_JK_STORED) {
           String methodName = Constants.getMethodForCode(methodCode - 1);
           request.method().setString(methodName);
       }

       requestHeaderMessage.getBytes(request.protocol());
       requestHeaderMessage.getBytes(request.requestURI());

       requestHeaderMessage.getBytes(request.remoteAddr());
       requestHeaderMessage.getBytes(request.remoteHost());
       requestHeaderMessage.getBytes(request.localName());
       request.setLocalPort(requestHeaderMessage.getInt());

       boolean isSSL = requestHeaderMessage.getByte() != 0;
       if (isSSL) {
           request.scheme().setString("https");
       }

       // Decode headers
       MimeHeaders headers = request.getMimeHeaders();

       int hCount = requestHeaderMessage.getInt();
       for(int i = 0 ; i < hCount ; i++) {
           String hName = null;

           // Header names are encoded as either an integer code starting
           // with 0xA0, or as a normal string (in which case the first
           // two bytes are the length).
           int isc = requestHeaderMessage.peekInt();
           int hId = isc & 0xFF;

           MessageBytes vMB = null;
           isc &= 0xFF00;
           if(0xA000 == isc) {
               requestHeaderMessage.getInt(); // To advance the read position
               hName = Constants.getHeaderForCode(hId - 1);
               vMB = headers.addValue(hName);
           } else {
               // reset hId -- if the header currently being read
               // happens to be 7 or 8 bytes long, the code below
               // will think it's the content-type header or the
               // content-length header - SC_REQ_CONTENT_TYPE=7,
               // SC_REQ_CONTENT_LENGTH=8 - leading to unexpected
               // behaviour.  see bug 5861 for more information.
               hId = -1;
               requestHeaderMessage.getBytes(tmpMB);
               ByteChunk bc = tmpMB.getByteChunk();
               vMB = headers.addValue(bc.getBuffer(),
                       bc.getStart(), bc.getLength());
           }

           requestHeaderMessage.getBytes(vMB);

           if (hId == Constants.SC_REQ_CONTENT_LENGTH ||
                   (hId == -1 && tmpMB.equalsIgnoreCase("Content-Length"))) {
               // just read the content-length header, so set it
               long cl = vMB.getLong();
               if(cl < Integer.MAX_VALUE)
                   request.setContentLength( (int)cl );
           } else if (hId == Constants.SC_REQ_CONTENT_TYPE ||
                   (hId == -1 && tmpMB.equalsIgnoreCase("Content-Type"))) {
               // just read the content-type header, so set it
               ByteChunk bchunk = vMB.getByteChunk();
               request.contentType().setBytes(bchunk.getBytes(),
                       bchunk.getOffset(),
                       bchunk.getLength());
           }
       }

       // Decode extra attributes
       boolean secret = false;
       byte attributeCode;
       while ((attributeCode = requestHeaderMessage.getByte())
               != Constants.SC_A_ARE_DONE) {

           switch (attributeCode) {

           case Constants.SC_A_REQ_ATTRIBUTE :
               requestHeaderMessage.getBytes(tmpMB);
               String n = tmpMB.toString();
               requestHeaderMessage.getBytes(tmpMB);
               String v = tmpMB.toString();
               /*
                * AJP13 misses to forward the remotePort.
                * Allow the AJP connector to add this info via
                * a private request attribute.
                * We will accept the forwarded data as the remote port,
                * and remove it from the public list of request attributes.
                */
               if(n.equals(Constants.SC_A_REQ_REMOTE_PORT)) {
                   try {
                       request.setRemotePort(Integer.parseInt(v));
                   } catch (NumberFormatException nfe) {
                       // Ignore invalid value
                   }
               } else {
                   request.setAttribute(n, v );
               }
               break;

           case Constants.SC_A_CONTEXT :
               requestHeaderMessage.getBytes(tmpMB);
               // nothing
               break;

           case Constants.SC_A_SERVLET_PATH :
               requestHeaderMessage.getBytes(tmpMB);
               // nothing
               break;

           case Constants.SC_A_REMOTE_USER :
               if (tomcatAuthentication) {
                   // ignore server
                   requestHeaderMessage.getBytes(tmpMB);
               } else {
                   requestHeaderMessage.getBytes(request.getRemoteUser());
               }
               break;

           case Constants.SC_A_AUTH_TYPE :
               if (tomcatAuthentication) {
                   // ignore server
                   requestHeaderMessage.getBytes(tmpMB);
               } else {
                   requestHeaderMessage.getBytes(request.getAuthType());
               }
               break;

           case Constants.SC_A_QUERY_STRING :
               requestHeaderMessage.getBytes(request.queryString());
               break;

           case Constants.SC_A_JVM_ROUTE :
               requestHeaderMessage.getBytes(request.instanceId());
               break;

           case Constants.SC_A_SSL_CERT :
               request.scheme().setString("https");
               // SSL certificate extraction is lazy, moved to JkCoyoteHandler
               requestHeaderMessage.getBytes(certificates);
               break;

           case Constants.SC_A_SSL_CIPHER :
               request.scheme().setString("https");
               requestHeaderMessage.getBytes(tmpMB);
               request.setAttribute(SSLSupport.CIPHER_SUITE_KEY,
                                    tmpMB.toString());
               break;

           case Constants.SC_A_SSL_SESSION :
               request.scheme().setString("https");
               requestHeaderMessage.getBytes(tmpMB);
               request.setAttribute(SSLSupport.SESSION_ID_KEY,
                                    tmpMB.toString());
               break;

           case Constants.SC_A_SSL_KEY_SIZE :
               request.setAttribute(SSLSupport.KEY_SIZE_KEY,
                       Integer.valueOf(requestHeaderMessage.getInt()));
               break;

           case Constants.SC_A_STORED_METHOD:
               requestHeaderMessage.getBytes(request.method());
               break;

           case Constants.SC_A_SECRET:
               requestHeaderMessage.getBytes(tmpMB);
               if (requiredSecret != null) {
                   secret = true;
                   if (!tmpMB.equals(requiredSecret)) {
                       response.setStatus(403);
                       adapter.log(request, response, 0);
                       error = true;
                   }
               }
               break;

           default:
               // Ignore unknown attribute for backward compatibility
               break;

           }

       }

       // Check if secret was submitted if required
       if ((requiredSecret != null) && !secret) {
           response.setStatus(403);
           adapter.log(request, response, 0);
           error = true;
       }

       // Check for a full URI (including protocol://host:port/)
       ByteChunk uriBC = request.requestURI().getByteChunk();
       if (uriBC.startsWithIgnoreCase("http", 0)) {

           int pos = uriBC.indexOf("://", 0, 3, 4);
           int uriBCStart = uriBC.getStart();
           int slashPos = -1;
           if (pos != -1) {
               byte[] uriB = uriBC.getBytes();
               slashPos = uriBC.indexOf('/', pos + 3);
               if (slashPos == -1) {
                   slashPos = uriBC.getLength();
                   // Set URI as "/"
                   request.requestURI().setBytes
                       (uriB, uriBCStart + pos + 1, 1);
               } else {
                   request.requestURI().setBytes
                       (uriB, uriBCStart + slashPos,
                        uriBC.getLength() - slashPos);
               }
               MessageBytes hostMB = headers.setValue("host");
               hostMB.setBytes(uriB, uriBCStart + pos + 3,
                               slashPos - pos - 3);
           }

       }

       MessageBytes valueMB = request.getMimeHeaders().getValue("host");
       parseHost(valueMB);

   }
   
   
   /**
    * Parse host.
    */
   protected void parseHost(MessageBytes valueMB) {

       if (valueMB == null || valueMB.isNull()) {
           // HTTP/1.0
           request.setServerPort(request.getLocalPort());
           try {
               request.serverName().duplicate(request.localName());
           } catch (IOException e) {
               response.setStatus(400);
               adapter.log(request, response, 0);
               error = true;
           }
           return;
       }

       ByteChunk valueBC = valueMB.getByteChunk();
       byte[] valueB = valueBC.getBytes();
       int valueL = valueBC.getLength();
       int valueS = valueBC.getStart();
       int colonPos = -1;
       if (hostNameC.length < valueL) {
           hostNameC = new char[valueL];
       }

       boolean ipv6 = (valueB[valueS] == '[');
       boolean bracketClosed = false;
       for (int i = 0; i < valueL; i++) {
           char b = (char) valueB[i + valueS];
           hostNameC[i] = b;
           if (b == ']') {
               bracketClosed = true;
           } else if (b == ':') {
               if (!ipv6 || bracketClosed) {
                   colonPos = i;
                   break;
               }
           }
       }

       if (colonPos < 0) {
           if (request.scheme().equalsIgnoreCase("https")) {
               // 443 - Default HTTPS port
               request.setServerPort(443);
           } else {
               // 80 - Default HTTTP port
               request.setServerPort(80);
           }
           request.serverName().setChars(hostNameC, 0, valueL);
       } else {

           request.serverName().setChars(hostNameC, 0, colonPos);

           int port = 0;
           int mult = 1;
           for (int i = valueL - 1; i > colonPos; i--) {
               int charValue = HexUtils.getDec(valueB[i + valueS]);
               if (charValue == -1) {
                   // Invalid character
                   error = true;
                   // 400 - Bad request
                   response.setStatus(400);
                   adapter.log(request, response, 0);
                   break;
               }
               port = port + (charValue * mult);
               mult = 10 * mult;
           }
           request.setServerPort(port);
       }
   }
   
   
   /**
    * When committing the response, we have to validate the set of headers, as
    * well as setup the response filters.
    */
   protected void prepareResponse()
       throws IOException {

       response.setCommitted(true);

       responseHeaderMessage.reset();
       responseHeaderMessage.appendByte(Constants.JK_AJP13_SEND_HEADERS);

       // HTTP header contents
       responseHeaderMessage.appendInt(response.getStatus());
       String message = null;
       if (org.apache.coyote.Constants.USE_CUSTOM_STATUS_MSG_IN_HEADER &&
               HttpMessages.isSafeInHttpHeader(response.getMessage())) {
           message = response.getMessage();
       }
       if (message == null){
           message = HttpMessages.getMessage(response.getStatus());
       }
       if (message == null) {
           // mod_jk + httpd 2.x fails with a null status message - bug 45026
           message = Integer.toString(response.getStatus());
       }
       tmpMB.setString(message);
       responseHeaderMessage.appendBytes(tmpMB);

       // Special headers
       MimeHeaders headers = response.getMimeHeaders();
       String contentType = response.getContentType();
       if (contentType != null) {
           headers.setValue("Content-Type").setString(contentType);
       }
       String contentLanguage = response.getContentLanguage();
       if (contentLanguage != null) {
           headers.setValue("Content-Language").setString(contentLanguage);
       }
       long contentLength = response.getContentLengthLong();
       if (contentLength >= 0) {
           headers.setValue("Content-Length").setLong(contentLength);
       }

       // Other headers
       int numHeaders = headers.size();
       responseHeaderMessage.appendInt(numHeaders);
       for (int i = 0; i < numHeaders; i++) {
           MessageBytes hN = headers.getName(i);
           int hC = Constants.getResponseAjpIndex(hN.toString());
           if (hC > 0) {
               responseHeaderMessage.appendInt(hC);
           }
           else {
               responseHeaderMessage.appendBytes(hN);
           }
           MessageBytes hV=headers.getValue(i);
           responseHeaderMessage.appendBytes(hV);
       }

       // Write to buffer
       responseHeaderMessage.end();
       output(responseHeaderMessage.getBuffer(), 0,
               responseHeaderMessage.getLen());
   }
   
   // Methods called by prepareResponse()
   protected abstract void output(byte[] src, int offset, int length)
           throws IOException;
   
   
   protected boolean isAsync() {
       return asyncStateMachine.isAsync();
   }
   
   protected SocketState asyncPostProcess() {
       return asyncStateMachine.asyncPostProcess();
   }

   // ------------------------------------- InputStreamInputBuffer Inner Class


   /**
    * This class is an input buffer which will read its data from an input
    * stream.
    */
   protected class SocketInputBuffer
       implements InputBuffer {


       /**
        * Read bytes into the specified chunk.
        */
       @Override
       public int doRead(ByteChunk chunk, Request req )
           throws IOException {

           if (endOfStream) {
               return -1;
           }
           if (first && req.getContentLengthLong() > 0) {
               // Handle special first-body-chunk
               if (!receive()) {
                   return 0;
               }
           } else if (empty) {
               if (!refillReadBuffer()) {
                   return -1;
               }
           }
           ByteChunk bc = bodyBytes.getByteChunk();
           chunk.setBytes(bc.getBuffer(), bc.getStart(), bc.getLength());
           empty = true;
           return chunk.getLength();

       }

   }
   
   // Methods used by SocketInputBuffer
   protected abstract boolean receive() throws IOException;
   protected abstract boolean refillReadBuffer() throws IOException;
}
