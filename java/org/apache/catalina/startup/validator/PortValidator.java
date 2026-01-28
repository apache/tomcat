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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;

/**
 * Validates port configuration for all connectors and the shutdown port.
 *
 * <p>Checks performed:
 * <ul>
 * <li>Port conflicts (port already in use)</li>
 * <li>Privileged port access (ports &lt; 1024 without root privileges)</li>
 * <li>Shutdown port security (disabled, missing, or insecure)</li>
 * <li>Duplicate ports across connectors</li>
 * <li>Invalid port numbers</li>
 * </ul>
 */
public class PortValidator {

    private static final int PRIVILEGED_PORT_THRESHOLD = 1024;
    private static final int MAX_PORT_VALUE = 65535;
    private static final String SHUTDOWN_DISABLED = "SHUTDOWN";

    /**
     * Validates port configuration for all connectors and the shutdown port.
     *
     * @param server the server to validate
     * @param result the validation result to populate
     */
    public void validate(Server server, ValidationResult result) {
        if (server == null) {
            return;
        }

        Map<Integer, String> portUsage = new HashMap<>();
        Set<Integer> inUsePorts = new HashSet<>();

        // Check shutdown port
        validateShutdownPort(server, result, portUsage);

        // Check all connector ports
        for (Service service : server.findServices()) {
            for (Connector connector : service.findConnectors()) {
                validateConnectorPort(connector, service.getName(), result, portUsage, inUsePorts);
            }
        }
    }

    private void validateShutdownPort(Server server, ValidationResult result,
            Map<Integer, String> portUsage) {
        int shutdownPort = server.getPort();
        String shutdownCommand = server.getShutdown();

        // Check if shutdown is disabled
        if (shutdownPort < 0) {
            result.addInfo("portValidator.shutdownDisabled");
            return;
        }

        // Check for insecure default shutdown command
        if (SHUTDOWN_DISABLED.equals(shutdownCommand)) {
            result.addWarning("Shutdown Port", "portValidator.shutdownCommandDefault",
                    String.valueOf(shutdownPort));
        }

        // Validate port number
        if (shutdownPort > MAX_PORT_VALUE) {
            result.addError("Shutdown Port", "portValidator.invalidPort",
                    String.valueOf(shutdownPort));
            return;
        }

        // Check for privileged port
        if (shutdownPort < PRIVILEGED_PORT_THRESHOLD && !isRunningAsRoot()) {
            result.addError("Shutdown Port", "portValidator.privilegedPort",
                    String.valueOf(shutdownPort), getCurrentUser());
        }

        // Check if port is in use
        if (!isPortAvailable(shutdownPort, null)) {
            result.addError("Shutdown Port", "portValidator.shutdownPortInUse",
                    String.valueOf(shutdownPort));
        }

        portUsage.put(shutdownPort, "Shutdown Port");
    }

    private void validateConnectorPort(Connector connector, String serviceName,
            ValidationResult result, Map<Integer, String> portUsage, Set<Integer> inUsePorts) {
        int port = connector.getPort();
        String protocol = connector.getProtocol();
        Object addressObj = connector.getProperty("address");
        InetAddress bindAddress = null;

        // Handle both String and InetAddress types
        if (addressObj instanceof InetAddress) {
            bindAddress = (InetAddress) addressObj;
        } else if (addressObj != null && !addressObj.toString().isEmpty()) {
            try {
                bindAddress = InetAddress.getByName(addressObj.toString());
            } catch (UnknownHostException e) {
                result.addError(String.format("Port %d (%s, %s)", port, protocol, serviceName),
                        "portValidator.invalidAddress", addressObj.toString());
                return;
            }
        }

        String location = String.format("Port %d (%s, %s)", port, protocol, serviceName);

        // Validate port number
        if (port < 0) {
            result.addError(location, "portValidator.invalidPort", String.valueOf(port));
            return;
        }

        if (port > MAX_PORT_VALUE) {
            result.addError(location, "portValidator.invalidPort", String.valueOf(port));
            return;
        }

        // Check for duplicate ports in configuration
        if (portUsage.containsKey(port)) {
            result.addError(location, "portValidator.duplicatePort",
                    String.valueOf(port), portUsage.get(port));
            return;
        }

        portUsage.put(port, location);

        // Check for privileged port
        if (port < PRIVILEGED_PORT_THRESHOLD && !isRunningAsRoot()) {
            result.addError(location, "portValidator.privilegedPort",
                    String.valueOf(port), getCurrentUser());
        }

        // Check if port is in use (avoid checking the same port multiple times)
        if (!inUsePorts.contains(port)) {
            if (!isPortAvailable(port, bindAddress)) {
                result.addError(location, "portValidator.portInUse",
                        String.valueOf(port));
                inUsePorts.add(port);
            }
        }

        // Check for insecure AJP configuration
        if (protocol.toLowerCase().contains("ajp")) {
            validateAjpConnector(connector, location, result);
        }
    }

    private void validateAjpConnector(Connector connector, String location, ValidationResult result) {
        Object secretObj = connector.getProperty("secret");
        String secret = secretObj != null ? secretObj.toString() : null;
        Object addressObj = connector.getProperty("address");
        String address = addressObj != null ? addressObj.toString() : null;

        // Check for missing secret (security risk)
        if (secret == null || secret.isEmpty()) {
            result.addWarning(location, "portValidator.ajpMissingSecret");
        }

        // Warn if listening on all interfaces
        if (address == null || address.isEmpty() || "0.0.0.0".equals(address)) {
            result.addWarning(location, "portValidator.ajpListeningAll");
        }
    }

    /**
     * Checks if a port is available by attempting to bind to it.
     *
     * <p><b>Note:</b> This check is subject to a time-of-check-time-of-use (TOCTOU) race
     * condition. Another process may bind to the port between this check and when Tomcat
     * attempts to bind. This is an inherent limitation of port availability checking and
     * cannot be fully eliminated. The check is still useful for catching obvious conflicts
     * early, but the application must handle bind failures gracefully at runtime.
     *
     * @param port the port number to check
     * @param address the address to bind to, or null for all interfaces
     * @return true if the port is available, false if already in use
     */
    private boolean isPortAvailable(int port, InetAddress address) {
        try (ServerSocket socket = new ServerSocket(port, 1, address)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isRunningAsRoot() {
        String osName = System.getProperty("os.name");
        if (osName != null && osName.toLowerCase().contains("windows")) {
            // Windows doesn't have the same privileged port restriction
            return true;
        }

        String user = getCurrentUser();
        return "root".equals(user);
    }

    private String getCurrentUser() {
        String user = System.getProperty("user.name");
        if (user == null || user.isEmpty()) {
            user = "unknown";
        }
        return user;
    }

}
