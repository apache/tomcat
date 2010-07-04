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
package org.apache.catalina.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.connector.Request;
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
        NOT_STARTED, STARTED, DISPATCHING, DISPATCHED, COMPLETING, TIMING_OUT, ERROR_DISPATCHING
    }
    
    private static final Log log = LogFactory.getLog(AsyncContextImpl.class);
    
    private ServletRequest servletRequest = null;
    private ServletResponse servletResponse = null;
    private List<AsyncListenerWrapper> listeners = new ArrayList<AsyncListenerWrapper>();
    private boolean hasOriginalRequestAndResponse = true;
    private volatile Runnable dispatch = null;
    private Context context = null;
    private AtomicReference<AsyncState> state = new AtomicReference<AsyncState>(AsyncState.NOT_STARTED);
    private long timeout = -1;
    private AsyncEvent event = null;
    
    private Request request;
    
    public AsyncContextImpl(Request request) {
        if (log.isDebugEnabled()) {
            log.debug("AsyncContext created["+request.getRequestURI()+"?"+request.getQueryString()+"]", new DebugException());
        }
        //TODO SERVLET3 - async
        this.request = request;
    }

    @Override
    public void complete() {
        if (log.isDebugEnabled()) {
            log.debug("AsyncContext Complete Called["+state.get()+"; "+request.getRequestURI()+"?"+request.getQueryString()+"]", new DebugException());
        }
        if (state.get()==AsyncState.COMPLETING) {
            //do nothing
        } else if (state.compareAndSet(AsyncState.DISPATCHED, AsyncState.COMPLETING) ||
                   state.compareAndSet(AsyncState.STARTED, AsyncState.COMPLETING)) {
            // TODO SERVLET3 - async
            AtomicBoolean dispatched = new AtomicBoolean(false);
            request.getCoyoteRequest().action(ActionCode.ACTION_ASYNC_COMPLETE,dispatched);
            if (!dispatched.get()) doInternalComplete(false);
        } else {
            throw new IllegalStateException("Complete not allowed. Invalid state:"+state.get());
        }
       
    }

    @Override
    public void dispatch() {
        HttpServletRequest sr = (HttpServletRequest)getServletRequest();
        String path = sr.getRequestURI();
        String cpath = sr.getContextPath();
        if (cpath.length()>1) path = path.substring(cpath.length());
        dispatch(path);
    }

    @Override
    public void dispatch(String path) {
        dispatch(request.getServletContext(),path);
    }

    @Override
    public void dispatch(ServletContext context, String path) {
        if (log.isDebugEnabled()) {
            log.debug("AsyncContext Dispatch Called["+state.get()+"; "+path+"; "+request.getRequestURI()+"?"+request.getQueryString()+"]", new DebugException());
        }

        // TODO SERVLET3 - async
        if (state.compareAndSet(AsyncState.STARTED, AsyncState.DISPATCHING) ||
            state.compareAndSet(AsyncState.DISPATCHED, AsyncState.DISPATCHING)) {

            if (request.getAttribute(ASYNC_REQUEST_URI)==null) {
                request.setAttribute(ASYNC_REQUEST_URI, request.getRequestURI()+"?"+request.getQueryString());
                request.setAttribute(ASYNC_CONTEXT_PATH, request.getContextPath());
                request.setAttribute(ASYNC_SERVLET_PATH, request.getServletPath());
                request.setAttribute(ASYNC_QUERY_STRING, request.getQueryString());
            }
            final RequestDispatcher requestDispatcher = context.getRequestDispatcher(path);
            final HttpServletRequest servletRequest = (HttpServletRequest)getRequest();
            final HttpServletResponse servletResponse = (HttpServletResponse)getResponse();
            Runnable run = new Runnable() {
                public void run() {
                    DispatcherType type = (DispatcherType)request.getAttribute(Globals.DISPATCHER_TYPE_ATTR);
                    try {
                        //piggy back on the request dispatcher to ensure that filters etc get called.
                        //TODO SERVLET3 - async should this be include/forward or a new dispatch type
                        //javadoc suggests include with the type of DispatcherType.ASYNC
                        request.setAttribute(Globals.DISPATCHER_TYPE_ATTR, DispatcherType.ASYNC);
                        requestDispatcher.include(servletRequest, servletResponse);
                    }catch (Exception x) {
                        //log.error("Async.dispatch",x);
                        throw new RuntimeException(x);
                    }finally {
                        request.setAttribute(Globals.DISPATCHER_TYPE_ATTR, type);
                    }
                }
            };
            this.dispatch = run;
            AtomicBoolean dispatched = new AtomicBoolean(false);
            request.getCoyoteRequest().action(ActionCode.ACTION_ASYNC_DISPATCH, dispatched );
            if (!dispatched.get()) {
                try {
                    doInternalDispatch();
                }catch (ServletException sx) {
                    throw new RuntimeException(sx);
                }catch (IOException ix) {
                    throw new RuntimeException(ix);
                }
            }
            if (state.get().equals(AsyncState.DISPATCHED)) {
                complete();
            }
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
    public void start(final Runnable run) {
        if (log.isDebugEnabled()) {
            log.debug("AsyncContext Start Called["+state.get()+"; "+request.getRequestURI()+"?"+request.getQueryString()+"]", new DebugException());
        }

        if (state.compareAndSet(AsyncState.STARTED, AsyncState.DISPATCHING) ||
            state.compareAndSet(AsyncState.DISPATCHED, AsyncState.DISPATCHING)) {
            // TODO SERVLET3 - async
            final ServletContext sctx = getServletRequest().getServletContext();
            Runnable r = new Runnable() {
                public void run() {
                    //TODO SERVLET3 - async - set context class loader when running the task.
                    try {
                        
                        run.run();
                    }catch (Exception x) {
                        log.error("Unable to run async task.",x);
                    }
                }
            };
            this.dispatch = r;
            AtomicBoolean dispatched = new AtomicBoolean(false);
            request.getCoyoteRequest().action(ActionCode.ACTION_ASYNC_DISPATCH, dispatched );
            if (!dispatched.get()) {
                try {
                    doInternalDispatch();
                }catch (ServletException sx) {
                    throw new RuntimeException(sx);
                }catch (IOException ix) {
                    throw new RuntimeException(ix);
                }
            }
        } else {
            throw new IllegalStateException("Dispatch not allowed. Invalid state:"+state.get());
        }
    }
    
    @Override
    public void addListener(AsyncListener listener) {
        AsyncListenerWrapper wrapper = new AsyncListenerWrapper();
        wrapper.setListener(listener);
        listeners.add(wrapper);
    }

    @Override
    public void addListener(AsyncListener listener, ServletRequest servletRequest,
            ServletResponse servletResponse) {
        AsyncListenerWrapper wrapper = new AsyncListenerWrapper();
        wrapper.setListener(listener);
        listeners.add(wrapper);
    }

    @Override
    public <T extends AsyncListener> T createListener(Class<T> clazz)
            throws ServletException {
        T listener = null;
        try {
             listener = clazz.newInstance();
        } catch (InstantiationException e) {
            ServletException se = new ServletException(e);
            throw se;
        } catch (IllegalAccessException e) {
            ServletException se = new ServletException(e);
            throw se;
        }
        return listener;
    }
    
    public void recycle() {
        servletRequest = null;
        servletResponse = null;
        listeners.clear();
        hasOriginalRequestAndResponse = true;
        state.set(AsyncState.NOT_STARTED);
        context = null;
        timeout = -1;
        event = null;
    }

    public boolean isStarted() {
        return (state.get() == AsyncState.STARTED || state.get() == AsyncState.DISPATCHING);
    }

    public void setStarted(Context context) {
        if (state.compareAndSet(AsyncState.NOT_STARTED, AsyncState.STARTED) ||
                state.compareAndSet(AsyncState.DISPATCHED, AsyncState.STARTED)) {
            this.context = context;
        } else {
            throw new IllegalStateException("Start illegal. Invalid state: "+state.get());
        }
    }

    public ServletRequest getServletRequest() {
        return servletRequest;
    }

    public ServletResponse getServletResponse() {
        return servletResponse;
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
        if (this.state.compareAndSet(AsyncState.TIMING_OUT, AsyncState.COMPLETING)) {
            log.debug("TIMING OUT!");
            boolean listenerInvoked = false;
            for (AsyncListenerWrapper listener : listeners) {
                listener.fireOnTimeout(event);
                listenerInvoked = true;
            }
            if (!listenerInvoked) {
                ((HttpServletResponse)servletResponse).setStatus(500);
            }
            doInternalComplete(true);
        } else if (this.state.compareAndSet(AsyncState.ERROR_DISPATCHING, AsyncState.COMPLETING)) {
            log.debug("ON ERROR!");
            boolean listenerInvoked = false;
            for (AsyncListenerWrapper listener : listeners) {
                try {
                    listener.fireOnError(event);
                }catch (IllegalStateException x) {
                    log.debug("Listener invoked invalid state.",x);
                }catch (Exception x) {
                    log.debug("Exception during onError.",x);
                }
                listenerInvoked = true;
            }
            if (!listenerInvoked) {
                ((HttpServletResponse)servletResponse).setStatus(500);
            }
            doInternalComplete(true);
        
        } else if (this.state.compareAndSet(AsyncState.DISPATCHING, AsyncState.DISPATCHED)) {
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
        } else if (this.state.get()==AsyncState.COMPLETING) {
            doInternalComplete(false);
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
        } else if (state.compareAndSet(AsyncState.COMPLETING, AsyncState.NOT_STARTED)) {
            for (AsyncListenerWrapper wrapper : listeners) {
                try {
                    wrapper.fireOnComplete(event);
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
            recycle();
            
        } else { 
            throw new IllegalStateException("Complete illegal. Invalid state:"+state.get());
        }
    }
    
    public AsyncState getState() {
        return state.get();
    }
    
    protected void setState(AsyncState st) {
        state.set(st);
    }
    
    public long getTimeout() {
        return timeout;
    }
    
    public void setTimeout(long timeout) {
        this.timeout = timeout;
        request.getCoyoteRequest().action(ActionCode.ACTION_ASYNC_SETTIMEOUT,new Long(timeout));
    }
    
    public void setTimeoutState() {
        state.set(AsyncState.TIMING_OUT);
    }
    
    public void setErrorState(Throwable t) {
        if (t!=null) request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
        state.set(AsyncState.ERROR_DISPATCHING);
    }
    
    public void init(ServletRequest request, ServletResponse response) {
        this.servletRequest = request;
        this.servletResponse = response;
        event = new AsyncEvent(this, request, response); 
    }
    public static class DebugException extends Exception {}
}
