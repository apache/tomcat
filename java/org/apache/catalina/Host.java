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

import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;


/**
 * A <b>Host</b> is a Container that represents a virtual host in the
 * Catalina servlet engine.  It is useful in the following types of scenarios:
 * <ul>
 * <li>You wish to use Interceptors that see every single request processed
 *     by this particular virtual host.
 * <li>You wish to run Catalina in with a standalone HTTP connector, but still
 *     want support for multiple virtual hosts.
 * </ul>
 * In general, you would not use a Host when deploying Catalina connected
 * to a web server (such as Apache), because the Connector will have
 * utilized the web server's facilities to determine which Context (or
 * perhaps even which Wrapper) should be utilized to process this request.
 * <p>
 * The parent Container attached to a Host is generally an Engine, but may
 * be some other implementation, or may be omitted if it is not necessary.
 * <p>
 * The child containers attached to a Host are generally implementations
 * of Context (representing an individual servlet context).
 *
 * @author Craig R. McClanahan
 */
public interface Host extends Container {


    // ----------------------------------------------------- Manifest Constants


    /**
     * The ContainerEvent event type sent when a new alias is added
     * by <code>addAlias()</code>.
     */
    public static final String ADD_ALIAS_EVENT = "addAlias";


    /**
     * The ContainerEvent event type sent when an old alias is removed
     * by <code>removeAlias()</code>.
     */
    public static final String REMOVE_ALIAS_EVENT = "removeAlias";


    // ------------------------------------------------------------- Properties


    /**
     * @return the XML root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     * If null, the base path defaults to
     * ${catalina.base}/conf/&lt;engine name&gt;/&lt;host name&gt; directory
     */
    public String getXmlBase();

    /**
     * Set the Xml root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     * If null, the base path defaults to
     * ${catalina.base}/conf/&lt;engine name&gt;/&lt;host name&gt; directory
     * @param xmlBase The new XML root
     */
    public void setXmlBase(String xmlBase);

    /**
     * @return the application root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     */
    public String getAppBase();


    /**
     * Set the application root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     *
     * @param appBase The new application root
     */
    public void setAppBase(String appBase);


    /**
     * @return the value of the auto deploy flag.  If true, it indicates that
     * this host's child webapps should be discovered and automatically
     * deployed dynamically.
     */
    public boolean getAutoDeploy();


    /**
     * Set the auto deploy flag value for this host.
     *
     * @param autoDeploy The new auto deploy flag
     */
    public void setAutoDeploy(boolean autoDeploy);


    /**
     * @return the Java class name of the context configuration class
     * for new web applications.
     */
    public String getConfigClass();


    /**
     * Set the Java class name of the context configuration class
     * for new web applications.
     *
     * @param configClass The new context configuration class
     */
    public void setConfigClass(String configClass);


    /**
     * @return the value of the deploy on startup flag.  If true, it indicates
     * that this host's child webapps should be discovered and automatically
     * deployed.
     */
    public boolean getDeployOnStartup();


    /**
     * Set the deploy on startup flag value for this host.
     *
     * @param deployOnStartup The new deploy on startup flag
     */
    public void setDeployOnStartup(boolean deployOnStartup);


    /**
     * @return the regular expression that defines the files and directories in
     * the host's appBase that will be ignored by the automatic deployment
     * process.
     */
    public String getDeployIgnore();


    /**
     * @return the compiled regular expression that defines the files and
     * directories in the host's appBase that will be ignored by the automatic
     * deployment process.
     */
    public Pattern getDeployIgnorePattern();


    /**
     * Set the regular expression that defines the files and directories in
     * the host's appBase that will be ignored by the automatic deployment
     * process.
     *
     * @param deployIgnore A regular expression matching file names
     */
    public void setDeployIgnore(String deployIgnore);


    /**
     * @return the executor that is used for starting and stopping contexts. This
     * is primarily for use by components deploying contexts that want to do
     * this in a multi-threaded manner.
     */
    public ExecutorService getStartStopExecutor();


    /**
     * Returns <code>true</code> if the Host will attempt to create directories for appBase and xmlBase
     * unless they already exist.
     * @return true if the Host will attempt to create directories
     */
    public boolean getCreateDirs();


    /**
     * Should the Host attempt to create directories for xmlBase and appBase
     * upon startup.
     *
     * @param createDirs The new value for this flag
     */
    public void setCreateDirs(boolean createDirs);


    /**
     * @return <code>true</code> of the Host is configured to automatically undeploy old
     * versions of applications deployed using parallel deployment. This only
     * takes effect is {@link #getAutoDeploy()} also returns <code>true</code>.
     */
    public boolean getUndeployOldVersions();


    /**
     * Set to <code>true</code> if the Host should automatically undeploy old versions of
     * applications deployed using parallel deployment. This only takes effect
     * if {@link #getAutoDeploy()} returns <code>true</code>.
     *
     * @param undeployOldVersions The new value for this flag
     */
    public void setUndeployOldVersions(boolean undeployOldVersions);


    // --------------------------------------------------------- Public Methods

    /**
     * Add an alias name that should be mapped to this same Host.
     *
     * @param alias The alias to be added
     */
    public void addAlias(String alias);


    /**
     * @return the set of alias names for this Host.  If none are defined,
     * a zero length array is returned.
     */
    public String[] findAliases();


    /**
     * Remove the specified alias name from the aliases for this Host.
     *
     * @param alias Alias name to be removed
     */
    public void removeAlias(String alias);
}
