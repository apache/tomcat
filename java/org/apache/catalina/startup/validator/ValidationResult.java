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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.tomcat.util.res.StringManager;

/**
 * Accumulates validation findings (errors, warnings, and informational messages)
 * from configuration validators.
 */
public class ValidationResult {

    private final List<Finding> findings = new ArrayList<>();
    private final StringManager sm;

    /**
     * Severity levels for validation findings.
     */
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public ValidationResult(StringManager sm) {
        this.sm = sm;
    }

    /**
     * Adds an error finding. Errors indicate configuration problems that
     * will likely cause runtime failures.
     *
     * @param messageKey the resource bundle key for the error message
     * @param args optional arguments for message formatting
     */
    public void addError(String messageKey, Object... args) {
        addFinding(Severity.ERROR, messageKey, args);
    }

    /**
     * Adds an error finding with a specific location reference.
     *
     * @param location the location (e.g., "server.xml:42" or "Port 8080")
     * @param messageKey the resource bundle key for the error message
     * @param args optional arguments for message formatting
     */
    public void addError(String location, String messageKey, Object... args) {
        addFinding(Severity.ERROR, location, messageKey, args);
    }

    /**
     * Adds a warning finding. Warnings indicate potentially problematic
     * configurations that may cause issues.
     *
     * @param messageKey the resource bundle key for the warning message
     * @param args optional arguments for message formatting
     */
    public void addWarning(String messageKey, Object... args) {
        addFinding(Severity.WARNING, messageKey, args);
    }

    /**
     * Adds a warning finding with a specific location reference.
     *
     * @param location the location (e.g., "server.xml:42" or "Port 8080")
     * @param messageKey the resource bundle key for the warning message
     * @param args optional arguments for message formatting
     */
    public void addWarning(String location, String messageKey, Object... args) {
        addFinding(Severity.WARNING, location, messageKey, args);
    }

    /**
     * Adds an informational finding. Info messages provide useful information
     * about the configuration without indicating problems.
     *
     * @param messageKey the resource bundle key for the info message
     * @param args optional arguments for message formatting
     */
    public void addInfo(String messageKey, Object... args) {
        addFinding(Severity.INFO, messageKey, args);
    }

    /**
     * Adds an informational finding with a specific location reference.
     *
     * @param location the location (e.g., "server.xml:42" or "Port 8080")
     * @param messageKey the resource bundle key for the info message
     * @param args optional arguments for message formatting
     */
    public void addInfo(String location, String messageKey, Object... args) {
        addFinding(Severity.INFO, location, messageKey, args);
    }

    private void addFinding(Severity severity, String messageKey, Object... args) {
        addFinding(severity, null, messageKey, args);
    }

    private void addFinding(Severity severity, String location, String messageKey, Object... args) {
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(messageKey, "messageKey cannot be null");
        String message = sm.getString(messageKey, args);
        findings.add(new Finding(severity, location, message));
    }

    /**
     * Returns all validation findings.
     *
     * @return an unmodifiable list of findings
     */
    public List<Finding> getFindings() {
        return Collections.unmodifiableList(findings);
    }

    /**
     * Returns the count of error findings.
     *
     * @return the number of errors
     */
    public int getErrorCount() {
        return (int) findings.stream().filter(f -> f.severity == Severity.ERROR).count();
    }

    /**
     * Returns the count of warning findings.
     *
     * @return the number of warnings
     */
    public int getWarningCount() {
        return (int) findings.stream().filter(f -> f.severity == Severity.WARNING).count();
    }

    /**
     * Returns the count of informational findings.
     *
     * @return the number of info messages
     */
    public int getInfoCount() {
        return (int) findings.stream().filter(f -> f.severity == Severity.INFO).count();
    }

    /**
     * Returns whether validation passed (no errors).
     *
     * @return true if there are no errors
     */
    public boolean isSuccess() {
        return getErrorCount() == 0;
    }

    /**
     * Returns whether there are any findings at all.
     *
     * @return true if there are any findings
     */
    public boolean hasFindings() {
        return !findings.isEmpty();
    }

    /**
     * Represents a single validation finding.
     */
    public static class Finding {
        private final Severity severity;
        private final String location;
        private final String message;

        public Finding(Severity severity, String location, String message) {
            this.severity = severity;
            this.location = location;
            this.message = message;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getLocation() {
            return location;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[').append(severity).append(']');
            if (location != null) {
                sb.append(' ').append(location).append(':');
            }
            sb.append(' ').append(message);
            return sb.toString();
        }
    }
}
