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
import java.util.ArrayList;
import java.util.List;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
/**
 * 
 * @author fhanik
 *
 */
public class AsyncContextImpl implements AsyncContext {
    protected static Log log = LogFactory.getLog(AsyncContextImpl.class);
    
    private boolean started = false;
    private ServletRequest servletRequest = null;
    private ServletResponse servletResponse = null;
    private List<AsyncListenerWrapper> listeners = new ArrayList<AsyncListenerWrapper>();
    private boolean hasOriginalRequestAndResponse = true;
    private boolean completed = false;
    
    private Request request;
    
    public AsyncContextImpl(Request request) {
        //TODO SERVLET3 - async
        this.request = request;
    }

    @Override
    public void complete() {
        // TODO SERVLET3 - async
        
        for (AsyncListenerWrapper wrapper : listeners) {
            try {
                wrapper.fireOnComplete();
            }catch (IOException x) {
                //how does this propagate, or should it?
               //TODO SERVLET3 - async 
                log.error("",x);
            }
        }
        this.completed = true;

    }

    @Override
    public void dispatch() {
        // TODO SERVLET3 - async

    }

    @Override
    public void dispatch(String path) {
        // TODO SERVLET3 - async

    }

    @Override
    public void dispatch(ServletContext context, String path) {
        // TODO SERVLET3 - async

    }

    @Override
    public ServletRequest getRequest() {
        return getServletRequest();
    }

    @Override
    public ServletResponse getResponse() {
        return getServletResponse();
    }

    @Override
    public void start(Runnable run) {
        // TODO SERVLET3 - async

    }
    
    public void addAsyncListener(AsyncListener listener) {
        AsyncListenerWrapper wrapper = new AsyncListenerWrapper();
        wrapper.setListener(listener);
        wrapper.setServletRequest(getServletRequest());
        wrapper.setServletResponse(getServletResponse());
        listeners.add(wrapper);
    }

    public void addAsyncListener(AsyncListener listener, ServletRequest servletRequest, ServletResponse servletResponse) {
        AsyncListenerWrapper wrapper = new AsyncListenerWrapper();
        wrapper.setListener(listener);
        wrapper.setServletRequest(servletRequest);
        wrapper.setServletResponse(servletResponse);
        listeners.add(wrapper);
    }
    
    
    protected void recycle() {
        started = false;
        servletRequest = null;
        servletResponse = null;
        listeners.clear();
        hasOriginalRequestAndResponse = true;
        completed = false;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
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

    @Override
    public boolean hasOriginalRequestAndResponse() {
        return hasOriginalRequestAndResponse;
    }

    public void setHasOriginalRequestAndResponse(boolean hasOriginalRequestAndResponse) {
        this.hasOriginalRequestAndResponse = hasOriginalRequestAndResponse;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
    
    

}
