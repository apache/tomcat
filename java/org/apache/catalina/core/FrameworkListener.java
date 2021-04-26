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

import java.util.concurrent.ConcurrentHashMap;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.Service;

/**
 * This listener must be declared in server.xml as a Server listener, possibly optional.
 * It will register a lifecycle listener on all contexts. This is an alternative to
 * adding a Listener in context.xml with more flexibility.
 */
public abstract class FrameworkListener implements LifecycleListener, ContainerListener {

    protected final ConcurrentHashMap<Context, LifecycleListener> contextListeners =
            new ConcurrentHashMap<>();

    /**
     * Create a lifecycle listener which will then be added to the specified context.
     * @param context the associated Context
     * @return the lifecycle listener
     */
    protected abstract LifecycleListener createLifecycleListener(Context context);

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        Lifecycle lifecycle = event.getLifecycle();
        if (Lifecycle.BEFORE_START_EVENT.equals(event.getType()) &&
                lifecycle instanceof Server) {
            Server server = (Server) lifecycle;
            registerListenersForServer(server);
        }
    }

    @Override
    public void containerEvent(ContainerEvent event) {
        String type = event.getType();
        if (Container.ADD_CHILD_EVENT.equals(type)) {
            processContainerAddChild((Container) event.getData());
        } else if (Container.REMOVE_CHILD_EVENT.equals(type)) {
            processContainerRemoveChild((Container) event.getData());
        }
    }

    protected void registerListenersForServer(Server server) {
        for (Service service : server.findServices()) {
            Engine engine = service.getContainer();
            if (engine != null) {
                engine.addContainerListener(this);
                registerListenersForEngine(engine);
            }
        }
    }

    protected void registerListenersForEngine(Engine engine) {
        for (Container hostContainer : engine.findChildren()) {
            Host host = (Host) hostContainer;
            host.addContainerListener(this);
            registerListenersForHost(host);
        }
    }

    protected void registerListenersForHost(Host host) {
        for (Container contextContainer : host.findChildren()) {
            Context context = (Context) contextContainer;
            registerContextListener(context);
        }
    }

    protected void registerContextListener(Context context) {
        LifecycleListener listener = createLifecycleListener(context);
        contextListeners.put(context, listener);
        context.addLifecycleListener(listener);
    }

    protected void processContainerAddChild(Container child) {
        if (child instanceof Context) {
            registerContextListener((Context) child);
        } else if (child instanceof Engine) {
            registerListenersForEngine((Engine) child);
        } else if (child instanceof Host) {
            registerListenersForHost((Host) child);
        }
    }

    protected void processContainerRemoveChild(Container child) {
        if (child instanceof Context) {
            LifecycleListener listener = contextListeners.remove(child);
            if (listener != null) {
                child.removeLifecycleListener(listener);
            }
        } else if (child instanceof Host || child instanceof Engine) {
            child.removeContainerListener(this);
        }
    }

}
