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

package org.apache.tomcat.lite;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * What we need to plugin a connector.
 * 
 * Currently we have lots of deps on coyote Request, but I plan to
 * change this and allow other HTTP implementations - like MINA, the
 * experimental async connector, etc. Most important will be 
 * different support for IO - i.e. a more non-blocking mode.
 * We'll probably keep MessageBytes as wrappers for request/res
 * properties. 
 * 
 * This interface has no dep on coyote.
 *  
 */
public interface Connector {

    public void setDaemon(boolean b);
    
    public void start() throws IOException;
    
    public void stop() throws Exception;
    
    /**
     * Called during close() - either on explicit output close, or 
     * after the request is completed. 
     * 
     * @throws IOException
     */
    public abstract void finishResponse(HttpServletResponse res) throws IOException;
   
    /**
     * Called before flushing the output during close.
     * Content-Length may be updated.
     * @param len 
     * 
     * @throws IOException
     */
    public abstract void beforeClose(HttpServletResponse res, int len) throws IOException;
    
    /** 
     * Called when the first flush() is called.
     * @throws IOException
     */
    public abstract void sendHeaders(HttpServletResponse res) throws IOException;
    
    /**
     * Send data to the client.
     * @throws IOException
     */
    public abstract void realFlush(HttpServletResponse res) throws IOException;

    /**
     * Write to the connector underlying buffer.
     * The chunk will be reused (currently).
     */
    public abstract void doWrite(HttpServletResponse res, ByteChunk outputChunk2) throws IOException;

    
    //public void finishResponse(HttpServletResponse res) throws IOException;
    
    public void acknowledge(HttpServletResponse res) throws IOException;

    public void reset(HttpServletResponse res);
    
    public void recycle(HttpServletRequest req, HttpServletResponse res);

    void initRequest(HttpServletRequest req, HttpServletResponse res);

    public void setTomcatLite(TomcatLite tomcatLite);

    public void setObjectManager(ObjectManager objectManager);
    
    public String getRemoteHost(HttpServletRequest req);
    
    public String getRemoteAddr(HttpServletRequest req);
    
    public int doRead(ServletRequestImpl coyoteRequest, ByteChunk bb) throws IOException;
}
