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
package org.apache.catalina;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.Mapper;

/**
 * A <strong>Service</strong> is a group of one or more <strong>Connectors</strong> that share a single
 * <strong>Container</strong> to process their incoming requests. This arrangement allows, for example, a non-SSL and
 * SSL connector to share the same population of web apps.
 * <p>
 * A given JVM can contain any number of Service instances; however, they are completely independent of each other and
 * share only the basic JVM facilities and classes on the system class path.
 *
 * @author Craig R. McClanahan
 */
public interface Service extends Lifecycle {

    // ------------------------------------------------------------- Properties

    /**
     * @return the <code>Engine</code> that handles requests for all <code>Connectors</code> associated with this
     *             Service.
     */
    Engine getContainer();

    /**
     * Set the <code>Engine</code> that handles requests for all <code>Connectors</code> associated with this Service.
     *
     * @param engine The new Engine
     */
    void setContainer(Engine engine);

    /**
     * @return the name of this Service.
     */
    String getName();

    /**
     * Set the name of this Service.
     *
     * @param name The new service name
     */
    void setName(String name);

    /**
     * @return the <code>Server</code> with which we are associated (if any).
     */
    Server getServer();

    /**
     * Set the <code>Server</code> with which we are associated (if any).
     *
     * @param server The server that owns this Service
     */
    void setServer(Server server);

    /**
     * @return the parent class loader for this component. If not set, return {@link #getServer()}
     *             {@link Server#getParentClassLoader()}. If no server has been set, return the system class loader.
     */
    ClassLoader getParentClassLoader();

    /**
     * Set the parent class loader for this service.
     *
     * @param parent The new parent class loader
     */
    void setParentClassLoader(ClassLoader parent);

    /**
     * @return the domain under which this container will be / has been registered.
     */
    String getDomain();


    // --------------------------------------------------------- Public Methods

    /**
     * Add a new Connector to the set of defined Connectors, and associate it with this Service's Container.
     *
     * @param connector The Connector to be added
     */
    void addConnector(Connector connector);

    /**
     * Find and return the set of Connectors associated with this Service.
     *
     * @return the set of associated Connectors
     */
    Connector[] findConnectors();

    /**
     * Remove the specified Connector from the set associated from this Service. The removed Connector will also be
     * disassociated from our Container.
     *
     * @param connector The Connector to be removed
     */
    void removeConnector(Connector connector);

    /**
     * Adds a named executor to the service
     *
     * @param ex Executor
     */
    void addExecutor(Executor ex);

    /**
     * Retrieves all executors
     *
     * @return Executor[]
     */
    Executor[] findExecutors();

    /**
     * Retrieves executor by name, null if not found
     *
     * @param name String
     *
     * @return Executor
     */
    Executor getExecutor(String name);

    /**
     * Removes an executor from the service
     *
     * @param ex Executor
     */
    void removeExecutor(Executor ex);

    /**
     * @return the mapper associated with this Service.
     */
    Mapper getMapper();
}
