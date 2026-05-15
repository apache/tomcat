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

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.startup.Catalina;

/**
 * A <code>Server</code> element represents the entire Catalina servlet container. Its attributes represent the
 * characteristics of the servlet container as a whole. A <code>Server</code> may contain one or more
 * <code>Services</code>, and the top level set of naming resources.
 * <p>
 * Normally, an implementation of this interface will also implement <code>Lifecycle</code>, such that when the
 * <code>start()</code> and <code>stop()</code> methods are called, all of the defined <code>Services</code> are also
 * started or stopped.
 * <p>
 * In between, the implementation must open a server socket on the port number specified by the <code>port</code>
 * property. When a connection is accepted, the first line is read and compared with the specified shutdown command. If
 * the command matches, shutdown of the server is initiated.
 */
public interface Server extends Lifecycle {

    // ------------------------------------------------------------- Properties

    /**
     * Returns the global naming resources for this server.
     *
     * @return the global naming resources
     */
    NamingResourcesImpl getGlobalNamingResources();


    /**
     * Set the global naming resources.
     *
     * @param globalNamingResources The new global naming resources
     */
    void setGlobalNamingResources(NamingResourcesImpl globalNamingResources);


    /**
     * Returns the global JNDI naming context for this server.
     *
     * @return the global naming context
     */
    javax.naming.Context getGlobalNamingContext();


    /**
     * Returns the port number on which the server listens for shutdown commands.
     *
     * @return the port number for shutdown commands
     *
     * @see #getPortOffset()
     * @see #getPortWithOffset()
     */
    int getPort();


    /**
     * Set the port number we listen to for shutdown commands.
     *
     * @param port The new port number
     *
     * @see #setPortOffset(int)
     */
    void setPort(int port);

    /**
     * Get the number that offsets the port used for shutdown commands. For example, if port is 8005, and portOffset is
     * 1000, the server listens at 9005.
     *
     * @return the port offset
     */
    int getPortOffset();

    /**
     * Set the number that offsets the server port used for shutdown commands. For example, if port is 8005, and you set
     * portOffset to 1000, connector listens at 9005.
     *
     * @param portOffset sets the port offset
     */
    void setPortOffset(int portOffset);

    /**
     * Get the actual port on which server is listening for the shutdown commands. If you do not set port offset, port
     * is returned. If you set port offset, port offset + port is returned.
     *
     * @return the port with offset
     */
    int getPortWithOffset();

    /**
     * Returns the address on which the server listens for shutdown commands.
     *
     * @return the address for shutdown commands
     */
    String getAddress();


    /**
     * Set the address on which we listen to for shutdown commands.
     *
     * @param address The new address
     */
    void setAddress(String address);


    /**
     * Returns the shutdown command string the server is waiting for.
     *
     * @return the shutdown command string
     */
    String getShutdown();


    /**
     * Set the shutdown command we are waiting for.
     *
     * @param shutdown The new shutdown command
     */
    void setShutdown(String shutdown);


    /**
     * Returns the parent class loader for this server component. If not explicitly set, returns the parent
     * class loader from {@link #getCatalina()}. If Catalina has not been set, returns the system class loader.
     *
     * @return the parent class loader
     */
    ClassLoader getParentClassLoader();


    /**
     * Set the parent class loader for this server.
     *
     * @param parent The new parent class loader
     */
    void setParentClassLoader(ClassLoader parent);


    /**
     * Returns the outer Catalina startup/shutdown component, if one has been set.
     *
     * @return the Catalina component, or {@code null} if not set
     */
    Catalina getCatalina();

    /**
     * Set the outer Catalina startup/shutdown component if present.
     *
     * @param catalina the outer Catalina component
     */
    void setCatalina(Catalina catalina);


    /**
     * Returns the configured base (instance) directory. If not set, the value from {@link #getCatalinaHome()}
     * is used. Note that home and base may be the same (and are by default).
     *
     * @return the configured base directory
     */
    File getCatalinaBase();

    /**
     * Set the configured base (instance) directory. Note that home and base may be the same (and are by default).
     *
     * @param catalinaBase the configured base directory
     */
    void setCatalinaBase(File catalinaBase);


    /**
     * Returns the configured home (binary) directory. Note that home and base may be the same (and are by default).
     *
     * @return the configured home directory
     */
    File getCatalinaHome();

    /**
     * Set the configured home (binary) directory. Note that home and base may be the same (and are by default).
     *
     * @param catalinaHome the configured home directory
     */
    void setCatalinaHome(File catalinaHome);


    /**
     * Get the utility thread count.
     *
     * @return the thread count
     */
    int getUtilityThreads();


    /**
     * Set the utility thread count.
     *
     * @param utilityThreads the new thread count
     */
    void setUtilityThreads(int utilityThreads);


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new Service to the set of defined Services.
     *
     * @param service The Service to be added
     */
    void addService(Service service);


    /**
     * Wait until a proper shutdown command is received, then return.
     */
    void await();


    /**
     * Find the specified Service
     *
     * @param name Name of the Service to be returned
     *
     * @return the specified Service, or <code>null</code> if none exists.
     */
    Service findService(String name);


    /**
     * Returns the array of all Services defined within this Server.
     *
     * @return the array of Services, or an empty array if none are defined
     */
    Service[] findServices();


    /**
     * Remove the specified Service from the set associated from this Server.
     *
     * @param service The Service to be removed
     */
    void removeService(Service service);


    /**
     * Returns the token required for performing operations on the associated JNDI naming context.
     *
     * @return the JNDI naming context token
     */
    Object getNamingToken();

    /**
     * Returns the utility executor managed by the server for background tasks.
     *
     * @return the utility executor service
     */
    ScheduledExecutorService getUtilityExecutor();

}
