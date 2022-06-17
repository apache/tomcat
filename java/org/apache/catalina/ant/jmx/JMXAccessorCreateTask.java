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

import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;

/**
 * Create new MBean at <em>JMX</em> JSR 160 MBeans Server.
 * <ul>
 * <li>Create Mbeans</li>
 * <li>Create Mbeans with parameter</li>
 * <li>Create remote Mbeans with different classloader</li>
 * </ul>
 * <p>
 * Examples:
 * <br>
 * create a new Mbean at jmx.server connection
 * </p>
 * <pre>
 *   &lt;jmx:create
 *           ref="jmx.server"
 *           name="Catalina:type=MBeanFactory"
 *           className="org.apache.catalina.mbeans.MBeanFactory"
 *           classLoader="Catalina:type=ServerClassLoader,name=server"&gt;
 *            &lt;Arg value="org.apache.catalina.mbeans.MBeanFactory" /&gt;
 *   &lt;/jmxCreate/&gt;
 * </pre>
 * <p>
 * <b>WARNING</b>Not all Tomcat MBeans can create remotely and autoregister by its parents!
 * Please, use the MBeanFactory operation to generate valves and realms.
 * </p>
 * <p>
 * First call to a remote MBeanserver save the JMXConnection a reference <em>jmx.server</em>
 * </p>
 * These tasks require Ant 1.6 or later interface.
 *
 * @author Peter Rossbach
 * @since 5.5.12
 */
public class JMXAccessorCreateTask extends JMXAccessorTask {
    // ----------------------------------------------------- Instance Variables

    private String className;
    private String classLoader;
    private List<Arg> args=new ArrayList<>();

    // ------------------------------------------------------------- Properties

    /**
     * @return Returns the classLoader.
     */
    public String getClassLoader() {
        return classLoader;
    }

    /**
     * @param classLoaderName The classLoader to set.
     */
    public void setClassLoader(String classLoaderName) {
        this.classLoader = classLoaderName;
    }

    /**
     * @return Returns the className.
     */
    public String getClassName() {
        return className;
    }

    /**
     * @param className The className to set.
     */
    public void setClassName(String className) {
        this.className = className;
    }

    public void addArg(Arg arg ) {
        args.add(arg);
    }

    /**
     * @return Returns the args.
     */
    public List<Arg> getArgs() {
        return args;
    }
    /**
     * @param args The args to set.
     */
    public void setArgs(List<Arg> args) {
        this.args = args;
    }

    // ------------------------------------------------------ protected Methods

    @Override
    public String jmxExecute(MBeanServerConnection jmxServerConnection)
        throws Exception {

        if (getName() == null) {
            throw new BuildException("Must specify a 'name'");
        }
        if ((className == null)) {
            throw new BuildException(
                    "Must specify a 'className' for get");
        }
        jmxCreate(jmxServerConnection, getName());
        return null;
    }

    /**
     * Create new MBean from ClassLoader identified by an ObjectName.
     *
     * @param jmxServerConnection Connection to the JMX server
     * @param name MBean name
     * @throws Exception Error creating MBean
     */
    protected void jmxCreate(MBeanServerConnection jmxServerConnection,
            String name) throws Exception {
        Object argsA[] = null;
        String sigA[] = null;
        if (args != null) {
            argsA = new Object[ args.size()];
            sigA = new String[args.size()];
            for( int i=0; i<args.size(); i++ ) {
                Arg arg=args.get(i);
                if (arg.getType() == null) {
                    arg.setType("java.lang.String");
                    sigA[i]=arg.getType();
                    argsA[i]=arg.getValue();
                } else {
                    sigA[i]=arg.getType();
                    argsA[i]=convertStringToType(arg.getValue(),arg.getType());
                }
            }
        }
        if (classLoader != null && !classLoader.isEmpty()) {
            if (isEcho()) {
                handleOutput("create MBean " + name + " from class " + className + " with classLoader " + classLoader);
            }
            if(args == null) {
                jmxServerConnection.createMBean(className, new ObjectName(name), new ObjectName(classLoader));
            } else {
                jmxServerConnection.createMBean(className, new ObjectName(name), new ObjectName(classLoader),argsA,sigA);
            }

        } else {
            if (isEcho()) {
                handleOutput("create MBean " + name + " from class " + className);
            }
            if(args == null) {
                jmxServerConnection.createMBean(className, new ObjectName(name));
            } else {
                jmxServerConnection.createMBean(className, new ObjectName(name),argsA,sigA);
            }
        }
    }

}
