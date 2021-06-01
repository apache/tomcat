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

import java.util.concurrent.Executor;

import org.apache.catalina.ContainerEvent;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

/**
 * <p>
 * A {@link LifecycleListener} that triggers the renewal of threads in Executor
 * pools when a {@link Context} is being stopped to avoid thread-local related
 * memory leaks.
 * </p>
 * <p>
 * Note : active threads will be renewed one by one when they come back to the
 * pool after executing their task, see
 * {@link org.apache.tomcat.util.threads.ThreadPoolExecutor}.afterExecute().
 * </p>
 *
 * This listener must be declared in server.xml to be active.
 *
 */
public class ThreadLocalLeakPreventionListener extends FrameworkListener {

    private static final Log log =
        LogFactory.getLog(ThreadLocalLeakPreventionListener.class);

    private volatile boolean serverStopping = false;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * Listens for {@link LifecycleEvent} for the start of the {@link Server} to
     * initialize itself and then for after_stop events of each {@link Context}.
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        try {
            super.lifecycleEvent(event);

            Lifecycle lifecycle = event.getLifecycle();
            if (Lifecycle.BEFORE_STOP_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Server) {
                // Server is shutting down, so thread pools will be shut down so
                // there is no need to clean the threads
                serverStopping = true;
            }

            if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Context) {
                stopIdleThreads((Context) lifecycle);
            }
        } catch (Exception e) {
            String msg =
                sm.getString(
                    "threadLocalLeakPreventionListener.lifecycleEvent.error",
                    event);
            log.error(msg, e);
        }
    }

    @Override
    public void containerEvent(ContainerEvent event) {
        try {
            super.containerEvent(event);
        } catch (Exception e) {
            String msg =
                sm.getString(
                    "threadLocalLeakPreventionListener.containerEvent.error",
                    event);
            log.error(msg, e);
        }

    }

    /**
     * Updates each ThreadPoolExecutor with the current time, which is the time
     * when a context is being stopped.
     *
     * @param context
     *            the context being stopped, used to discover all the Connectors
     *            of its parent Service.
     */
    private void stopIdleThreads(Context context) {
        if (serverStopping) {
            return;
        }

        if (!(context instanceof StandardContext) ||
            !((StandardContext) context).getRenewThreadsWhenStoppingContext()) {
            log.debug("Not renewing threads when the context is stopping. "
                + "It is not configured to do it.");
            return;
        }

        Engine engine = (Engine) context.getParent().getParent();
        Service service = engine.getService();
        Connector[] connectors = service.findConnectors();
        if (connectors != null) {
            for (Connector connector : connectors) {
                ProtocolHandler handler = connector.getProtocolHandler();
                Executor executor = null;
                if (handler != null) {
                    executor = handler.getExecutor();
                }

                if (executor instanceof ThreadPoolExecutor) {
                    ThreadPoolExecutor threadPoolExecutor =
                        (ThreadPoolExecutor) executor;
                    threadPoolExecutor.contextStopping();
                } else if (executor instanceof StandardThreadExecutor) {
                    StandardThreadExecutor stdThreadExecutor =
                        (StandardThreadExecutor) executor;
                    stdThreadExecutor.contextStopping();
                }

            }
        }
    }

    @Override
    protected LifecycleListener createLifecycleListener(Context context) {
        return this;
    }
}
