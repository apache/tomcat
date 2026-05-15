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
package org.apache.tomcat.util.descriptor.tld;

import java.util.HashMap;
import java.util.Map;

/**
 * Model of a Tag Library Validator from the XML descriptor.
 */
public class ValidatorXml {
    /**
     * The fully qualified class name of the validator.
     */
    private String validatorClass;
    /**
     * The initialization parameters for the validator.
     */
    private final Map<String,String> initParams = new HashMap<>();

    /**
     * Constructs a new ValidatorXml.
     */
    public ValidatorXml() {
    }

    /**
     * Returns the validator class name.
     *
     * @return the validator class name
     */
    public String getValidatorClass() {
        return validatorClass;
    }

    /**
     * Sets the validator class name.
     *
     * @param validatorClass The validator class name
     */
    public void setValidatorClass(String validatorClass) {
        this.validatorClass = validatorClass;
    }

    /**
     * Adds an initialization parameter.
     *
     * @param name  The parameter name
     * @param value The parameter value
     */
    public void addInitParam(String name, String value) {
        initParams.put(name, value);
    }

    /**
     * Returns the initialization parameters.
     *
     * @return the initialization parameters
     */
    public Map<String,String> getInitParams() {
        return initParams;
    }
}
