/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.catalina.connector;

import java.io.IOException;

import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
/**
 * TODO SERVLET 3 - async 
 * @author fhanik
 *
 */
public class AsyncListenerWrapper {

    private AsyncListener listener = null;
    private ServletRequest servletRequest = null;
    private ServletResponse servletResponse = null;
    
    public void fireOnComplete() throws IOException {
        // TODO SERVLET 3 - async 

    }


    public void fireOnTimeout() throws IOException {
        // TODO SERVLET 3 - async 

    }

    public ServletRequest getServletRequest() {
        return servletRequest;
    }

    public void setServletRequest(ServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }

    public ServletResponse getServletResponse() {
        return servletResponse;
    }

    public void setServletResponse(ServletResponse servletResponse) {
        this.servletResponse = servletResponse;
    }

    public AsyncListener getListener() {
        return listener;
    }

    public void setListener(AsyncListener listener) {
        this.listener = listener;
    }
    
    

}
