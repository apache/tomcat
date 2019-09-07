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

import org.apache.tools.ant.BuildException;

/**
 *
 * Definition
 * <pre>
 *   &lt;path id="catalina_ant"&gt;
 *       &lt;fileset dir="${catalina.home}/server/lib"&gt;
 *           &lt;include name="catalina-ant.jar"/&gt;
 *       &lt;/fileset&gt;
 *   &lt;/path&gt;
 *
 *   &lt;typedef
 *       name="jmxEquals"
 *       classname="org.apache.catalina.ant.jmx.JMXAccessorEqualsCondition"
 *       classpathref="catalina_ant"/&gt;
 * </pre>
 *
 * usage: Wait for start backup node
 * <pre>
 *     &lt;target name="wait"&gt;
 *        &lt;waitfor maxwait="${maxwait}" maxwaitunit="second" timeoutproperty="server.timeout" &gt;
 *           &lt;and&gt;
 *               &lt;socket server="${server.name}" port="${server.port}"/&gt;
 *               &lt;http url="${url}"/&gt;
 *               &lt;jmxEquals
 *                   host="localhost" port="9014" username="controlRole" password="tomcat"
 *                   name="Catalina:type=IDataSender,host=localhost,senderAddress=192.168.111.1,senderPort=9025"
 *                   attribute="connected" value="true"
 *               /&gt;
 *           &lt;/and&gt;
 *       &lt;/waitfor&gt;
 *       &lt;fail if="server.timeout" message="Server ${url} don't answer inside ${maxwait} sec" /&gt;
 *       &lt;echo message="Server ${url} alive" /&gt;
 *   &lt;/target&gt;
 *
 * </pre>
 *
 * @author Peter Rossbach
 * @since 5.5.10
 */
public class JMXAccessorEqualsCondition extends JMXAccessorConditionBase {

    /**
     * Descriptive information describing this implementation.
     */
    private static final String info = "org.apache.catalina.ant.JMXAccessorEqualsCondition/1.1";

    /**
     * Return descriptive information about this implementation and the
     * corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return info;
    }

    @Override
    public boolean eval() {
        String value = getValue();

        if (value == null) {
            throw new BuildException("value attribute is not set");
        }
        if (getName() == null || getAttribute() == null) {
            throw new BuildException(
                    "Must specify an MBean name and attribute for equals condition");
        }
        //FIXME check url or host/parameter
        String jmxValue = accessJMXValue();
        if (jmxValue != null) {
            return jmxValue.equals(value);
        }
        return false;
    }
}

