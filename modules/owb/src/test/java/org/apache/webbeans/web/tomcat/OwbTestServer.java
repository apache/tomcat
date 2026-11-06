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
import java.util.function.Consumer;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

/**
 * Embedded Tomcat server configured for OWB integration tests.
 */
final class OwbTestServer implements AutoCloseable {

    enum ListenerMode {
        SERVER,
        CONTEXT
    }

    private final Tomcat tomcat;
    private final Context context;
    private final URI baseUri;

    private OwbTestServer(Tomcat tomcat, Context context, URI baseUri) {
        this.tomcat = tomcat;
        this.context = context;
        this.baseUri = baseUri;
    }

    static OwbTestServer start(Path webapp, ListenerMode mode, boolean startWithoutBeansXml,
            boolean addSecurityValve, String contextPath, Consumer<Tomcat> configurer) throws Exception {
        Path base = Files.createTempDirectory(webapp.getParent(), "tomcat-");

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        tomcat.getServer().setPort(-1);
        tomcat.setAddDefaultWebXmlToWebapp(false);
        // Enable naming so that Tomcat's DefaultInstanceManager invokes
        // @PostConstruct and @PreDestroy callbacks.
        tomcat.enableNaming();

        if (configurer != null) {
            configurer.accept(tomcat);
        }

        Context context = tomcat.addWebapp(contextPath, webapp.toString());
        if (mode == ListenerMode.SERVER) {
            OpenWebBeansListener listener = new OpenWebBeansListener();
            listener.setStartWithoutBeansXml(startWithoutBeansXml);
            listener.setAddSecurityValve(addSecurityValve);
            tomcat.getServer().addLifecycleListener(listener);
        } else {
            OpenWebBeansContextLifecycleListener listener = new OpenWebBeansContextLifecycleListener();
            listener.setStartWithoutBeansXml(startWithoutBeansXml);
            listener.setAddSecurityValve(addSecurityValve);
            context.addLifecycleListener(listener);
        }

        try {
            tomcat.start();
        } catch (Exception e) {
            closeQuietly(tomcat);
            throw e;
        }

        int port = tomcat.getConnector().getLocalPort();
        URI baseUri = URI.create("http://127.0.0.1:" + port + contextPath + "/");
        return new OwbTestServer(tomcat, context, baseUri);
    }

    static OwbTestServer start(Path webapp, ListenerMode mode, boolean startWithoutBeansXml,
            boolean addSecurityValve) throws Exception {
        return start(webapp, mode, startWithoutBeansXml, addSecurityValve, "/owb-test", null);
    }

    Context context() {
        return context;
    }

    URI baseUri() {
        return baseUri;
    }

    Tomcat tomcat() {
        return tomcat;
    }

    @Override
    public void close() throws Exception {
        try {
            tomcat.stop();
        } finally {
            tomcat.destroy();
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
