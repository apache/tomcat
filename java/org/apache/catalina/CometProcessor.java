/*
 * Copyright 2006 The Apache Software Foundation.
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


package org.apache.catalina;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface CometProcessor {

    /**
     * Begin will be called by the main service method of the servlet at the beginning 
     * of the processing of the connection. It can be used to initialize any relevant 
     * fields using the request and response objects.
     * 
     * @param request The HTTP servlet request instance, which can be accessed
     *        asynchronously at any time until the end or error methods are called
     * @param response The HTTP servlet response instance, which can be accessed
     *        asynchronously at any time until the end or error methods are called
     * @throws IOException
     * @throws ServletException
     */
    public void begin(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException;

    /**
     * End may be called to end the processing of the request. Fields that have
     * been initialized in the begin method should be reset. After this method has
     * been called, the request and response objects, as well as all their dependent
     * objects will be recycled and used to process other requests.
     * 
     * @param request
     * @param response
     * @throws IOException
     * @throws ServletException
     */
    public void end(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException;

    /**
     * Error will be called by the container in the case where an IO exception
     * or a similar unrecoverable error occurs on the connection. Fields that have
     * been initialized in the begin method should be reset. After this method has
     * been called, the request and response objects, as well as all their dependent
     * objects will be recycled and used to process other requests.
     * 
     * @param request
     * @param response
     * @throws IOException
     * @throws ServletException
     */
    public void error(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException;
    
    /**
     * This indicates that input data is available, and that one read can be made
     * without blocking. The available and ready methods of the InputStream or
     * Reader may be used to determine if there is a risk of blocking: the servlet
     * should read while data is reported available, and can make one additional read
     * without blocking. When encountering a read error or an EOF, the servlet MUST
     * report it by either returning null or throwing an exception such as an 
     * IOException. 
     * 
     * @param request
     * @param response
     * @throws IOException An IOException may be thrown to indicate an IO error, 
     *         or that the EOF has been reached on the connection
     * @throws ServletException
     * @return false if the read attempt returned an EOF; alternately, it is also
     *         valid to throw an IOException
     */
    public boolean read(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException;

}
