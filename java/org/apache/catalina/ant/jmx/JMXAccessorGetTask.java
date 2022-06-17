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


import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;


/**
 * Access <em>JMX</em> JSR 160 MBeans Server.
 * <ul>
 * <li>Get Mbeans attributes</li>
 * <li>Show Get result as Ant console log</li>
 * <li>Bind Get result as Ant properties</li>
 * </ul>
 * <p>
 * Examples:
 * <br>
 * Get an Mbean IDataSender attribute nrOfRequests and create a new ant property <em>IDataSender.9025.nrOfRequests</em>
 * </p>
 * <pre>
 *   &lt;jmx:get
 *           ref="jmx.server"
 *           name="Catalina:type=IDataSender,host=localhost,senderAddress=192.168.1.2,senderPort=9025"
 *           attribute="nrOfRequests"
 *           resultproperty="IDataSender.9025.nrOfRequests"
 *           echo="false"&gt;
 *       /&gt;
 * </pre>
 * <p>
 * First call to a remote MBeanserver save the JMXConnection a referenz <em>jmx.server</em>
 * </p>
 * These tasks require Ant 1.6 or later interface.
 *
 * @author Peter Rossbach
 * @since 5.5.10
 */
public class JMXAccessorGetTask extends JMXAccessorTask {


    // ----------------------------------------------------- Instance Variables

    private String attribute;

    // ------------------------------------------------------------- Properties

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


    // ------------------------------------------------------ protected Methods

    @Override
    public String jmxExecute(MBeanServerConnection jmxServerConnection)
        throws Exception {

        if (getName() == null) {
            throw new BuildException("Must specify a 'name'");
        }
        if ((attribute == null)) {
            throw new BuildException(
                    "Must specify a 'attribute' for get");
        }
        return  jmxGet(jmxServerConnection, getName());
    }


    /**
     * Get property value.
     *
     * @param jmxServerConnection Connection to the JMX server
     * @param name The MBean name
     * @return The error message if any
     * @throws Exception An error occurred
     */
    protected String jmxGet(MBeanServerConnection jmxServerConnection, String name) throws Exception {
        String error = null;
        if(isEcho()) {
            handleOutput("MBean " + name + " get attribute " + attribute );
        }
        Object result = jmxServerConnection.getAttribute(
                new ObjectName(name), attribute);
        if (result != null) {
            echoResult(attribute,result);
            createProperty(result);
        } else {
            error = "Attribute " + attribute + " is empty";
        }
        return error;
    }
}
