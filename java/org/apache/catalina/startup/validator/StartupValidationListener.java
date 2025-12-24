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
package org.apache.catalina.startup.validator;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * A lifecycle listener that runs configuration validators during server startup
 * as a pre-flight check. This allows catching configuration errors and aborting
 * startup for issues that may not normally stop the server.
 *
 * <p>The listener runs at the BEFORE_INIT_EVENT, which occurs after the server
 * configuration has been parsed but before the server attempts to bind to any ports.
 * This ensures that port availability checks and other validations can run without
 * interference from the server itself.
 *
 * <p>Configuration options (set as listener attributes):
 * <ul>
 * <li><b>abortOnError</b> - If true, abort startup if validation errors are found.
 *     Default: false</li>
 * <li><b>logWarnings</b> - If true, log warnings to the console/logs.
 *     Default: true</li>
 * <li><b>logInfo</b> - If true, log informational messages.
 *     Default: false</li>
 * </ul>
 *
 * <p>Example listener configuration with options:
 * <pre>
 * &lt;Listener className="org.apache.catalina.startup.validator.StartupValidationListener"
 *           abortOnError="true"
 *           logWarnings="true"
 *           logInfo="false" /&gt;
 * </pre>
 */
public class StartupValidationListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(StartupValidationListener.class);
    private static final StringManager sm = StringManager.getManager(StartupValidationListener.class);

    private boolean abortOnError = false;
    private boolean logWarnings = true;
    private boolean logInfo = false;

    /**
     * Sets whether to abort startup if validation errors are found.
     *
     * @param abortOnError true to abort on errors
     */
    public void setAbortOnError(boolean abortOnError) {
        this.abortOnError = abortOnError;
    }

    /**
     * Sets whether to log warning messages.
     *
     * @param logWarnings true to log warnings
     */
    public void setLogWarnings(boolean logWarnings) {
        this.logWarnings = logWarnings;
    }

    /**
     * Sets whether to log informational messages.
     *
     * @param logInfo true to log info messages
     */
    public void setLogInfo(boolean logInfo) {
        this.logInfo = logInfo;
    }

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        // Run before init() to check ports before they're bound,
        // return for any other events.
        if (!Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
            return;
        }

        if (!(event.getLifecycle() instanceof Server)) {
            log.warn(sm.getString("startupValidationListener.notServer"));
            return;
        }

        Server server = (Server) event.getLifecycle();

        if (log.isInfoEnabled()) {
            log.info(sm.getString("startupValidationListener.starting"));
        }

        // Init registry and validate server config
        ValidatorRegistry registry = new ValidatorRegistry();
        ValidationResult result = registry.validate(server);

        // Log findings
        logFindings(result);

        // Should we abort now?
        if (abortOnError && result.getErrorCount() > 0) {
            String message = sm.getString("startupValidationListener.abortingOnErrors",
                    String.valueOf(result.getErrorCount()));
            log.error(message);
            throw new StartupAbortException(message);
        }

        if (log.isInfoEnabled()) {
            log.info(sm.getString("startupValidationListener.complete",
                    String.valueOf(result.getErrorCount()),
                    String.valueOf(result.getWarningCount()),
                    String.valueOf(result.getInfoCount())));
        }
    }

    private void logFindings(ValidationResult result) {
        for (ValidationResult.Finding finding : result.getFindings()) {
            switch (finding.getSeverity()) {
                case ERROR:
                    log.error(formatFinding(finding));
                    break;
                case WARNING:
                    if (logWarnings) {
                        log.warn(formatFinding(finding));
                    }
                    break;
                case INFO:
                    if (logInfo && log.isInfoEnabled()) {
                        log.info(formatFinding(finding));
                    }
                    break;
            }
        }
    }

    private String formatFinding(ValidationResult.Finding finding) {
        if (finding.getLocation() != null) {
            return finding.getLocation() + ": " + finding.getMessage();
        }
        return finding.getMessage();
    }
}
