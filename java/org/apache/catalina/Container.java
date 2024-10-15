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

import java.beans.PropertyChangeListener;
import java.io.File;

import javax.management.ObjectName;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;


/**
 * A <b>Container</b> is an object that can execute requests received from a client, and return responses based on those
 * requests. A Container may optionally support a pipeline of Valves that process the request in an order configured at
 * runtime, by implementing the <b>Pipeline</b> interface as well.
 * <p>
 * Containers will exist at several conceptual levels within Catalina. The following examples represent common cases:
 * <ul>
 * <li><b>Engine</b> - Representation of the entire Catalina servlet engine, most likely containing one or more
 * subcontainers that are either Host or Context implementations, or other custom groups.
 * <li><b>Host</b> - Representation of a virtual host containing a number of Contexts.
 * <li><b>Context</b> - Representation of a single ServletContext, which will typically contain one or more Wrappers for
 * the supported servlets.
 * <li><b>Wrapper</b> - Representation of an individual servlet definition.
 * </ul>
 * A given deployment of Catalina need not include Containers at all of the levels described above. For example, an
 * administration application embedded within a network device (such as a router) might only contain a single Context
 * and a few Wrappers, or even a single Wrapper if the application is relatively small. Therefore, Container
 * implementations need to be designed so that they will operate correctly in the absence of parent Containers in a
 * given deployment.
 * <p>
 * A Container may also be associated with a number of support components that provide functionality which might be
 * shared (by attaching it to a parent Container) or individually customized. The following support components are
 * currently recognized:
 * <ul>
 * <li><b>Loader</b> - Class loader to use for integrating new Java classes for this Container into the JVM in which
 * Catalina is running.
 * <li><b>Logger</b> - Implementation of the <code>log()</code> method signatures of the <code>ServletContext</code>
 * interface.
 * <li><b>Manager</b> - Manager for the pool of Sessions associated with this Container.
 * <li><b>Realm</b> - Read-only interface to a security domain, for authenticating user identities and their
 * corresponding roles.
 * <li><b>Resources</b> - JNDI directory context enabling access to static resources, enabling custom linkages to
 * existing server components when Catalina is embedded in a larger server.
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public interface Container extends Lifecycle {


    // ----------------------------------------------------- Manifest Constants


    /**
     * The ContainerEvent event type sent when a child container is added by <code>addChild()</code>.
     */
    String ADD_CHILD_EVENT = "addChild";


    /**
     * The ContainerEvent event type sent when a valve is added by <code>addValve()</code>, if this Container supports
     * pipelines.
     */
    String ADD_VALVE_EVENT = "addValve";


    /**
     * The ContainerEvent event type sent when a child container is removed by <code>removeChild()</code>.
     */
    String REMOVE_CHILD_EVENT = "removeChild";


    /**
     * The ContainerEvent event type sent when a valve is removed by <code>removeValve()</code>, if this Container
     * supports pipelines.
     */
    String REMOVE_VALVE_EVENT = "removeValve";


    // ------------------------------------------------------------- Properties

    /**
     * Obtain the log to which events for this container should be logged.
     *
     * @return The Logger with which this Container is associated. If there is no associated Logger, return the Logger
     *             associated with the parent Container (if any); otherwise return <code>null</code>.
     */
    Log getLogger();


    /**
     * Return the logger name that the container will use.
     *
     * @return the abbreviated name of this container for logging messages
     */
    String getLogName();


    /**
     * Obtain the JMX name for this container.
     *
     * @return the JMX name associated with this container.
     */
    ObjectName getObjectName();


    /**
     * Obtain the JMX domain under which this container will be / has been registered.
     *
     * @return The JMX domain name
     */
    String getDomain();


    /**
     * Calculate the key properties string to be added to an object's {@link ObjectName} to indicate that it is
     * associated with this container.
     *
     * @return A string suitable for appending to the ObjectName
     */
    String getMBeanKeyProperties();


    /**
     * Return the Pipeline object that manages the Valves associated with this Container.
     *
     * @return The Pipeline
     */
    Pipeline getPipeline();


    /**
     * Get the Cluster for this container.
     *
     * @return The Cluster with which this Container is associated. If there is no associated Cluster, return the
     *             Cluster associated with our parent Container (if any); otherwise return <code>null</code>.
     */
    Cluster getCluster();


    /**
     * Set the Cluster with which this Container is associated.
     *
     * @param cluster the Cluster with which this Container is associated.
     */
    void setCluster(Cluster cluster);


    /**
     * Get the delay between the invocation of the backgroundProcess method on this container and its children. Child
     * containers will not be invoked if their delay value is positive (which would mean they are using their own
     * thread). Setting this to a positive value will cause a thread to be spawned. After waiting the specified amount
     * of time, the thread will invoke the {@link #backgroundProcess()} method on this container and all children with
     * non-positive delay values.
     *
     * @return The delay between the invocation of the backgroundProcess method on this container and its children. A
     *             non-positive value indicates that background processing will be managed by the parent.
     */
    int getBackgroundProcessorDelay();


    /**
     * Set the delay between the invocation of the execute method on this container and its children.
     *
     * @param delay The delay in seconds between the invocation of backgroundProcess methods
     */
    void setBackgroundProcessorDelay(int delay);


    /**
     * Return a name string (suitable for use by humans) that describes this Container. Within the set of child
     * containers belonging to a particular parent, Container names must be unique.
     *
     * @return The human readable name of this container.
     */
    String getName();


    /**
     * Set a name string (suitable for use by humans) that describes this Container. Within the set of child containers
     * belonging to a particular parent, Container names must be unique.
     *
     * @param name New name of this container
     *
     * @exception IllegalStateException if this Container has already been added to the children of a parent Container
     *                                      (after which the name may not be changed)
     */
    void setName(String name);


    /**
     * Get the parent container.
     *
     * @return Return the Container for which this Container is a child, if there is one. If there is no defined parent,
     *             return <code>null</code>.
     */
    Container getParent();


    /**
     * Set the parent Container to which this Container is being added as a child. This Container may refuse to become
     * attached to the specified Container by throwing an exception.
     *
     * @param container Container to which this Container is being added as a child
     *
     * @exception IllegalArgumentException if this Container refuses to become attached to the specified Container
     */
    void setParent(Container container);


    /**
     * Get the parent class loader.
     *
     * @return the parent class loader for this component. If not set, return
     *             {@link #getParent()}.{@link #getParentClassLoader()}. If no parent has been set, return the system
     *             class loader.
     */
    ClassLoader getParentClassLoader();


    /**
     * Set the parent class loader for this component. For {@link Context}s this call is meaningful only
     * <strong>before</strong> a Loader has been configured, and the specified value (if non-null) should be passed as
     * an argument to the class loader constructor.
     *
     * @param parent The new parent class loader
     */
    void setParentClassLoader(ClassLoader parent);


    /**
     * Obtain the Realm with which this Container is associated.
     *
     * @return The associated Realm; if there is no associated Realm, the Realm associated with the parent Container (if
     *             any); otherwise return <code>null</code>.
     */
    Realm getRealm();


    /**
     * Set the Realm with which this Container is associated.
     *
     * @param realm The newly associated Realm
     */
    void setRealm(Realm realm);


    /**
     * Find the configuration path where a configuration resource is located.
     *
     * @param container    The container
     * @param resourceName The resource file name
     *
     * @return the configuration path
     */
    static String getConfigPath(Container container, String resourceName) {
        StringBuilder result = new StringBuilder();
        Container host = null;
        Container engine = null;
        while (container != null) {
            if (container instanceof Host) {
                host = container;
            } else if (container instanceof Engine) {
                engine = container;
            }
            container = container.getParent();
        }
        if (host != null && ((Host) host).getXmlBase() != null) {
            result.append(((Host) host).getXmlBase()).append('/');
        } else {
            result.append("conf/");
            if (engine != null) {
                result.append(engine.getName()).append('/');
            }
            if (host != null) {
                result.append(host.getName()).append('/');
            }
        }
        result.append(resourceName);
        return result.toString();
    }


    /**
     * Return the Service to which this container belongs.
     *
     * @param container The container to start from
     *
     * @return the Service, or null if not found
     */
    static Service getService(Container container) {
        while (container != null && !(container instanceof Engine)) {
            container = container.getParent();
        }
        if (container == null) {
            return null;
        }
        return ((Engine) container).getService();
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Execute a periodic task, such as reloading, etc. This method will be invoked inside the classloading context of
     * this container. Unexpected throwables will be caught and logged.
     */
    void backgroundProcess();


    /**
     * Add a new child Container to those associated with this Container, if supported. Prior to adding this Container
     * to the set of children, the child's <code>setParent()</code> method must be called, with this Container as an
     * argument. This method may thrown an <code>IllegalArgumentException</code> if this Container chooses not to be
     * attached to the specified Container, in which case it is not added
     *
     * @param child New child Container to be added
     *
     * @exception IllegalArgumentException if this exception is thrown by the <code>setParent()</code> method of the
     *                                         child Container
     * @exception IllegalArgumentException if the new child does not have a name unique from that of existing children
     *                                         of this Container
     * @exception IllegalStateException    if this Container does not support child Containers
     */
    void addChild(Container child);


    /**
     * Add a container event listener to this component.
     *
     * @param listener The listener to add
     */
    void addContainerListener(ContainerListener listener);


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * Obtain a child Container by name.
     *
     * @param name Name of the child Container to be retrieved
     *
     * @return The child Container with the given name or <code>null</code> if no such child exists.
     */
    Container findChild(String name);


    /**
     * Obtain the child Containers associated with this Container.
     *
     * @return An array containing all children of this container. If this Container has no children, a zero-length
     *             array is returned.
     */
    Container[] findChildren();


    /**
     * Obtain the container listeners associated with this Container.
     *
     * @return An array containing the container listeners associated with this Container. If this Container has no
     *             registered container listeners, a zero-length array is returned.
     */
    ContainerListener[] findContainerListeners();


    /**
     * Remove an existing child Container from association with this parent Container.
     *
     * @param child Existing child Container to be removed
     */
    void removeChild(Container child);


    /**
     * Remove a container event listener from this component.
     *
     * @param listener The listener to remove
     */
    void removeContainerListener(ContainerListener listener);


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    void removePropertyChangeListener(PropertyChangeListener listener);


    /**
     * Notify all container event listeners that a particular event has occurred for this Container. The default
     * implementation performs this notification synchronously using the calling thread.
     *
     * @param type Event type
     * @param data Event data
     */
    void fireContainerEvent(String type, Object data);


    /**
     * Log a request/response that was destined for this container but has been handled earlier in the processing chain
     * so that the request/response still appears in the correct access logs.
     *
     * @param request    Request (associated with the response) to log
     * @param response   Response (associated with the request) to log
     * @param time       Time taken to process the request/response in milliseconds (use 0 if not known)
     * @param useDefault Flag that indicates that the request/response should be logged in the engine's default access
     *                       log
     */
    void logAccess(Request request, Response response, long time, boolean useDefault);


    /**
     * Obtain the AccessLog to use to log a request/response that is destined for this container. This is typically used
     * when the request/response was handled (and rejected) earlier in the processing chain so that the request/response
     * still appears in the correct access logs.
     *
     * @return The AccessLog to use for a request/response destined for this container
     */
    AccessLog getAccessLog();


    /**
     * Obtain the number of threads available for starting and stopping any children associated with this container.
     * This allows start/stop calls to children to be processed in parallel.
     *
     * @return The currently configured number of threads used to start/stop children associated with this container
     */
    int getStartStopThreads();


    /**
     * Sets the number of threads available for starting and stopping any children associated with this container. This
     * allows start/stop calls to children to be processed in parallel.
     *
     * @param startStopThreads The new number of threads to be used
     */
    void setStartStopThreads(int startStopThreads);


    /**
     * Obtain the location of CATALINA_BASE.
     *
     * @return The location of CATALINA_BASE.
     */
    File getCatalinaBase();


    /**
     * Obtain the location of CATALINA_HOME.
     *
     * @return The location of CATALINA_HOME.
     */
    File getCatalinaHome();
}
