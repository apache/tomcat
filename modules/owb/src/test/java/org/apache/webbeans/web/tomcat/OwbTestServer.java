/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.webbeans.web.tomcat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;

/**
 * Embedded Tomcat server configured for OWB integration tests.
 */
final class OwbTestServer implements AutoCloseable {

    enum ListenerMode {
        SERVER,
        CONTEXT
    }

    record Deployment(String contextPath, Path webapp) {
    }

    private final Tomcat tomcat;
    private final Map<String, Context> contexts;
    private final Map<String, URI> baseUris;
    private final ListenerMode listenerMode;
    private final boolean startWithoutBeansXml;
    private final boolean addSecurityValve;
    private final boolean enableLeakChecks;
    private final int port;

    private OwbTestServer(Tomcat tomcat, Map<String, Context> contexts, Map<String, URI> baseUris,
            ListenerMode listenerMode, boolean startWithoutBeansXml, boolean addSecurityValve,
            boolean enableLeakChecks, int port) {
        this.tomcat = tomcat;
        this.contexts = contexts;
        this.baseUris = baseUris;
        this.listenerMode = listenerMode;
        this.startWithoutBeansXml = startWithoutBeansXml;
        this.addSecurityValve = addSecurityValve;
        this.enableLeakChecks = enableLeakChecks;
        this.port = port;
    }

    static OwbTestServer start(Path webapp, ListenerMode mode, boolean startWithoutBeansXml,
            boolean addSecurityValve) throws Exception {
        return start(List.of(new Deployment("/owb-test", webapp)), mode, startWithoutBeansXml, addSecurityValve,
                false, null);
    }

    static OwbTestServer start(Path webapp, ListenerMode mode, boolean startWithoutBeansXml,
            boolean addSecurityValve, String contextPath, Consumer<Tomcat> configurer) throws Exception {
        return start(List.of(new Deployment(contextPath, webapp)), mode, startWithoutBeansXml, addSecurityValve,
                false, configurer);
    }

    static OwbTestServer start(Path webapp, ListenerMode mode, boolean startWithoutBeansXml,
            boolean addSecurityValve, String contextPath, boolean useDefaultWebXml,
            Consumer<Tomcat> configurer) throws Exception {
        return start(List.of(new Deployment(contextPath, webapp)), mode, startWithoutBeansXml, addSecurityValve,
                useDefaultWebXml, configurer);
    }

    static OwbTestServer start(List<Deployment> deployments, ListenerMode mode, boolean startWithoutBeansXml,
            boolean addSecurityValve, boolean useDefaultWebXml, Consumer<Tomcat> configurer) throws Exception {
        return start(deployments, mode, startWithoutBeansXml, addSecurityValve, useDefaultWebXml, false,
                configurer);
    }

    static OwbTestServer start(List<Deployment> deployments, ListenerMode mode, boolean startWithoutBeansXml,
            boolean addSecurityValve, boolean useDefaultWebXml, boolean enableLeakChecks,
            Consumer<Tomcat> configurer) throws Exception {
        Path base = Files.createTempDirectory(deployments.get(0).webapp().getParent(), "tomcat-");

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        tomcat.getServer().setPort(-1);
        tomcat.setAddDefaultWebXmlToWebapp(useDefaultWebXml);
        // Enable naming so that Tomcat's DefaultInstanceManager invokes
        // @PostConstruct and @PreDestroy callbacks.
        tomcat.enableNaming();

        if (configurer != null) {
            configurer.accept(tomcat);
        }

        Map<String, Context> contexts = new LinkedHashMap<>();
        for (Deployment deployment : deployments) {
            Context context = addWebapp(tomcat, deployment.contextPath(), deployment.webapp(), mode,
                    startWithoutBeansXml, addSecurityValve, enableLeakChecks);
            contexts.put(deployment.contextPath(), context);
        }

        if (mode == ListenerMode.SERVER) {
            OpenWebBeansListener listener = new OpenWebBeansListener();
            listener.setStartWithoutBeansXml(startWithoutBeansXml);
            listener.setAddSecurityValve(addSecurityValve);
            tomcat.getServer().addLifecycleListener(listener);
        }

        try {
            tomcat.start();
        } catch (Exception e) {
            closeQuietly(tomcat);
            throw e;
        }

        int port = tomcat.getConnector().getLocalPort();
        Map<String, URI> baseUris = new LinkedHashMap<>();
        for (String contextPath : contexts.keySet()) {
            baseUris.put(contextPath, URI.create("http://127.0.0.1:" + port + contextPath + "/"));
        }
        return new OwbTestServer(tomcat, contexts, baseUris, mode, startWithoutBeansXml, addSecurityValve,
                enableLeakChecks, port);
    }

    Context deploy(String contextPath, Path webapp) throws Exception {
        Context context = addWebapp(tomcat, contextPath, webapp, listenerMode, startWithoutBeansXml,
                addSecurityValve, enableLeakChecks);
        if (!context.getState().isAvailable()) {
            context.start();
        }
        contexts.put(contextPath, context);
        baseUris.put(contextPath, URI.create("http://127.0.0.1:" + port + contextPath + "/"));
        return context;
    }

    Context context() {
        return contexts.values().iterator().next();
    }

    Context context(String contextPath) {
        return contexts.get(contextPath);
    }

    URI baseUri() {
        return baseUris.values().iterator().next();
    }

    URI baseUri(String contextPath) {
        return baseUris.get(contextPath);
    }

    Tomcat tomcat() {
        return tomcat;
    }

    void stopContext(String contextPath) throws Exception {
        context(contextPath).stop();
    }

    void startContext(String contextPath) throws Exception {
        context(contextPath).start();
    }

    @Override
    public void close() throws Exception {
        try {
            tomcat.stop();
        } finally {
            tomcat.destroy();
        }
    }

    private static Context addWebapp(Tomcat tomcat, String contextPath, Path webapp, ListenerMode mode,
            boolean startWithoutBeansXml, boolean addSecurityValve, boolean enableLeakChecks) {
        Context context;
        if (mode == ListenerMode.CONTEXT) {
            OpenWebBeansContextLifecycleListener listener = new OpenWebBeansContextLifecycleListener();
            listener.setStartWithoutBeansXml(startWithoutBeansXml);
            listener.setAddSecurityValve(addSecurityValve);
            // Register the listener before the context starts so dynamic
            // deployments also receive the CONFIGURE_START event.
            context = tomcat.addWebapp(tomcat.getHost(), contextPath, webapp.toString(),
                    new OwbContextConfig(listener));
        } else {
            context = tomcat.addWebapp(contextPath, webapp.toString());
        }
        if (!enableLeakChecks && context instanceof StandardContext standardContext) {
            // These checks require --add-opens for java.lang and java.rmi.
            // They are only enabled by the dedicated leak test.
            standardContext.setClearReferencesThreadLocals(false);
            standardContext.setClearReferencesRmiTargets(false);
        }
        return context;
    }

    private static final class OwbContextConfig extends ContextConfig {

        private final OpenWebBeansContextLifecycleListener listener;

        private OwbContextConfig(OpenWebBeansContextLifecycleListener listener) {
            this.listener = listener;
        }

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            super.lifecycleEvent(event);
            if (Lifecycle.CONFIGURE_START_EVENT.equals(event.getType())) {
                listener.lifecycleEvent(event);
            }
        }
    }

    private static void closeQuietly(Tomcat tomcat) {
        try {
            tomcat.stop();
        } catch (Exception ignored) {
            // Ignore cleanup failures while reporting the startup failure.
        }
        try {
            tomcat.destroy();
        } catch (Exception ignored) {
            // Ignore cleanup failures while reporting the startup failure.
        }
    }
}
