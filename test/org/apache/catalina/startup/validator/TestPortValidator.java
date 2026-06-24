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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.core.StandardService;
import org.apache.tomcat.util.res.StringManager;

/**
 * Tests for the validation framework with the PortValidator.
 */
public class TestPortValidator {

    private static final StringManager sm =
            StringManager.getManager("org.apache.catalina.startup.validator");

    private ValidatorRegistry registry;
    private ValidationResult result;
    private StandardServer server;
    private StandardService service;

    @Before
    public void setUp() {
        registry = new ValidatorRegistry();
        server = new StandardServer();
        service = new StandardService();
        service.setName("Catalina");
        server.addService(service);
    }

    @Test
    public void testValidConfiguration() {
        // Use a high port unlikely to be in use
        Connector connector = new Connector();
        connector.setPort(54321);
        service.addConnector(connector);

        result = registry.validate(server);

        // Should have no errors
        Assert.assertEquals("Should have no errors for valid config",
                0, result.getErrorCount());
    }

    @Test
    public void testInvalidPortNumber() {
        Connector connector = new Connector();
        connector.setPort(70000); // Invalid: > 65535
        service.addConnector(connector);

        result = registry.validate(server);

        Assert.assertTrue("Should have error for invalid port",
                result.getErrorCount() > 0);
    }

    @Test
    public void testDuplicatePorts() {
        Connector connector1 = new Connector();
        connector1.setPort(8080);
        service.addConnector(connector1);

        Connector connector2 = new Connector();
        connector2.setPort(8080);
        service.addConnector(connector2);

        result = registry.validate(server);

        boolean hasDuplicateError = false;
        for (ValidationResult.Finding finding : result.getFindings()) {
            if (finding.getMessage().contains("already configured")) {
                hasDuplicateError = true;
            }
        }

        Assert.assertTrue("Should detect duplicate port", hasDuplicateError);
    }

    @Test
    public void testDefaultShutdownCommand() {
        server.setPort(8005);
        server.setShutdown("SHUTDOWN"); // Default command

        Connector connector = new Connector();
        connector.setPort(54322);
        service.addConnector(connector);

        result = registry.validate(server);

        boolean hasWarning = false;
        for (ValidationResult.Finding finding : result.getFindings()) {
            if (finding.getSeverity() == ValidationResult.Severity.WARNING &&
                    finding.getMessage().contains("SHUTDOWN")) {
                hasWarning = true;
            }
        }

        Assert.assertTrue("Should warn about default shutdown command", hasWarning);
    }

    @Test
    public void testNullServer() {
        // Should not crash with null server
        result = registry.validate(null);
        Assert.assertNotNull(result);
    }

    @Test
    public void testAjpConnectorMissingSecret() {
        Connector connector = new Connector("AJP/1.3");
        connector.setPort(8009);
        service.addConnector(connector);

        result = registry.validate(server);

        boolean hasAjpWarning = false;
        for (ValidationResult.Finding finding : result.getFindings()) {
            if (finding.getMessage().contains("secret")) {
                hasAjpWarning = true;
            }
        }

        Assert.assertTrue("Should warn about missing AJP secret", hasAjpWarning);
    }

    @Test
    public void testMultipleServices() {
        StandardService service2 = new StandardService();
        service2.setName("Service2");
        server.addService(service2);

        Connector connector1 = new Connector();
        connector1.setPort(8080);
        service.addConnector(connector1);

        Connector connector2 = new Connector();
        connector2.setPort(8081);
        service2.addConnector(connector2);

        result = registry.validate(server);

        // Should validate both services without crashing
        Assert.assertNotNull(result);
    }
}
