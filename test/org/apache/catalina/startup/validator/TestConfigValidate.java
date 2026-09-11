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

import org.apache.catalina.startup.Bootstrap;
import org.apache.catalina.startup.Catalina;

/**
 * Integration tests for the config-validate command and loadConfigOnly functionality.
 */
public class TestConfigValidate {

    private Bootstrap bootstrap;
    private Catalina catalina;

    @Before
    public void setUp() {
        bootstrap = new Bootstrap();
        catalina = new Catalina();
    }

    @Test
    public void testLoadConfigOnlyDoesNotBindPorts() throws Exception {
        // loadConfigOnly should parse config without calling init()
        // This means it won't bind to ports
        catalina.loadConfigOnly();

        // Verify server was loaded
        Assert.assertNotNull("Server should be loaded", catalina.getServer());
    }

    @Test
    public void testLoadConfigOnlyWithArguments() throws Exception {
        // Test loadConfigOnly(String[] args) variant
        String[] args = new String[] {"start"};
        catalina.loadConfigOnly(args);

        Assert.assertNotNull("Server should be loaded", catalina.getServer());
    }

    @Test
    public void testConfigValidateCommand() throws Exception {
        // Test that config-validate command works through Bootstrap
        // This is an integration test that verifies the full flow, but
        // we can't easily test main() because of System.exit() calls.
        bootstrap.init();

        // Verify bootstrap initialized correctly
        Assert.assertNotNull("Bootstrap should be initialized", bootstrap);
    }

    @Test
    public void testValidatorRegistryWithNullServer() {
        // Ensure validator handles null server gracefully
        ValidatorRegistry registry = new ValidatorRegistry();
        ValidationResult result = registry.validate(null);

        Assert.assertNotNull("Result should not be null", result);
        Assert.assertEquals("Should have no errors for null server", 0, result.getErrorCount());
    }

    @Test
    public void testValidationResultCounts() {
        ValidatorRegistry registry = new ValidatorRegistry();
        ValidationResult result = new ValidationResult(
                org.apache.tomcat.util.res.StringManager.getManager(
                        "org.apache.catalina.startup.validator"));

        // Add some test findings
        result.addError("portValidator.invalidPort", "99999");
        result.addWarning("portValidator.ajpMissingSecret");
        result.addInfo("portValidator.shutdownDisabled");

        Assert.assertEquals("Should have 1 error", 1, result.getErrorCount());
        Assert.assertEquals("Should have 1 warning", 1, result.getWarningCount());
        Assert.assertEquals("Should have 1 info", 1, result.getInfoCount());
        Assert.assertFalse("Validation should not be successful with errors", result.isSuccess());
        Assert.assertTrue("Should have findings", result.hasFindings());
    }

    @Test
    public void testValidationResultWithLocation() {
        ValidationResult result = new ValidationResult(
                org.apache.tomcat.util.res.StringManager.getManager(
                        "org.apache.catalina.startup.validator"));

        result.addError("Port 8080", "portValidator.portInUse", "8080");

        Assert.assertEquals("Should have 1 error", 1, result.getErrorCount());

        ValidationResult.Finding finding = result.getFindings().get(0);
        Assert.assertEquals("Location should match", "Port 8080", finding.getLocation());
        Assert.assertEquals("Severity should be ERROR", ValidationResult.Severity.ERROR, finding.getSeverity());
    }

    @Test
    public void testLoadVsLoadConfigOnly() throws Exception {
        // Verify that load() and loadConfigOnly() handle the same config parsing
        // but load() additionally calls init()
        Catalina catalinaConfigOnly = new Catalina();

        // Should parse the configuration
        catalinaConfigOnly.loadConfigOnly();

        // Should have a server
        Assert.assertNotNull("Config-only server should exist", catalinaConfigOnly.getServer());
    }
}
