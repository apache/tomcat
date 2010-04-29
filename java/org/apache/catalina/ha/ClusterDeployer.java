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

package org.apache.catalina.ha;

/**
 * A <b>ClusterDeployer</b> interface allows to plug in and out the
 * different deployment implementations
 *
 * @author Filip Hanik
 * @version $Id$
 */
import org.apache.catalina.LifecycleException;
import java.io.IOException;
import java.net.URL;
import org.apache.catalina.tribes.ChannelListener;

public interface ClusterDeployer extends ChannelListener {
    /**
     * Descriptive information about this component implementation.
     */
    public String info = "ClusterDeployer/1.0";
    /**
     * Start the cluster deployer, the owning container will invoke this
     * @throws Exception - if failure to start cluster
     */
    public void start() throws Exception;

    /**
     * Stops the cluster deployer, the owning container will invoke this
     * @throws LifecycleException
     */
    public void stop() throws LifecycleException;

    /**
     * Sets the deployer for this cluster deployer to use.
     * @param deployer Deployer
     */
    // FIXME
    //public void setDeployer(Deployer deployer);

    /**
     * Install a new web application, whose web application archive is at the
     * specified URL, into this container and all the other
     * members of the cluster with the specified context path.
     * A context path of "" (the empty string) should be used for the root
     * application for this container.  Otherwise, the context path must
     * start with a slash.
     * <p>
     * If this application is successfully installed locally, 
     * a ContainerEvent of type
     * <code>INSTALL_EVENT</code> will be sent to all registered listeners,
     * with the newly created <code>Context</code> as an argument.
     *
     * @param contextPath The context path to which this application should
     *  be installed (must be unique)
     * @param war A URL of type "jar:" that points to a WAR file, or type
     *  "file:" that points to an unpacked directory structure containing
     *  the web application to be installed
     *
     * @exception IllegalArgumentException if the specified context path
     *  is malformed (it must be "" or start with a slash)
     * @exception IllegalStateException if the specified context path
     *  is already attached to an existing web application
     * @exception IOException if an input/output error was encountered
     *  during installation
     */
    public void install(String contextPath, URL war) throws IOException;

    /**
     * Remove an existing web application, attached to the specified context
     * path.  If this application is successfully removed, a
     * ContainerEvent of type <code>REMOVE_EVENT</code> will be sent to all
     * registered listeners, with the removed <code>Context</code> as
     * an argument. Deletes the web application war file and/or directory
     * if they exist in the Host's appBase.
     *
     * @param contextPath The context path of the application to be removed
     * @param undeploy boolean flag to remove web application from server
     *
     * @exception IllegalArgumentException if the specified context path
     *  is malformed (it must be "" or start with a slash)
     * @exception IllegalArgumentException if the specified context path does
     *  not identify a currently installed web application
     * @exception IOException if an input/output error occurs during
     *  removal
     */
    public void remove(String contextPath, boolean undeploy) throws IOException;

    /**
     * call from container Background Process
     */
    public void backgroundProcess();
    
    /**
     * Returns the cluster the cluster deployer is associated with
     * @return CatalinaCluster
     */
    public CatalinaCluster getCluster();

    /**
     * Associates the cluster deployer with a cluster
     * @param cluster CatalinaCluster
     */
    public void setCluster(CatalinaCluster cluster);

}
