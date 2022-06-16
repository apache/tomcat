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

public abstract class JMXAccessorConditionBase extends ProjectComponent implements Condition {

    private String url = null;
    private String host = "localhost";
    private String port = "8050";
    private String password = null;
    private String username = null;
    private String name = null;
    private String attribute;
    private String value;
    private String ref = "jmx.server" ;

    /**
     * @return Returns the attribute.
     */
    public String getAttribute() {
        return attribute;
    }
    /**
     * @param attribute The attribute to set.
     */
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }
    /**
     * @return Returns the host.
     */
    public String getHost() {
        return host;
    }
    /**
     * @param host The host to set.
     */
    public void setHost(String host) {
        this.host = host;
    }
    /**
     * @return Returns the name.
     */
    public String getName() {
        return name;
    }
    /**
     * @param objectName The name to set.
     */
    public void setName(String objectName) {
        this.name = objectName;
    }
    /**
     * @return Returns the password.
     */
    public String getPassword() {
        return password;
    }
    /**
     * @param password The password to set.
     */
    public void setPassword(String password) {
        this.password = password;
    }
    /**
     * @return Returns the port.
     */
    public String getPort() {
        return port;
    }
    /**
     * @param port The port to set.
     */
    public void setPort(String port) {
        this.port = port;
    }
    /**
     * @return Returns the url.
     */
    public String getUrl() {
        return url;
    }
    /**
     * @param url The url to set.
     */
    public void setUrl(String url) {
        this.url = url;
    }
    /**
     * @return Returns the username.
     */
    public String getUsername() {
        return username;
    }
    /**
     * @param username The username to set.
     */
    public void setUsername(String username) {
        this.username = username;
    }
    /**
     * @return Returns the value.
     */
    public String getValue() {
        return value;
    }
    // The setter for the "value" attribute
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * @return Returns the ref.
     */
    public String getRef() {
        return ref;
    }
    /**
     * @param refId The ref to set.
     */
    public void setRef(String refId) {
        this.ref = refId;
    }

    /**
     * Get JMXConnection (default look at <em>jmx.server</em> project reference
     * from jmxOpen Task).
     *
     * @return active JMXConnection
     * @throws MalformedURLException Invalid URL for JMX server
     * @throws IOException Connection error
     */
    protected MBeanServerConnection getJMXConnection()
            throws MalformedURLException, IOException {
        return JMXAccessorTask.accessJMXConnection(
                getProject(),
                getUrl(), getHost(),
                getPort(), getUsername(), getPassword(), ref);
    }

    /**
     * Get value from MBeans attribute.
     *
     * @return The value
     */
    protected String accessJMXValue() {
        try {
            Object result = getJMXConnection().getAttribute(
                    new ObjectName(name), attribute);
            if (result != null) {
                return result.toString();
            }
        } catch (Exception e) {
            // ignore access or connection open errors
        }
        return null;
    }
}

