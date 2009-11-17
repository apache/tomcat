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

/**
 * The data fields in a HTTP response and few helper and accessors.
 * No actions - just a struct.
 * 
 * Subset of coyte response.
 * 
 * @author Costin Manolache
 */
public class HttpResponse {
    // Primary fields 
    // in coyote message is String, status is int
    protected MessageBytes message = MessageBytes.newInstance();
    protected MessageBytes proto = MessageBytes.newInstance();
    protected MessageBytes statusBuffer = MessageBytes.newInstance();
    protected MimeHeaders headers = new MimeHeaders();
    public Object nativeResponse;
       
    boolean commited;
    
    public void recycle() {
        getMimeHeaders().recycle();
        message.recycle();
        statusBuffer.setInt(200);
        commited = false;
    }
    
    public boolean isCommitted() {
        return commited;
    }

    public void setCommitted(boolean b) {
        commited = b;
    }
    
    // Methods named for compat with coyote
    
    public void setStatus(int i) {
        statusBuffer.setInt(i);
    }
    
    public void setMessage(String s) {
        message.setString(s);
    }
    
    public String getMessage() {
        return message.toString();
    }
    
    public MessageBytes getMessageBuffer() {
        return message;
    }
    
    public MessageBytes protocol() {
        return proto;
    }
    
    public int getStatus() {
        return statusBuffer.getInt();
    }

    public MessageBytes getStatusBuffer() {
        return statusBuffer;
    }
    

    public void addHeader(String name, String value) {
        getMimeHeaders().addValue(name).setString(value);
    }

    public void setHeader(String name, String value) {
        getMimeHeaders().setValue(name).setString(value);
    }

    public void setMimeHeaders(MimeHeaders resHeaders) {
        this.headers = resHeaders;
    }

    public MimeHeaders getMimeHeaders() {
        return headers;
    }
    
    /**
     * Warning: This method always returns <code>false<code> for Content-Type
     * and Content-Length.
     */
    public boolean containsHeader(String name) {
        return headers.getHeader(name) != null;
    }

    public void setContentLength(long length) {
        MessageBytes clB = getMimeHeaders().getUniqueValue("content-length");
        if (clB == null) {
            clB = getMimeHeaders().addValue("content-length");
        }        
        clB.setLong(length);
    }
    
    public long getContentLength() {
        MessageBytes clB = getMimeHeaders().getUniqueValue("content-length");
        return (clB == null || clB.isNull()) ? -1 : clB.getLong();
    }
    
    public void setContentType(String contentType) {
        MessageBytes clB = getMimeHeaders().getUniqueValue("content-type");
        if (clB == null) {
            setHeader("content-type", contentType);
        } else {
            clB.setString(contentType);
        }
    }
    
}