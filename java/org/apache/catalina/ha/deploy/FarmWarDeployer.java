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

package org.apache.catalina.ha.deploy;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.ha.ClusterDeployer;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.tribes.Member;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;


/**
 * <p>
 * A farm war deployer is a class that is able to deploy/undeploy web
 * applications in WAR form within the cluster.
 * </p>
 * Any host can act as the admin, and will have three directories
 * <ul>
 * <li>deployDir - the directory where we watch for changes</li>
 * <li>applicationDir - the directory where we install applications</li>
 * <li>tempDir - a temporaryDirectory to store binary data when downloading a
 * war from the cluster</li>
 * </ul>
 * Currently we only support deployment of WAR files since they are easier to
 * send across the wire.
 * 
 * @author Filip Hanik
 * @author Peter Rossbach
 * @version $Revision$
 */
public class FarmWarDeployer extends ClusterListener
        implements ClusterDeployer, FileChangeListener {
    /*--Static Variables----------------------------------------*/
    private static final Log log = LogFactory.getLog(FarmWarDeployer.class);
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * The descriptive information about this implementation.
     */
    private static final String info = "FarmWarDeployer/1.2";

    /*--Instance Variables--------------------------------------*/
    protected boolean started = false; //default 5 seconds

    protected HashMap<String, FileMessageFactory> fileFactories =
        new HashMap<String, FileMessageFactory>();

    protected String deployDir;

    protected String tempDir;

    protected String watchDir;

    protected boolean watchEnabled = false;

    protected WarWatcher watcher = null;

    /**
     * Iteration count for background processing.
     */
    private int count = 0;

    /**
     * Frequency of the Farm watchDir check. Cluster wide deployment will be
     * done once for the specified amount of backgrondProcess calls (ie, the
     * lower the amount, the most often the checks will occur).
     */
    protected int processDeployFrequency = 2;

    /**
     * Path where context descriptors should be deployed.
     */
    protected File configBase = null;

    /**
     * The associated host.
     */
    protected Host host = null;

    /**
     * The host appBase.
     */
    protected File appBase = null;

    /**
     * MBean server.
     */
    protected MBeanServer mBeanServer = null;

    /**
     * The associated deployer ObjectName.
     */
    protected ObjectName oname = null;

    /*--Constructor---------------------------------------------*/
    public FarmWarDeployer() {
    }

    /**
     * Return descriptive information about this deployer implementation and the
     * corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (info);

    }

    /*--Logic---------------------------------------------------*/
    @Override
    public void start() throws Exception {
        if (started)
            return;
        Container hcontainer = getCluster().getContainer();
        if(!(hcontainer instanceof Host)) {
            log.error(sm.getString("farmWarDeployer.hostOnly"));
            return ;
        }
        host = (Host) hcontainer;
    
        // Check to correct engine and host setup
        Container econtainer = host.getParent();
        if(!(econtainer instanceof Engine)) {
            log.error(sm.getString("farmWarDeployer.hostParentEngine",
                    host.getName())); 
            return ;
        }
        Engine engine = (Engine) econtainer;
        String hostname = null;
        hostname = host.getName();
        try {
            oname = new ObjectName(engine.getName() + ":type=Deployer,host="
                    + hostname);
        } catch (Exception e) {
            log.error(sm.getString("farmWarDeployer.mbeanNameFail",
                    engine.getName(), hostname),e);
            return;
        }
        if (watchEnabled) {
            watcher = new WarWatcher(this, new File(getWatchDir()));
            if (log.isInfoEnabled()) {
                log.info(sm.getString(
                        "farmWarDeployer.watchDir", getWatchDir()));
            }
        }
         
        configBase = new File(
                System.getProperty(Globals.CATALINA_BASE_PROP), "conf");
        configBase = new File(configBase, engine.getName());
        configBase = new File(configBase, hostname);

        // Retrieve the MBean server
        mBeanServer = Registry.getRegistry(null, null).getMBeanServer();

        started = true;
        count = 0;

        getCluster().addClusterListener(this);

        if (log.isInfoEnabled())
            log.info(sm.getString("farmWarDeployer.started"));
    }

    /*
     * stop cluster wide deployments
     * 
     * @see org.apache.catalina.ha.ClusterDeployer#stop()
     */
    @Override
    public void stop() throws LifecycleException {
        started = false;
        getCluster().removeClusterListener(this);
        count = 0;
        if (watcher != null) {
            watcher.clear();
            watcher = null;

        }
        if (log.isInfoEnabled())
            log.info(sm.getString("farmWarDeployer.stopped"));
    }

    public void cleanDeployDir() {
        throw new java.lang.UnsupportedOperationException(sm.getString(
                "farmWarDeployer.notImplemented", "cleanDeployDir()"));
    }

    /**
     * Callback from the cluster, when a message is received, The cluster will
     * broadcast it invoking the messageReceived on the receiver.
     * 
     * @param msg
     *            ClusterMessage - the message received from the cluster
     */
    @Override
    public void messageReceived(ClusterMessage msg) {
        try {
            if (msg instanceof FileMessage) {
                FileMessage fmsg = (FileMessage) msg;
                if (log.isDebugEnabled())
                    log.debug(sm.getString("farmWarDeployer.msgRxDeploy",
                            fmsg.getContextPath(), fmsg.getFileName()));
                FileMessageFactory factory = getFactory(fmsg);
                // TODO correct second try after app is in service!
                if (factory.writeMessage(fmsg)) {
                    //last message received war file is completed
                    String name = factory.getFile().getName();
                    if (!name.endsWith(".war"))
                        name = name + ".war";
                    File deployable = new File(getDeployDir(), name);
                    try {
                        String path = fmsg.getContextPath();
                        if (!isServiced(path)) {
                            addServiced(path);
                            try {
                                remove(path);
                                if (!factory.getFile().renameTo(deployable)) {
                                    log.error(sm.getString(
                                            "farmWarDeployer.renameFail",
                                            factory.getFile(), deployable));
                                }
                                check(path);
                            } finally {
                                removeServiced(path);
                            }
                            if (log.isDebugEnabled())
                                log.debug(sm.getString(
                                        "farmWarDeployer.deployEnd", path));
                        } else
                            log.error(sm.getString(
                                    "farmWarDeployer.servicingDeploy", path,
                                    name));
                    } catch (Exception ex) {
                        log.error(ex);
                    } finally {
                        removeFactory(fmsg);
                    }
                }
            } else if (msg instanceof UndeployMessage) {
                try {
                    UndeployMessage umsg = (UndeployMessage) msg;
                    String path = umsg.getContextPath();
                    if (log.isDebugEnabled())
                        log.debug(sm.getString("farmWarDeployer.msgRxUndeploy",
                                path));
                    if (!isServiced(path)) {
                        addServiced(path);
                        try {
                            remove(path);
                        } finally {
                            removeServiced(path);
                        }
                        if (log.isDebugEnabled())
                            log.debug(sm.getString(
                                    "farmWarDeployer.undeployEnd", path));
                    } else
                        log.error(sm.getString(
                                "farmWarDeployer.servicingUneploy", path));
                } catch (Exception ex) {
                    log.error(ex);
                }
            }
        } catch (java.io.IOException x) {
            log.error(sm.getString("farmWarDeployer.msgIoe"), x);
        }
    }

    /**
     * create factory for all transported war files
     * 
     * @param msg
     * @return Factory for all app message (war files)
     * @throws java.io.FileNotFoundException
     * @throws java.io.IOException
     */
    public synchronized FileMessageFactory getFactory(FileMessage msg)
            throws java.io.FileNotFoundException, java.io.IOException {
        File tmpFile = new File(msg.getFileName());
        File writeToFile = new File(getTempDir(), tmpFile.getName());
        FileMessageFactory factory = fileFactories.get(msg.getFileName());
        if (factory == null) {
            factory = FileMessageFactory.getInstance(writeToFile, true);
            fileFactories.put(msg.getFileName(), factory);
        }
        return factory;
    }

    /**
     * Remove file (war) from messages)
     * 
     * @param msg
     */
    public void removeFactory(FileMessage msg) {
        fileFactories.remove(msg.getFileName());
    }

    /**
     * Before the cluster invokes messageReceived the cluster will ask the
     * receiver to accept or decline the message, In the future, when messages
     * get big, the accept method will only take a message header
     * 
     * @param msg
     *            ClusterMessage
     * @return boolean - returns true to indicate that messageReceived should be
     *         invoked. If false is returned, the messageReceived method will
     *         not be invoked.
     */
    @Override
    public boolean accept(ClusterMessage msg) {
        return (msg instanceof FileMessage) || (msg instanceof UndeployMessage);
    }

    /**
     * Install a new web application, whose web application archive is at the
     * specified URL, into this container and all the other members of the
     * cluster with the specified context path. A context path of "" (the empty
     * string) should be used for the root application for this container.
     * Otherwise, the context path must start with a slash.
     * <p>
     * If this application is successfully installed locally, a ContainerEvent
     * of type <code>INSTALL_EVENT</code> will be sent to all registered
     * listeners, with the newly created <code>Context</code> as an argument.
     * 
     * @param contextPath
     *            The context path to which this application should be installed
     *            (must be unique)
     * @param war
     *            A URL of type "jar:" that points to a WAR file, or type
     *            "file:" that points to an unpacked directory structure
     *            containing the web application to be installed
     * 
     * @exception IllegalArgumentException
     *                if the specified context path is malformed (it must be ""
     *                or start with a slash)
     * @exception IllegalStateException
     *                if the specified context path is already attached to an
     *                existing web application
     * @exception IOException
     *                if an input/output error was encountered during
     *                installation
     */
    @Override
    public void install(String contextPath, URL war) throws IOException {
        Member[] members = getCluster().getMembers();
        Member localMember = getCluster().getLocalMember();
        FileMessageFactory factory = FileMessageFactory.getInstance(new File(
                war.getFile()), false);
        FileMessage msg = new FileMessage(localMember, war.getFile(),
                contextPath);
        if(log.isDebugEnabled())
            log.debug(sm.getString("farmWarDeployer.sendStart", contextPath,
                    war));
        msg = factory.readMessage(msg);
        while (msg != null) {
            for (int i = 0; i < members.length; i++) {
                if (log.isDebugEnabled())
                    log.debug(sm.getString("farmWarDeployer.sendFragment",
                            contextPath, war, members[i]));
                getCluster().send(msg, members[i]);
            }
            msg = factory.readMessage(msg);
        }
        if(log.isDebugEnabled())
            log.debug(sm.getString(
                    "farmWarDeployer.sendEnd", contextPath, war));
    }

    /**
     * Remove an existing web application, attached to the specified context
     * path. If this application is successfully removed, a ContainerEvent of
     * type <code>REMOVE_EVENT</code> will be sent to all registered
     * listeners, with the removed <code>Context</code> as an argument.
     * Deletes the web application war file and/or directory if they exist in
     * the Host's appBase.
     * 
     * @param contextPath
     *            The context path of the application to be removed
     * @param undeploy
     *            boolean flag to remove web application from server
     * 
     * @exception IllegalArgumentException
     *                if the specified context path is malformed (it must be ""
     *                or start with a slash)
     * @exception IllegalArgumentException
     *                if the specified context path does not identify a
     *                currently installed web application
     * @exception IOException
     *                if an input/output error occurs during removal
     */
    @Override
    public void remove(String contextPath, boolean undeploy)
            throws IOException {
        if (log.isInfoEnabled())
            log.info(sm.getString("farmWarDeployer.removeStart", contextPath));
        Member localMember = getCluster().getLocalMember();
        UndeployMessage msg = new UndeployMessage(localMember, System
                .currentTimeMillis(), "Undeploy:" + contextPath + ":"
                + System.currentTimeMillis(), contextPath, undeploy);
        if (log.isDebugEnabled())
            log.debug(sm.getString("farmWarDeployer.removeTxMsg", contextPath));
        cluster.send(msg);
        // remove locally
        if (undeploy) {
            try {
                if (!isServiced(contextPath)) {
                    addServiced(contextPath);
                    try {
                        remove(contextPath);
                    } finally {
                        removeServiced(contextPath);
                    }
                } else
                    log.error(sm.getString("farmWarDeployer.removeFailRemote",
                            contextPath));

            } catch (Exception ex) {
                log.error(sm.getString("farmWarDeployer.removeFailLocal",
                        contextPath), ex);
            }
        }

    }

    /*
     * Modification from watchDir war detected!
     * 
     * @see org.apache.catalina.ha.deploy.FileChangeListener#fileModified(File)
     */
    @Override
    public void fileModified(File newWar) {
        try {
            File deployWar = new File(getDeployDir(), newWar.getName());
            copy(newWar, deployWar);
            String contextName = getContextName(deployWar);
            if (log.isInfoEnabled())
                log.info(sm.getString("farmWarDeployer.modInstall", contextName,
                        deployWar.getAbsolutePath()));
            try {
                remove(contextName, false);
            } catch (Exception x) {
                log.error(sm.getString("farmWarDeployer.modRemoveFail"), x);
            }
            install(contextName, deployWar.toURI().toURL());
        } catch (Exception x) {
            log.error(sm.getString("farmWarDeployer.modInstallFail"), x);
        }
    }

    /*
     * War remove from watchDir
     * 
     * @see org.apache.catalina.ha.deploy.FileChangeListener#fileRemoved(File)
     */
    @Override
    public void fileRemoved(File removeWar) {
        try {
            String contextName = getContextName(removeWar);
            if (log.isInfoEnabled())
                log.info(sm.getString("farmWarDeployer.removeLocal",
                        contextName));
            remove(contextName, true);
        } catch (Exception x) {
            log.error(sm.getString("farmWarDeployer.removeLocalFail"), x);
        }
    }

    /**
     * Create a context path from war
     * @param war War filename
     * @return '/filename' or if war name is ROOT.war context name is empty
     *         string '' 
     */
    protected String getContextName(File war) {
        String contextName = "/"
        + war.getName().substring(0,
                war.getName().lastIndexOf(".war"));
        if("/ROOT".equals(contextName))
            contextName= "" ;
        return contextName ;
    }
    
    /**
     * Return a File object representing the "application root" directory for
     * our associated Host.
     */
    protected File getAppBase() {

        if (appBase != null) {
            return appBase;
        }

        File file = new File(host.getAppBase());
        if (!file.isAbsolute())
            file = new File(System.getProperty(Globals.CATALINA_BASE_PROP), host
                    .getAppBase());
        try {
            appBase = file.getCanonicalFile();
        } catch (IOException e) {
            appBase = file;
        }
        return (appBase);

    }

    /**
     * Invoke the remove method on the deployer.
     */
    protected void remove(String path) throws Exception {
        // TODO Handle remove also work dir content !
        // Stop the context first to be nicer
        Context context = (Context) host.findChild(path);
        if (context != null) {
            if(log.isDebugEnabled())
                log.debug(sm.getString("farmWarDeployer.undeployLocal", path));
            context.stop();
            String baseName = context.getBaseName();
            File war = new File(getAppBase(), baseName + ".war");
            File dir = new File(getAppBase(), baseName);
            File xml = new File(configBase, baseName + ".xml");
            if (war.exists()) {
                if (!war.delete()) {
                    log.error(sm.getString("farmWarDeployer.deleteFail", war));
                }
            } else if (dir.exists()) {
                undeployDir(dir);
            } else {
                if (!xml.delete()) {
                    log.error(sm.getString("farmWarDeployer.deleteFail", xml));
                }
            }
            // Perform new deployment and remove internal HostConfig state
            check(path);
        }

    }

    /**
     * Delete the specified directory, including all of its contents and
     * subdirectories recursively.
     * 
     * @param dir
     *            File object representing the directory to be deleted
     */
    protected void undeployDir(File dir) {

        String files[] = dir.list();
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; i < files.length; i++) {
            File file = new File(dir, files[i]);
            if (file.isDirectory()) {
                undeployDir(file);
            } else {
                if (!file.delete()) {
                    log.error(sm.getString("farmWarDeployer.deleteFail", file));
                }
            }
        }
        if (!dir.delete()) {
            log.error(sm.getString("farmWarDeployer.deleteFail", dir));
        }
    }

    /*
     * Call watcher to check for deploy changes
     * 
     * @see org.apache.catalina.ha.ClusterDeployer#backgroundProcess()
     */
    @Override
    public void backgroundProcess() {
        if (started) {
            count = (count + 1) % processDeployFrequency;
            if (count == 0 && watchEnabled) {
                watcher.check();
            }
        }

    }

    /*--Deployer Operations ------------------------------------*/

    /**
     * Invoke the check method on the deployer.
     */
    protected void check(String name) throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "check", params, signature);
    }

    /**
     * Invoke the check method on the deployer.
     */
    protected boolean isServiced(String name) throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        Boolean result = (Boolean) mBeanServer.invoke(oname, "isServiced",
                params, signature);
        return result.booleanValue();
    }

    /**
     * Invoke the check method on the deployer.
     */
    protected void addServiced(String name) throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "addServiced", params, signature);
    }

    /**
     * Invoke the check method on the deployer.
     */
    protected void removeServiced(String name) throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "removeServiced", params, signature);
    }

    /*--Instance Getters/Setters--------------------------------*/
    @Override
    public boolean equals(Object listener) {
        return super.equals(listener);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public String getDeployDir() {
        return deployDir;
    }

    public void setDeployDir(String deployDir) {
        this.deployDir = deployDir;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public String getWatchDir() {
        return watchDir;
    }

    public void setWatchDir(String watchDir) {
        this.watchDir = watchDir;
    }

    public boolean isWatchEnabled() {
        return watchEnabled;
    }

    public boolean getWatchEnabled() {
        return watchEnabled;
    }

    public void setWatchEnabled(boolean watchEnabled) {
        this.watchEnabled = watchEnabled;
    }

    /**
     * Return the frequency of watcher checks.
     */
    public int getProcessDeployFrequency() {

        return (this.processDeployFrequency);

    }

    /**
     * Set the watcher checks frequency.
     * 
     * @param processExpiresFrequency
     *            the new manager checks frequency
     */
    public void setProcessDeployFrequency(int processExpiresFrequency) {

        if (processExpiresFrequency <= 0) {
            return;
        }
        this.processDeployFrequency = processExpiresFrequency;
    }

    /**
     * Copy a file to the specified temp directory.
     * @param from copy from temp
     * @param to   to host appBase directory
     * @return true, copy successful
     */
    protected boolean copy(File from, File to) {
        try {
            if (!to.exists()) {
                if (!to.createNewFile()) {
                    log.error(sm.getString("farmWarDeployer.fileNewFail", to));
                    return false;
                }
            }
            java.io.FileInputStream is = new java.io.FileInputStream(from);
            java.io.FileOutputStream os = new java.io.FileOutputStream(to,
                    false);
            byte[] buf = new byte[4096];
            while (true) {
                int len = is.read(buf);
                if (len < 0)
                    break;
                os.write(buf, 0, len);
            }
            is.close();
            os.close();
        } catch (IOException e) {
            log.error(sm.getString("farmWarDeployer.fileCopyFail",
                    from, to), e);
            return false;
        }
        return true;
    }

}
