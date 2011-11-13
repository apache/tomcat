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
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.naming.NamingException;
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
import org.apache.coyote.AsyncContextCallback;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;
/**
 *
 * @author fhanik
 *
 */
public class AsyncContextImpl implements AsyncContext, AsyncContextCallback {

    private static final Log log = LogFactory.getLog(AsyncContextImpl.class);

    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    private ServletRequest servletRequest = null;
    private ServletResponse servletResponse = null;
    private List<AsyncListenerWrapper> listeners = new ArrayList<AsyncListenerWrapper>();
    private boolean hasOriginalRequestAndResponse = true;
    private volatile Runnable dispatch = null;
    private Context context = null;
    private long timeout = -1;
    private AsyncEvent event = null;
    private Request request;
    private volatile InstanceManager instanceManager;

    public AsyncContextImpl(Request request) {
        if (log.isDebugEnabled()) {
            logDebug("Constructor");
        }
        this.request = request;
    }

    @Override
    public void complete() {
        if (log.isDebugEnabled()) {
            logDebug("complete   ");
        }
        check();
        request.getCoyoteRequest().action(ActionCode.COMMIT, null);
        request.getCoyoteRequest().action(ActionCode.ASYNC_COMPLETE, null);
    }

    @Override
    public void fireOnComplete() {
        List<AsyncListenerWrapper> listenersCopy =
            new ArrayList<AsyncListenerWrapper>();
        listenersCopy.addAll(listeners);
        for (AsyncListenerWrapper listener : listenersCopy) {
            try {
                listener.fireOnComplete(event);
            } catch (IOException ioe) {
                log.warn("onComplete() failed for listener of type [" +
                        listener.getClass().getName() + "]", ioe);
            }
        }
    }

    public boolean timeout() throws IOException {
        AtomicBoolean result = new AtomicBoolean();
        request.getCoyoteRequest().action(ActionCode.ASYNC_TIMEOUT, result);

        if (result.get()) {
            boolean listenerInvoked = false;
            List<AsyncListenerWrapper> listenersCopy =
                new ArrayList<AsyncListenerWrapper>();
            listenersCopy.addAll(listeners);
            for (AsyncListenerWrapper listener : listenersCopy) {
                listener.fireOnTimeout(event);
                listenerInvoked = true;
            }
            if (listenerInvoked) {
                request.getCoyoteRequest().action(
                        ActionCode.ASYNC_IS_TIMINGOUT, result);
                return !result.get();
            } else {
                // No listeners, container calls complete
                complete();
            }
        }
        return true;
    }

    @Override
    public void dispatch() {
        check();
        HttpServletRequest sr = (HttpServletRequest)getRequest();
        String path = sr.getRequestURI();
        String cpath = sr.getContextPath();
        if (cpath.length()>1) path = path.substring(cpath.length());
        dispatch(path);
    }

    @Override
    public void dispatch(String path) {
        check();
        dispatch(request.getServletContext(),path);
    }

    @Override
    public void dispatch(ServletContext context, String path) {
        if (log.isDebugEnabled()) {
            logDebug("dispatch   ");
        }
        check();
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
            @Override
            public void run() {
                request.getCoyoteRequest().action(ActionCode.ASYNC_DISPATCHED, null);
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
        this.request.getCoyoteRequest().action(ActionCode.ASYNC_DISPATCH, null);
    }

    @Override
    public ServletRequest getRequest() {
        check();
        return servletRequest;
    }

    @Override
    public ServletResponse getResponse() {
        check();
        return servletResponse;
    }

    @Override
    public void start(final Runnable run) {
        if (log.isDebugEnabled()) {
            logDebug("start      ");
        }
        check();
        Runnable wrapper = new RunnableWrapper(run, context);
        this.request.getCoyoteRequest().action(ActionCode.ASYNC_RUN, wrapper);
    }

    @Override
    public void addListener(AsyncListener listener) {
        check();
        AsyncListenerWrapper wrapper = new AsyncListenerWrapper();
        wrapper.setListener(listener);
        listeners.add(wrapper);
    }

    @Override
    public void addListener(AsyncListener listener, ServletRequest servletRequest,
            ServletResponse servletResponse) {
        check();
        AsyncListenerWrapper wrapper = new AsyncListenerWrapper();
        wrapper.setListener(listener);
        listeners.add(wrapper);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends AsyncListener> T createListener(Class<T> clazz)
            throws ServletException {
        check();
        T listener = null;
        try {
             listener = (T) getInstanceManager().newInstance(clazz.getName(),
                     clazz.getClassLoader());
        } catch (InstantiationException e) {
            ServletException se = new ServletException(e);
            throw se;
        } catch (IllegalAccessException e) {
            ServletException se = new ServletException(e);
            throw se;
        } catch (InvocationTargetException e) {
            ExceptionUtils.handleThrowable(e.getCause());
            ServletException se = new ServletException(e);
            throw se;
        } catch (NamingException e) {
            ServletException se = new ServletException(e);
            throw se;
        } catch (ClassNotFoundException e) {
            ServletException se = new ServletException(e);
            throw se;
        }
        return listener;
    }

    public void recycle() {
        if (log.isDebugEnabled()) {
            logDebug("recycle    ");
        }
        context = null;
        dispatch = null;
        event = null;
        hasOriginalRequestAndResponse = true;
        instanceManager = null;
        listeners.clear();
        request = null;
        servletRequest = null;
        servletResponse = null;
        timeout = -1;
    }

    public boolean isStarted() {
        AtomicBoolean result = new AtomicBoolean(false);
        request.getCoyoteRequest().action(
                ActionCode.ASYNC_IS_STARTED, result);
        return result.get();
    }

    public void setStarted(Context context, ServletRequest request,
            ServletResponse response, boolean originalRequestResponse) {

        this.request.getCoyoteRequest().action(
                ActionCode.ASYNC_START, this);

        this.context = context;
        this.servletRequest = request;
        this.servletResponse = response;
        this.hasOriginalRequestAndResponse = originalRequestResponse;
        this.event = new AsyncEvent(this, request, response);

        List<AsyncListenerWrapper> listenersCopy =
            new ArrayList<AsyncListenerWrapper>();
        listenersCopy.addAll(listeners);
        for (AsyncListenerWrapper listener : listenersCopy) {
            try {
                listener.fireOnStartAsync(event);
            } catch (IOException ioe) {
                log.warn("onStartAsync() failed for listener of type [" +
                        listener.getClass().getName() + "]", ioe);
            }
        }
        listeners.clear();
    }

    @Override
    public boolean hasOriginalRequestAndResponse() {
        check();
        return hasOriginalRequestAndResponse;
    }

    protected void doInternalDispatch() throws ServletException, IOException {
        if (log.isDebugEnabled()) {
            logDebug("intDispatch");
        }
        try {
            dispatch.run();
            if (!request.isAsync()) {
                fireOnComplete();
            }
        } catch (RuntimeException x) {
            // doInternalComplete(true);
            if (x.getCause() instanceof ServletException) {
                throw (ServletException)x.getCause();
            }
            if (x.getCause() instanceof IOException) {
                throw (IOException)x.getCause();
            }
            throw new ServletException(x);
        }
    }


    @Override
    public long getTimeout() {
        check();
        return timeout;
    }


    @Override
    public void setTimeout(long timeout) {
        check();
        this.timeout = timeout;
        request.getCoyoteRequest().action(ActionCode.ASYNC_SETTIMEOUT,
                Long.valueOf(timeout));
    }


    public void setErrorState(Throwable t) {
        if (t!=null) request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
        request.getCoyoteRequest().action(ActionCode.ASYNC_ERROR, null);
        AsyncEvent errorEvent = new AsyncEvent(event.getAsyncContext(),
                event.getSuppliedRequest(), event.getSuppliedResponse(), t);
        List<AsyncListenerWrapper> listenersCopy =
            new ArrayList<AsyncListenerWrapper>();
        listenersCopy.addAll(listeners);
        for (AsyncListenerWrapper listener : listenersCopy) {
            try {
                listener.fireOnError(errorEvent);
            } catch (IOException ioe) {
                log.warn("onStartAsync() failed for listener of type [" +
                        listener.getClass().getName() + "]", ioe);
            }
        }
    }


    private void logDebug(String method) {
        String rHashCode;
        String crHashCode;
        String rpHashCode;
        String stage;
        StringBuilder uri = new StringBuilder();
        if (request == null) {
            rHashCode = "null";
            crHashCode = "null";
            rpHashCode = "null";
            stage = "-";
            uri.append("N/A");
        } else {
            rHashCode = Integer.toHexString(request.hashCode());
            org.apache.coyote.Request coyoteRequest = request.getCoyoteRequest();
            if (coyoteRequest == null) {
                crHashCode = "null";
                rpHashCode = "null";
                stage = "-";
            } else {
                crHashCode = Integer.toHexString(coyoteRequest.hashCode());
                RequestInfo rp = coyoteRequest.getRequestProcessor();
                if (rp == null) {
                    rpHashCode = "null";
                    stage = "-";
                } else {
                    rpHashCode = Integer.toHexString(rp.hashCode());
                    stage = Integer.toString(rp.getStage());
                }
            }
            uri.append(request.getRequestURI());
            if (request.getQueryString() != null) {
                uri.append('?');
                uri.append(request.getQueryString());
            }
        }
        String threadName = Thread.currentThread().getName();
        int len = threadName.length();
        if (len > 20) {
            threadName = threadName.substring(len - 20, len);
        }
        String msg = String.format(
                "Req: %1$8s  CReq: %2$8s  RP: %3$8s  Stage: %4$s  " +
                "Thread: %5$20s  State: %6$20s  Method: %7$11s  URI: %8$s",
                rHashCode, crHashCode, rpHashCode, stage,
                threadName, "N/A", method, uri);
        if (log.isTraceEnabled()) {
            log.trace(msg, new DebugException());
        } else {
            log.debug(msg);
        }
    }

    private InstanceManager getInstanceManager() {
        if (instanceManager == null) {
            if (context instanceof StandardContext) {
                instanceManager = ((StandardContext)context).getInstanceManager();
            } else {
                instanceManager = new DefaultInstanceManager(null,
                        new HashMap<String, Map<String, String>>(),
                        context,
                        getClass().getClassLoader());
            }
        }
        return instanceManager;
    }

    private void check() {
        if (request == null) {
            // AsyncContext has been recycled and should not be being used
            throw new IllegalStateException(sm.getString(
                    "asyncContextImpl.requestEnded"));
        }
    }
    private static class DebugException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static class RunnableWrapper implements Runnable {

        private Runnable wrapped = null;
        private Context context = null;

        public RunnableWrapper(Runnable wrapped, Context ctxt) {
            this.wrapped = wrapped;
            this.context = ctxt;
        }

        @Override
        public void run() {
            ClassLoader oldCL;
            if (Globals.IS_SECURITY_ENABLED) {
                PrivilegedAction<ClassLoader> pa = new PrivilegedGetTccl();
                oldCL = AccessController.doPrivileged(pa);
            } else {
                oldCL = Thread.currentThread().getContextClassLoader();
            }

            try {
                if (Globals.IS_SECURITY_ENABLED) {
                    PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                            context.getLoader().getClassLoader());
                    AccessController.doPrivileged(pa);
                } else {
                    Thread.currentThread().setContextClassLoader
                            (context.getLoader().getClassLoader());
                }                wrapped.run();
            } finally {
                if (Globals.IS_SECURITY_ENABLED) {
                    PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                            oldCL);
                    AccessController.doPrivileged(pa);
                } else {
                    Thread.currentThread().setContextClassLoader(oldCL);
                }
            }
        }

    }


    private static class PrivilegedSetTccl implements PrivilegedAction<Void> {

        private ClassLoader cl;

        PrivilegedSetTccl(ClassLoader cl) {
            this.cl = cl;
        }

        @Override
        public Void run() {
            Thread.currentThread().setContextClassLoader(cl);
            return null;
        }
    }

    private static class PrivilegedGetTccl
            implements PrivilegedAction<ClassLoader> {

        @Override
        public ClassLoader run() {
            return Thread.currentThread().getContextClassLoader();
        }
    }

}
