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
package org.apache.catalina.mbeans;

import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.HostConfig;

public class ContainerMBean extends BaseCatalinaMBean<ContainerBase> {

    /**
     * Add a new child Container to those associated with this Container, if supported. Won't start the child yet. Has
     * to be started with a call to Start method after necessary configurations are done.
     *
     * @param type ClassName of the child to be added
     * @param name Name of the child to be added
     *
     * @exception MBeanException if the child cannot be added
     */
    public void addChild(String type, String name) throws MBeanException {

        Container contained = (Container) newInstance(type);
        contained.setName(name);

        if (contained instanceof StandardHost) {
            HostConfig config = new HostConfig();
            contained.addLifecycleListener(config);
        } else if (contained instanceof StandardContext) {
            ContextConfig config = new ContextConfig();
            contained.addLifecycleListener(config);
        }

        boolean oldValue = true;

        ContainerBase container = doGetManagedResource();
        try {
            oldValue = container.getStartChildren();
            container.setStartChildren(false);
            container.addChild(contained);
            contained.init();
        } catch (LifecycleException e) {
            throw new MBeanException(e);
        } finally {
            if (container != null) {
                container.setStartChildren(oldValue);
            }
        }
    }


    /**
     * Remove an existing child Container from association with this parent Container.
     *
     * @param name Name of the existing child Container to be removed
     *
     * @throws MBeanException if the child cannot be removed
     */
    public void removeChild(String name) throws MBeanException {
        if (name != null) {
            Container container = doGetManagedResource();
            Container contained = container.findChild(name);
            container.removeChild(contained);
        }
    }


    /**
     * Adds a valve to this Container instance.
     *
     * @param valveType ClassName of the valve to be added
     *
     * @return the MBean name of the new valve
     *
     * @throws MBeanException if adding the valve failed
     */
    public String addValve(String valveType) throws MBeanException {
        Valve valve = (Valve) newInstance(valveType);

        Container container = doGetManagedResource();
        container.getPipeline().addValve(valve);

        if (valve instanceof JmxEnabled) {
            return ((JmxEnabled) valve).getObjectName().toString();
        } else {
            return null;
        }
    }


    /**
     * Remove an existing Valve.
     *
     * @param valveName MBean Name of the Valve to remove
     *
     * @exception MBeanException if a component cannot be removed
     */
    public void removeValve(String valveName) throws MBeanException {
        Container container = doGetManagedResource();

        ObjectName oname;
        try {
            oname = new ObjectName(valveName);
        } catch (MalformedObjectNameException | NullPointerException e) {
            throw new MBeanException(e);
        }

        if (container != null) {
            Valve[] valves = container.getPipeline().getValves();
            for (Valve valve : valves) {
                if (valve instanceof JmxEnabled) {
                    ObjectName voname = ((JmxEnabled) valve).getObjectName();
                    if (voname.equals(oname)) {
                        container.getPipeline().removeValve(valve);
                    }
                }
            }
        }
    }


    /**
     * Add a LifecycleEvent listener to this component.
     *
     * @param type ClassName of the listener to add
     *
     * @throws MBeanException if adding the listener failed
     */
    public void addLifecycleListener(String type) throws MBeanException {
        LifecycleListener listener = (LifecycleListener) newInstance(type);
        Container container = doGetManagedResource();
        container.addLifecycleListener(listener);
    }


    /**
     * Remove a LifecycleEvent listeners from this component.
     *
     * @param type The ClassName of the listeners to be removed. Note that all the listeners having given ClassName will
     *                 be removed.
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public void removeLifecycleListeners(String type) throws MBeanException {
        Container container = doGetManagedResource();

        LifecycleListener[] listeners = container.findLifecycleListeners();
        for (LifecycleListener listener : listeners) {
            if (listener.getClass().getName().equals(type)) {
                container.removeLifecycleListener(listener);
            }
        }
    }


    /**
     * List the class name of each of the lifecycle listeners added to this container.
     *
     * @return the lifecycle listeners class names
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public String[] findLifecycleListenerNames() throws MBeanException {
        Container container = doGetManagedResource();
        List<String> result = new ArrayList<>();

        LifecycleListener[] listeners = container.findLifecycleListeners();
        for (LifecycleListener listener : listeners) {
            result.add(listener.getClass().getName());
        }

        return result.toArray(new String[0]);
    }


    /**
     * List the class name of each of the container listeners added to this container.
     *
     * @return the container listeners class names
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public String[] findContainerListenerNames() throws MBeanException {
        Container container = doGetManagedResource();
        List<String> result = new ArrayList<>();

        ContainerListener[] listeners = container.findContainerListeners();
        for (ContainerListener listener : listeners) {
            result.add(listener.getClass().getName());
        }

        return result.toArray(new String[0]);
    }
}
