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

import org.apache.catalina.Server;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Registry for managing execution of configuration validators.
 */
public class ValidatorRegistry {

    private static final Log log = LogFactory.getLog(ValidatorRegistry.class);
    private static final StringManager sm = StringManager.getManager(ValidatorRegistry.class);

    /**
     * Validates the server configuration with all enabled Validators.
     *
     * @param server the server to validate
     * @return the validation result
     */
    public ValidationResult validate(Server server) {
        ValidationResult result = new ValidationResult(sm);

        // Run the port validator
        PortValidator portValidator = new PortValidator();
        try {
            if (log.isDebugEnabled()) {
                log.debug("Running port configuration validation...");
            }
            portValidator.validate(server, result);
        } catch (Exception e) {
            log.warn("Error during port validation", e);
            result.addWarning("Validator failed: " + e.getMessage());
        }

        return result;
    }
}
