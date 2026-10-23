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
package org.apache.catalina.startup;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Logs the configured connectors, hosts, and user valves when the server has started.
 * <p>
 * This listener must only be nested within {@link Server} elements.
 */
public class ConfigurationLoggerListener implements LifecycleListener {

    /**
     * Constructs a new ConfigurationLoggerListener.
     */
    public ConfigurationLoggerListener() {
    }

    private static final Log log = LogFactory.getLog(ConfigurationLoggerListener.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (Lifecycle.AFTER_START_EVENT.equals(event.getType())) {
            if (!(event.getLifecycle() instanceof Server server)) {
                log.warn(sm.getString("listener.notServer", event.getLifecycle().getClass().getSimpleName()));
            } else {
                log.info(sm.getString("configurationLoggerListener.server", describe(server)));
            }
        }
    }


    /**
     * Describe the connectors, hosts, and user-configured valves of a server. The basic valve of each pipeline and
     * {@link ErrorReportValve}, which every host installs itself, are omitted. Context pipelines are not included.
     *
     * @param server the started server
     *
     * @return the configuration summary, with sections separated by {@code "; "}
     */
    static String describe(Server server) {
        StringBuilder body = new StringBuilder();
        Service[] services = server.findServices();
        for (int i = 0; i < services.length; i++) {
            if (i > 0) {
                body.append("; ");
            }
            appendService(body, services[i]);
        }
        return body.toString();
    }


    private static void appendService(StringBuilder body, Service service) {
        Engine engine = service.getContainer();
        body.append("service [").append(service.getName()).append(']');
        if (engine != null) {
            body.append(" engine [").append(engine.getName()).append(']');
            String engineValves = valveNames(engine);
            if (!engineValves.isEmpty()) {
                body.append("; engine valves [").append(engineValves).append(']');
            }
        }
        for (Connector connector : service.findConnectors()) {
            body.append("; connector [").append(connector.getProtocol()).append("] port [")
                    .append(connector.getPortWithOffset()).append("] secure [").append(connector.getSecure())
                    .append(']');
        }
        if (engine != null) {
            for (Container child : engine.findChildren()) {
                if (child instanceof Host host) {
                    body.append("; host [").append(host.getName()).append(']');
                    String hostValves = valveNames(host);
                    if (!hostValves.isEmpty()) {
                        body.append(" valves [").append(hostValves).append(']');
                    }
                }
            }
        }
    }


    /**
     * @return the simple names of valves other than the pipeline's basic valve and {@link ErrorReportValve}, separated
     *             by {@code ", "}, or an empty string when none remain
     */
    private static String valveNames(Container container) {
        Pipeline pipeline = container.getPipeline();
        if (pipeline == null) {
            return "";
        }
        Valve basic = pipeline.getBasic();
        StringBuilder names = new StringBuilder();
        for (Valve valve : pipeline.getValves()) {
            if (valve == basic || valve instanceof ErrorReportValve) {
                continue;
            }
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(valve.getClass().getSimpleName());
        }
        return names.toString();
    }
}
