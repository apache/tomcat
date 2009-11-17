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

package org.apache.tomcat.util.http;

import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.UDecoder;

/**
 * Simple request representation, public fields - no I/O or actions,
 * just a struct.
 * 
 * Based on Coyote request.
 * 
 * @author Costin Manolache
 */
public class HttpRequest {
    
    // Same fields as in coyote - this is the primary info from request
    
    protected MessageBytes schemeMB;
    
    protected MessageBytes methodMB;
    protected MessageBytes unparsedURIMB;
    protected MessageBytes protoMB;
    protected MimeHeaders headers;
    
    // Reference to 'real' request object
    public Object nativeRequest;
    public Object wrapperRequest;

    protected MessageBytes remoteAddrMB;
    protected MessageBytes remoteHostMB;
    protected int remotePort;

    protected MessageBytes localNameMB;
    protected MessageBytes localAddrMB;
    protected int localPort;

    protected MessageBytes serverNameMB;
    protected int serverPort = -1;
    
    public HttpRequest() {
        schemeMB = 
            MessageBytes.newInstance();
        methodMB = MessageBytes.newInstance();
        unparsedURIMB = MessageBytes.newInstance();
        decodedUriMB = MessageBytes.newInstance();
        requestURI = MessageBytes.newInstance();
        protoMB = MessageBytes.newInstance();
        headers = new MimeHeaders();
        queryMB = MessageBytes.newInstance();
        serverNameMB = MessageBytes.newInstance();
        
        parameters = new Parameters();
        parameters.setQuery(queryMB);
        parameters.setURLDecoder(urlDecoder);
        //parameters.setHeaders(headers);
        cookies = new Cookies(headers);
        
        initRemote();
    }
    
    private void initRemote() {
        remoteAddrMB = MessageBytes.newInstance();
        localNameMB = MessageBytes.newInstance();
        remoteHostMB = MessageBytes.newInstance();
        localAddrMB = MessageBytes.newInstance();
    }
    
    
    public HttpRequest(MessageBytes scheme, MessageBytes method,
            MessageBytes unparsedURI, MessageBytes protocol,
            MimeHeaders mimeHeaders,
            MessageBytes requestURI, 
            MessageBytes decodedURI, 
            MessageBytes query, Parameters params, 
            MessageBytes serverName,
            Cookies cookies) {
        this.schemeMB = scheme;
        this.methodMB = method;
        this.unparsedURIMB = unparsedURI;
        this.protoMB = protocol;
        this.headers = mimeHeaders;
        
        this.requestURI = requestURI;
        this.decodedUriMB = decodedURI;
        this.queryMB = query;
        this.parameters = params;
        this.serverNameMB = serverName;
        this.cookies = cookies;
        initRemote();        
    }


    
    // ==== Derived fields, computed after request is received ===
    
    protected MessageBytes requestURI;
    protected MessageBytes queryMB;
    protected MessageBytes decodedUriMB;
    
    // -----------------
    protected Parameters parameters;
    
    protected MessageBytes contentTypeMB;

    protected String charEncoding;
    protected long contentLength = -1;

    protected Cookies cookies;

    // Avoid object creation:
    protected UDecoder urlDecoder = new UDecoder();

    
    public void recycle() {
        schemeMB.recycle();
        methodMB.setString("GET");
        unparsedURIMB.recycle();
        protoMB.setString("HTTP/1.1");
        headers.recycle();
        
        requestURI.recycle();
        queryMB.recycle();
        decodedUriMB.recycle();
        
        parameters.recycle();
        contentTypeMB = null;
        charEncoding = null;
        contentLength = -1;
        remoteAddrMB.recycle();
        remoteHostMB.recycle();
        cookies.recycle();
    }
    
    public Parameters getParameters() {
        return parameters;
    }
    
    // For compatibility with coyote
    public MessageBytes decodedURI() {
        return decodedUriMB;
    }

    public MessageBytes requestURI() {
        return requestURI;
    }

    public MessageBytes method() {
        return methodMB;
    }
    
    public String getHeader(String name) {
        return headers.getHeader(name);
    }

    public MimeHeaders getMimeHeaders() {
        return headers;
    }
    
    /**
     * Get the character encoding used for this request.
     */
    public String getCharacterEncoding() {

        if (charEncoding != null)
            return charEncoding;

        charEncoding = ContentType.getCharsetFromContentType(getContentType());
        return charEncoding;

    }


    public void setCharacterEncoding(String enc) {
        this.charEncoding = enc;
    }


    public void setContentLength(int len) {
        this.contentLength = len;
    }


    public int getContentLength() {
        long length = getContentLengthLong();

        if (length < Integer.MAX_VALUE) {
            return (int) length;
        }
        return -1;
    }

    public long getContentLengthLong() {
        if( contentLength > -1 ) return contentLength;

        MessageBytes clB = headers.getUniqueValue("content-length");
        contentLength = (clB == null || clB.isNull()) ? -1 : clB.getLong();

        return contentLength;
    }

    public String getContentType() {
        contentType();
        if ((contentTypeMB == null) || contentTypeMB.isNull()) 
            return null;
        return contentTypeMB.toString();
    }


    public void setContentType(String type) {
        contentTypeMB.setString(type);
    }


    public MessageBytes contentType() {
        if (contentTypeMB == null || contentTypeMB.isNull())
            contentTypeMB = headers.getValue("content-type");
        return contentTypeMB;
    }

    public int getServerPort() {
        return serverPort;
    }
    
    public void setServerPort(int serverPort ) {
        this.serverPort=serverPort;
    }

    public MessageBytes remoteAddr() {
        return remoteAddrMB;
    }

    public MessageBytes remoteHost() {
        return remoteHostMB;
    }

    public MessageBytes localName() {
        return localNameMB;
    }    

    public MessageBytes localAddr() {
        return localAddrMB;
    }
    
    public int getRemotePort(){
        return remotePort;
    }
        
    public void setRemotePort(int port){
        this.remotePort = port;
    }
    
    public int getLocalPort(){
        return localPort;
    }
        
    public void setLocalPort(int port){
        this.localPort = port;
    }
    
    public MessageBytes queryString() {
        return queryMB;
    }
    
    public MessageBytes protocol() {
        return protoMB;
    }
    
    public MessageBytes scheme() {
        return schemeMB;
    }
    
    public MessageBytes serverName() {
        return serverNameMB;
    }

    public MessageBytes unparsedURI() {
        return unparsedURIMB;
    }

    public Cookies getCookies() {
        return cookies;
    }
    
    public UDecoder getURLDecoder() {
        return urlDecoder;
    }

}

