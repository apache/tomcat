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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;

public class TestFrameworkListener {

    @Test
    public void testRegistrationIsIdempotent() throws Exception {
        StandardServer server = new StandardServer();
        StandardService service = new StandardService();
        service.setName("service");
        StandardEngine engine = new StandardEngine();
        engine.setName("engine");
        service.setContainer(engine);
        server.addService(service);

        StandardHost host = new StandardHost();
        host.setName("localhost");
        engine.addChild(host);

        StandardContext context = new StandardContext();
        context.setPath("/ctx");
        host.addChild(context);

        TesterListener listener = new TesterListener();

        LifecycleEvent beforeStart = new LifecycleEvent(server, Lifecycle.BEFORE_START_EVENT, null);

        // Simulate repeated in-place Server starts
        listener.lifecycleEvent(beforeStart);
        listener.lifecycleEvent(beforeStart);
        listener.lifecycleEvent(beforeStart);

        Assert.assertEquals(1, countListener(engine.findContainerListeners(), listener));
        Assert.assertEquals(1, countListener(host.findContainerListeners(), listener));
        Assert.assertEquals(1, listener.getContextListenerCount());
        Assert.assertEquals(1, listener.getCreatedListeners().size());
        // Exactly one listener — the one created for this context — must be attached
        LifecycleListener created = listener.getCreatedListeners().get(0);
        long attached = Stream.of(context.findLifecycleListeners()).filter(l -> l == created).count();
        Assert.assertEquals(1, attached);
    }


    private static long countListener(ContainerListener[] listeners, ContainerListener expected) {
        return Stream.of(listeners).filter(l -> l == expected).count();
    }


    private static class TesterListener extends FrameworkListener {

        private final List<LifecycleListener> createdListeners = new ArrayList<>();

        @Override
        protected LifecycleListener createLifecycleListener(Context context) {
            // Return a new instance on each call, as OpenWebBeansListener does
            LifecycleListener listener = event -> {
                // No-Op
            };
            createdListeners.add(listener);
            return listener;
        }

        int getContextListenerCount() {
            return contextListeners.size();
        }

        List<LifecycleListener> getCreatedListeners() {
            return createdListeners;
        }
    }
}
