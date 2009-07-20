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
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.coyote.ActionCode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
/**
 * 
 * @author fhanik
 *
 */
public class AsyncContextImpl implements AsyncContext {
    
    public static enum AsyncState {
        NOT_STARTED, STARTED, DISPATCHING, DISPATCHED, COMPLETING
    };
    
    protected static Log log = LogFactory.getLog(AsyncContextImpl.class);
    
    private ServletRequest servletRequest = null;
    private ServletResponse servletResponse = null;
    private List<AsyncListenerWrapper> listeners = new ArrayList<AsyncListenerWrapper>();
    private boolean hasOriginalRequestAndResponse = true;
    private volatile Runnable dispatch = null;
    private Context context = null;
    private AtomicReference<AsyncState> state = new AtomicReference<AsyncState>();
    
    private Request request;
    
    public AsyncContextImpl(Request request) {
        //TODO SERVLET3 - async
        this.request = request;
    }

    @Override
    public void complete() {
        // TODO SERVLET3 - async
        doInternalComplete(false);
    }

    @Override
    public void dispatch() {
        HttpServletRequest sr = (HttpServletRequest)getServletRequest();
        String path = sr.getRequestURI();
        dispatch(path);
    }

    @Override
    public void dispatch(String path) {
        dispatch(request.getServletContext(),path);
    }

    @Override
    public void dispatch(ServletContext context, String path) {
        // TODO SERVLET3 - async
        if (this.state.compareAndSet(AsyncState.STARTED, AsyncState.DISPATCHING)) {
            if (request.getAttribute(ASYNC_REQUEST_URI)==null) {
                request.setAttribute(ASYNC_REQUEST_URI, request.getRequestURI());
                request.setAttribute(ASYNC_CONTEXT_PATH, request.getContextPath());
                request.setAttribute(ASYNC_SERVLET_PATH, request.getServletPath());
                request.setAttribute(ASYNC_QUERY_STRING, request.getQueryString());
            }
            final RequestDispatcher requestDispatcher = context.getRequestDispatcher(path);
            final HttpServletRequest servletRequest = (HttpServletRequest)getRequest();
            final HttpServletResponse servletResponse = (HttpServletResponse)getResponse();
            Runnable run = new Runnable() {
                public void run() {
                    try {
                        //piggy back on the request dispatcher to ensure that filters etc get called.
                        //TODO SERVLET3 - async should this be include/forward or a new dispatch type
                        //javadoc suggests include with the type of DispatcherType.ASYNC
                        requestDispatcher.include(servletRequest, servletResponse);
                    }catch (Exception x) {
                        //log.error("Async.dispatch",x);
                        throw new RuntimeException(x);
                    }
                }
            };
            this.dispatch = run;
            request.coyoteRequest.action(ActionCode.ACTION_ASYNC_DISPATCH, null );

        } else {
            throw new IllegalStateException("Dispatch not allowed. Invalid state:"+state.get());
        }
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
        this.dispatch = run;
        request.coyoteRequest.action(ActionCode.ACTION_ASYNC_DISPATCH, null );
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
        servletRequest = null;
        servletResponse = null;
        listeners.clear();
        hasOriginalRequestAndResponse = true;
        state.set(AsyncState.NOT_STARTED);
        context = null;
    }

    public boolean isStarted() {
        return (state.get()!=AsyncState.NOT_STARTED);
    }

    public void setStarted(Context context) {
        if (state.compareAndSet(AsyncState.NOT_STARTED, AsyncState.STARTED)) {
            this.context = context;
        } else {
            throw new IllegalStateException("Already started.");
        }
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
        return (state.get()==AsyncState.NOT_STARTED);
    }

    public void setCompleted() {
        this.state.set(AsyncState.NOT_STARTED);
    }
    
    public void doInternalDispatch() throws ServletException, IOException {
        if (this.state.compareAndSet(AsyncState.DISPATCHING, AsyncState.DISPATCHED)) {
            if (this.dispatch!=null) {
                try {
                    dispatch.run();
                } catch (RuntimeException x) {
                    doInternalComplete(true);
                    if (x.getCause() instanceof ServletException) throw (ServletException)x.getCause();
                    if (x.getCause() instanceof IOException) throw (IOException)x.getCause();
                    else throw new ServletException(x);
                } finally {
                    dispatch = null;
                }
            }
        } else {
            throw new IllegalStateException("Dispatch illegal. Invalid state: "+state.get());
        }
    }
    
    public void doInternalComplete(boolean error) {
        if (isCompleted()) return;
        if (state.compareAndSet(AsyncState.STARTED, AsyncState.NOT_STARTED)) {
            //this is the same as
            //request.startAsync().complete();
            recycle();
        } else if (state.compareAndSet(AsyncState.DISPATCHED, AsyncState.NOT_STARTED)) {
            for (AsyncListenerWrapper wrapper : listeners) {
                try {
                    wrapper.fireOnComplete();
                }catch (IOException x) {
                    //how does this propagate, or should it?
                    //TODO SERVLET3 - async 
                    log.error("",x);
                }
            }
            try {
                if (!error) getResponse().flushBuffer();

            }catch (Exception x) {
                log.error("",x);
            }
            request.coyoteRequest.action(ActionCode.ACTION_ASYNC_COMPLETE,null);
            recycle();
        } else { 
            throw new IllegalStateException("Complete illegal. Invalid state:"+state.get());
        }
    }

}
