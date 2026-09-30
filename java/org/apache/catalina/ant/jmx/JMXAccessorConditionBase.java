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
import javax.management.remote.JMXConnector;

import org.apache.tools.ant.BuildException;
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
    /*
     * A null host or port means the attribute was not specified. The defaults (localhost and 8050) are applied when
     * the JMX service URL is built, so an explicitly specified value can be detected and compared against an existing
     * connection reference.
     */
    private String host = null;
    private String port = null;
    private String password = null;
    private String username = null;
    private String name = null;
    private String attribute;
    private String value;
    private String ref = "jmx.server";

    /*
     * Connector created for the current evaluation when the connection is not shared through a project reference. It
     * is closed once the value has been read so repeated waitfor evaluations do not accumulate connections.
     */
    private JMXConnector jmxConnector;

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
        if (ref != null && !ref.isEmpty() && getProject() != null) {
            // Reuse or establish a shared connection stored in the project reference; this condition does not own it.
            return JMXAccessorTask.accessJMXConnection(getProject(), getUrl(), getHost(), getPort(), getUsername(),
                    getPassword(), ref);
        }
        // No reference to store it in: this condition owns the connection and closes it after the evaluation.
        jmxConnector = JMXAccessorTask.createJMXConnector(getUrl(), getHost(), getPort(), getUsername(), getPassword());
        return jmxConnector.getMBeanServerConnection();
    }

    /**
     * Close the JMX connection owned by this condition, if any. Connections shared through a project reference are
     * left open for reuse.
     */
    private void closeJMXConnector() {
        if (jmxConnector != null) {
            try {
                jmxConnector.close();
            } catch (IOException e) {
                // Ignore errors while closing
            }
            jmxConnector = null;
        }
    }

    /**
     * Get value from MBeans attribute.
     *
     * @return The value
     */
    protected String accessJMXValue() {
        try {
            MBeanServerConnection jmxServerConnection = getJMXConnection();
            Object result = jmxServerConnection.getAttribute(new ObjectName(name), attribute);
            if (result != null) {
                return result.toString();
            }
        } catch (BuildException e) {
            // Re-throw configuration errors.
            throw e;
        } catch (Exception e) {
            /*
             * Exceptions are ignored for compatibility with the waitFor task when waiting for the server to start. If
             * the exception was thrown the build task would fail rather than wait.
             */
        } finally {
            closeJMXConnector();
        }
        return null;
    }
}

