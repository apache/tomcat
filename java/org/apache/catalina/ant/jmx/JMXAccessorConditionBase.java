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
package org.apache.catalina.ant.jmx;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.taskdefs.condition.Condition;

/**
 * Base class for JMX accessor conditions.
 */
public abstract class JMXAccessorConditionBase extends ProjectComponent implements Condition {

    /**
     * Constructs a new JMXAccessorConditionBase.
     */
    public JMXAccessorConditionBase() {
    }

    private String url = null;
    private String host = "localhost";
    private String port = "8050";
    private String password = null;
    private String username = null;
    private String name = null;
    private String attribute;
    private String value;
    private String ref = "jmx.server";

    /**
     * Get the attribute name.
     *
     * @return the attribute name
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * Set the attribute name.
     *
     * @param attribute the attribute name to set
     */
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    /**
     * Get the JMX host.
     *
     * @return the JMX host
     */
    public String getHost() {
        return host;
    }

    /**
     * Set the JMX host.
     *
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Get the MBean object name.
     *
     * @return the MBean object name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the MBean object name.
     *
     * @param objectName the name to set
     */
    public void setName(String objectName) {
        this.name = objectName;
    }

    /**
     * Get the JMX password.
     *
     * @return the JMX password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Set the JMX password.
     *
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Get the JMX port.
     *
     * @return the JMX port
     */
    public String getPort() {
        return port;
    }

    /**
     * Set the JMX port.
     *
     * @param port the port to set
     */
    public void setPort(String port) {
        this.port = port;
    }

    /**
     * Get the JMX URL.
     *
     * @return the JMX URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Set the JMX URL.
     *
     * @param url the URL to set
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Get the JMX username.
     *
     * @return the JMX username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Set the JMX username.
     *
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Get the expected attribute value.
     *
     * @return the expected attribute value
     */
    public String getValue() {
        return value;
    }

    /**
     * Set the expected attribute value.
     *
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Get the project reference for the JMX connection.
     *
     * @return the project reference
     */
    public String getRef() {
        return ref;
    }

    /**
     * Set the project reference for the JMX connection.
     *
     * @param refId the reference to set
     */
    public void setRef(String refId) {
        this.ref = refId;
    }

    /**
     * Get JMXConnection (default look at <em>jmx.server</em> project reference from jmxOpen Task).
     *
     * @return active JMXConnection
     *
     * @throws MalformedURLException Invalid URL for JMX server
     * @throws IOException           Connection error
     */
    protected MBeanServerConnection getJMXConnection() throws MalformedURLException, IOException {
        return JMXAccessorTask.accessJMXConnection(getProject(), getUrl(), getHost(), getPort(), getUsername(),
                getPassword(), ref);
    }

    /**
     * Get value from MBeans attribute.
     *
     * @return The value
     */
    protected String accessJMXValue() {
        try {
            Object result = getJMXConnection().getAttribute(new ObjectName(name), attribute);
            if (result != null) {
                return result.toString();
            }
        } catch (Exception e) {
            // ignore access or connection open errors
        }
        return null;
    }
}

