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
package org.apache.tomcat.util.descriptor.web;

import java.io.Serial;
import java.io.Serializable;


/**
 * Representation of a context initialization parameter that is configured in the server configuration file, rather than
 * the application deployment descriptor. This is convenient for establishing default values (which may be configured to
 * allow application overrides or not) without having to modify the application deployment descriptor itself.
 */
public class ApplicationParameter implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ApplicationParameter.
     */
    public ApplicationParameter() {
    }

    // ------------------------------------------------------------- Properties


    /**
     * The description of this environment entry.
     */
    private String description = null;

    /**
     * Returns the description of this parameter.
     *
     * @return the description of this parameter
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the description of this parameter.
     *
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * The name of this application parameter.
     */
    private String name = null;

    /**
     * Returns the name of this parameter.
     *
     * @return the name of this parameter
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of this parameter.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }


    /**
     * Does this application parameter allow overrides by the application deployment descriptor?
     */
    private boolean override = true;

    /**
     * Returns whether overrides are allowed.
     *
     * @return whether overrides are allowed
     */
    public boolean getOverride() {
        return this.override;
    }

    /**
     * Sets whether overrides are allowed.
     *
     * @param override whether overrides are allowed
     */
    public void setOverride(boolean override) {
        this.override = override;
    }


    /**
     * The value of this application parameter.
     */
    private String value = null;

    /**
     * Returns the value of this parameter.
     *
     * @return the value of this parameter
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Sets the value of this parameter.
     *
     * @param value the value
     */
    public void setValue(String value) {
        this.value = value;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ApplicationParameter[");
        sb.append("name=");
        sb.append(name);
        if (description != null) {
            sb.append(", description=");
            sb.append(description);
        }
        sb.append(", value=");
        sb.append(value);
        sb.append(", override=");
        sb.append(override);
        sb.append(']');
        return sb.toString();

    }


}
