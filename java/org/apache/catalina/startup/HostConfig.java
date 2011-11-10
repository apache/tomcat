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
package org.apache.catalina.startup;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.IOTools;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;


/**
 * Startup event listener for a <b>Host</b> that configures the properties
 * of that Host, and the associated defined contexts.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Id$
 */
public class HostConfig
    implements LifecycleListener {

    private static final Log log = LogFactory.getLog( HostConfig.class );

    // ----------------------------------------------------- Instance Variables


    /**
     * Config base.
     */
    protected File configBase = null;


    /**
     * The Java class name of the Context configuration class we should use.
     */
    protected String configClass = "org.apache.catalina.startup.ContextConfig";


    /**
     * The Java class name of the Context implementation we should use.
     */
    protected String contextClass = "org.apache.catalina.core.StandardContext";


    /**
     * The Host we are associated with.
     */
    protected Host host = null;


    /**
     * The JMX ObjectName of this component.
     */
    protected ObjectName oname = null;


    /**
     * The string resources for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * Should we deploy XML Context config files packaged with WAR files and
     * directories?
     */
    protected boolean deployXML = false;


    /**
     * Should XML files be copied to $CATALINA_BASE/conf/<engine>/<host> by
     * default when a web application is deployed?
     */
    protected boolean copyXML = false;


    /**
     * Should we unpack WAR files when auto-deploying applications in the
     * <code>appBase</code> directory?
     */
    protected boolean unpackWARs = false;


    /**
     * Map of deployed applications.
     */
    protected Map<String, DeployedApplication> deployed =
        new ConcurrentHashMap<String, DeployedApplication>();


    /**
     * List of applications which are being serviced, and shouldn't be
     * deployed/undeployed/redeployed at the moment.
     */
    protected ArrayList<String> serviced = new ArrayList<String>();


    /**
     * The <code>Digester</code> instance used to parse context descriptors.
     */
    protected static Digester digester = createDigester();

    /**
     * The list of Wars in the appBase to be ignored because they are invalid
     * (e.g. contain /../ sequences).
     */
    protected Set<String> invalidWars = new HashSet<String>();

    // ------------------------------------------------------------- Properties


    /**
     * Return the Context configuration class name.
     */
    public String getConfigClass() {

        return (this.configClass);

    }


    /**
     * Set the Context configuration class name.
     *
     * @param configClass The new Context configuration class name.
     */
    public void setConfigClass(String configClass) {

        this.configClass = configClass;

    }


    /**
     * Return the Context implementation class name.
     */
    public String getContextClass() {

        return (this.contextClass);

    }


    /**
     * Set the Context implementation class name.
     *
     * @param contextClass The new Context implementation class name.
     */
    public void setContextClass(String contextClass) {

        this.contextClass = contextClass;

    }


    /**
     * Return the deploy XML config file flag for this component.
     */
    public boolean isDeployXML() {

        return (this.deployXML);

    }


    /**
     * Set the deploy XML config file flag for this component.
     *
     * @param deployXML The new deploy XML flag
     */
    public void setDeployXML(boolean deployXML) {

        this.deployXML= deployXML;

    }


    /**
     * Return the copy XML config file flag for this component.
     */
    public boolean isCopyXML() {

        return (this.copyXML);

    }


    /**
     * Set the copy XML config file flag for this component.
     *
     * @param copyXML The new copy XML flag
     */
    public void setCopyXML(boolean copyXML) {

        this.copyXML= copyXML;

    }


    /**
     * Return the unpack WARs flag.
     */
    public boolean isUnpackWARs() {

        return (this.unpackWARs);

    }


    /**
     * Set the unpack WARs flag.
     *
     * @param unpackWARs The new unpack WARs flag
     */
    public void setUnpackWARs(boolean unpackWARs) {

        this.unpackWARs = unpackWARs;

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Process the START event for an associated Host.
     *
     * @param event The lifecycle event that has occurred
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        if (event.getType().equals(Lifecycle.PERIODIC_EVENT))
            check();

        // Identify the host we are associated with
        try {
            host = (Host) event.getLifecycle();
            if (host instanceof StandardHost) {
                setCopyXML(((StandardHost) host).isCopyXML());
                setDeployXML(((StandardHost) host).isDeployXML());
                setUnpackWARs(((StandardHost) host).isUnpackWARs());
            }
        } catch (ClassCastException e) {
            log.error(sm.getString("hostConfig.cce", event.getLifecycle()), e);
            return;
        }

        // Process the event that has occurred
        if (event.getType().equals(Lifecycle.START_EVENT))
            start();
        else if (event.getType().equals(Lifecycle.STOP_EVENT))
            stop();

    }


    /**
     * Add a serviced application to the list.
     */
    public synchronized void addServiced(String name) {
        serviced.add(name);
    }


    /**
     * Is application serviced ?
     * @return state of the application
     */
    public synchronized boolean isServiced(String name) {
        return (serviced.contains(name));
    }


    /**
     * Removed a serviced application from the list.
     */
    public synchronized void removeServiced(String name) {
        serviced.remove(name);
    }


    /**
     * Get the instant where an application was deployed.
     * @return 0L if no application with that name is deployed, or the instant
     * on which the application was deployed
     */
    public long getDeploymentTime(String name) {
        DeployedApplication app = deployed.get(name);
        if (app == null) {
            return 0L;
        }

        return app.timestamp;
    }


    /**
     * Has the specified application been deployed? Note applications defined
     * in server.xml will not have been deployed.
     * @return <code>true</code> if the application has been deployed and
     * <code>false</code> if the application has not been deployed or does not
     * exist
     */
    public boolean isDeployed(String name) {
        DeployedApplication app = deployed.get(name);
        if (app == null) {
            return false;
        }

        return true;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Create the digester which will be used to parse context config files.
     */
    protected static Digester createDigester() {
        Digester digester = new Digester();
        digester.setValidating(false);
        // Add object creation rule
        digester.addObjectCreate("Context", "org.apache.catalina.core.StandardContext",
            "className");
        // Set the properties on that object (it doesn't matter if extra
        // properties are set)
        digester.addSetProperties("Context");
        return (digester);
    }

    protected File returnCanonicalPath(String path) {
        File file = new File(path);
        File base = new File(System.getProperty(Globals.CATALINA_BASE_PROP));
        if (!file.isAbsolute())
            file = new File(base,path);
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file;
        }
    }


    /**
     * Return a File object representing the "configuration root" directory
     * for our associated Host.
     */
    protected File configBase() {

        if (configBase != null) {
            return configBase;
        }

        if (host.getXmlBase()!=null) {
            configBase = returnCanonicalPath(host.getXmlBase());
        } else {
            StringBuilder xmlDir = new StringBuilder("conf");
            Container parent = host.getParent();
            if (parent instanceof Engine) {
                xmlDir.append('/');
                xmlDir.append(parent.getName());
            }
            xmlDir.append('/');
            xmlDir.append(host.getName());
            configBase = returnCanonicalPath(xmlDir.toString());
        }
        return (configBase);

    }

    /**
     * Get the name of the configBase.
     * For use with JMX management.
     */
    public String getConfigBaseName() {
        return configBase().getAbsolutePath();
    }


    /**
     * Deploy applications for any directories or WAR files that are found
     * in our "application root" directory.
     */
    protected void deployApps() {

        File appBase = host.getAppBaseFile();
        File configBase = configBase();
        String[] filteredAppPaths = filterAppPaths(appBase.list());
        // Deploy XML descriptors from configBase
        deployDescriptors(configBase, configBase.list());
        // Deploy WARs
        deployWARs(appBase, filteredAppPaths);
        // Deploy expanded folders
        deployDirectories(appBase, filteredAppPaths);

    }


    /**
     * Filter the list of application file paths to remove those that match
     * the regular expression defined by {@link Host#getDeployIgnore()}.
     *
     * @param unfilteredAppPaths    The list of application paths to filtert
     *
     * @return  The filtered list of application paths
     */
    protected String[] filterAppPaths(String[] unfilteredAppPaths) {
        Pattern filter = host.getDeployIgnorePattern();
        if (filter == null) {
            return unfilteredAppPaths;
        }

        List<String> filteredList = new ArrayList<String>();
        Matcher matcher = null;
        for (String appPath : unfilteredAppPaths) {
            if (matcher == null) {
                matcher = filter.matcher(appPath);
            } else {
                matcher.reset(appPath);
            }
            if (matcher.matches()) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("hostConfig.ignorePath", appPath));
                }
            } else {
                filteredList.add(appPath);
            }
        }
        return filteredList.toArray(new String[filteredList.size()]);
    }


    /**
     * Deploy applications for any directories or WAR files that are found
     * in our "application root" directory.
     */
    protected void deployApps(String name) {

        File appBase = host.getAppBaseFile();
        File configBase = configBase();
        ContextName cn = new ContextName(name);
        String baseName = cn.getBaseName();

        if (deploymentExists(baseName)) {
            return;
        }

        // Deploy XML descriptor from configBase
        File xml = new File(configBase, baseName + ".xml");
        if (xml.exists()) {
            deployDescriptor(cn, xml);
            return;
        }
        // Deploy WAR
        File war = new File(appBase, baseName + ".war");
        if (war.exists()) {
            deployWAR(cn, war);
            return;
        }
        // Deploy expanded folder
        File dir = new File(appBase, baseName);
        if (dir.exists())
            deployDirectory(cn, dir);
    }


    /**
     * Deploy XML context descriptors.
     */
    protected void deployDescriptors(File configBase, String[] files) {

        if (files == null)
            return;

        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<Future<?>>();

        for (int i = 0; i < files.length; i++) {
            File contextXml = new File(configBase, files[i]);

            if (files[i].toLowerCase(Locale.ENGLISH).endsWith(".xml")) {
                ContextName cn = new ContextName(files[i]);

                if (isServiced(cn.getName()) || deploymentExists(cn.getName()))
                    continue;

                results.add(
                        es.submit(new DeployDescriptor(this, cn, contextXml)));
            }
        }

        for (Future<?> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployDescriptor.threaded.error"), e);
            }
        }
    }


    /**
     * @param cn
     * @param contextXml
     */
    protected void deployDescriptor(ContextName cn, File contextXml) {

        DeployedApplication deployedApp = new DeployedApplication(cn.getName());

        // Assume this is a configuration descriptor and deploy it
        if(log.isInfoEnabled()) {
            log.info(sm.getString("hostConfig.deployDescriptor",
                    contextXml.getAbsolutePath()));
        }

        Context context = null;
        try {
            synchronized (digester) {
                try {
                    context = (Context) digester.parse(contextXml);
                    if (context == null) {
                        log.error(sm.getString(
                                "hostConfig.deployDescriptor.error",
                                contextXml.getAbsolutePath()));
                        return;
                    }
                } finally {
                    digester.reset();
                }
            }

            Class<?> clazz = Class.forName(host.getConfigClass());
            LifecycleListener listener =
                (LifecycleListener) clazz.newInstance();
            context.addLifecycleListener(listener);

            context.setConfigFile(contextXml.toURI().toURL());
            context.setName(cn.getName());
            context.setPath(cn.getPath());
            context.setWebappVersion(cn.getVersion());
            // Add the associated docBase to the redeployed list if it's a WAR
            boolean isExternalWar = false;
            boolean isExternal = false;
            if (context.getDocBase() != null) {
                File docBase = new File(context.getDocBase());
                if (!docBase.isAbsolute()) {
                    docBase = new File(host.getAppBaseFile(), context.getDocBase());
                }
                // If external docBase, register .xml as redeploy first
                if (!docBase.getCanonicalPath().startsWith(
                        host.getAppBaseFile().getAbsolutePath() + File.separator)) {
                    isExternal = true;
                    deployedApp.redeployResources.put(
                            contextXml.getAbsolutePath(),
                            Long.valueOf(contextXml.lastModified()));
                    deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                            Long.valueOf(docBase.lastModified()));
                    if (docBase.getAbsolutePath().toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                        isExternalWar = true;
                    }
                } else {
                    log.warn(sm.getString("hostConfig.deployDescriptor.localDocBaseSpecified",
                             docBase));
                    // Ignore specified docBase
                    context.setDocBase(null);
                }
            }
            host.addChild(context);
            // Get paths for WAR and expanded WAR in appBase

            // default to appBase dir + name
            File expandedDocBase = new File(host.getAppBaseFile(), cn.getBaseName());
            if (context.getDocBase() != null) {
                // first assume docBase is absolute
                expandedDocBase = new File(context.getDocBase());
                if (!expandedDocBase.isAbsolute()) {
                    // if docBase specified and relative, it must be relative to appBase
                    expandedDocBase = new File(host.getAppBaseFile(), context.getDocBase());
                }
            }
            // Add the eventual unpacked WAR and all the resources which will be
            // watched inside it
            if (isExternalWar && unpackWARs) {
                deployedApp.redeployResources.put(expandedDocBase.getAbsolutePath(),
                        Long.valueOf(expandedDocBase.lastModified()));
                deployedApp.redeployResources.put(contextXml.getAbsolutePath(),
                        Long.valueOf(contextXml.lastModified()));
                addWatchedResources(deployedApp, expandedDocBase.getAbsolutePath(), context);
            } else {
                // Find an existing matching war and expanded folder
                if (!isExternal) {
                    File warDocBase = new File(expandedDocBase.getAbsolutePath() + ".war");
                    if (warDocBase.exists()) {
                        deployedApp.redeployResources.put(warDocBase.getAbsolutePath(),
                                Long.valueOf(warDocBase.lastModified()));
                    }
                }
                if (expandedDocBase.exists()) {
                    deployedApp.redeployResources.put(expandedDocBase.getAbsolutePath(),
                            Long.valueOf(expandedDocBase.lastModified()));
                    addWatchedResources(deployedApp,
                            expandedDocBase.getAbsolutePath(), context);
                } else {
                    addWatchedResources(deployedApp, null, context);
                }
                // Add the context XML to the list of files which should trigger a redeployment
                if (!isExternal) {
                    deployedApp.redeployResources.put(
                            contextXml.getAbsolutePath(),
                            Long.valueOf(contextXml.lastModified()));
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployDescriptor.error",
                                   contextXml.getAbsolutePath()), t);
        }

        if (context != null && host.findChild(context.getName()) != null) {
            deployed.put(context.getName(), deployedApp);
        }
    }


    /**
     * Deploy WAR files.
     */
    protected void deployWARs(File appBase, String[] files) {

        if (files == null)
            return;

        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<Future<?>>();

        for (int i = 0; i < files.length; i++) {

            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File war = new File(appBase, files[i]);
            if (files[i].toLowerCase(Locale.ENGLISH).endsWith(".war") && war.isFile()
                    && !invalidWars.contains(files[i]) ) {

                ContextName cn = new ContextName(files[i]);

                // Check for WARs with /../ /./ or similar sequences in the name
                if (!validateContextPath(appBase, cn.getBaseName())) {
                    log.error(sm.getString(
                            "hostConfig.illegalWarName", files[i]));
                    invalidWars.add(files[i]);
                    continue;
                }

                if (isServiced(cn.getName()) || deploymentExists(cn.getName()))
                    continue;

                results.add(es.submit(new DeployWar(this, cn, war)));
            }
        }

        for (Future<?> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployWar.threaded.error"), e);
            }
        }
    }


    private boolean validateContextPath(File appBase, String contextPath) {
        // More complicated than the ideal as the canonical path may or may
        // not end with File.separator for a directory

        StringBuilder docBase;
        String canonicalDocBase = null;

        try {
            String canonicalAppBase = appBase.getCanonicalPath();
            docBase = new StringBuilder(canonicalAppBase);
            if (canonicalAppBase.endsWith(File.separator)) {
                docBase.append(contextPath.substring(1).replace(
                        '/', File.separatorChar));
            } else {
                docBase.append(contextPath.replace('/', File.separatorChar));
            }
            // At this point docBase should be canonical but will not end
            // with File.separator

            canonicalDocBase =
                (new File(docBase.toString())).getCanonicalPath();

            // If the canonicalDocBase ends with File.separator, add one to
            // docBase before they are compared
            if (canonicalDocBase.endsWith(File.separator)) {
                docBase.append(File.separator);
            }
        } catch (IOException ioe) {
            return false;
        }

        // Compare the two. If they are not the same, the contextPath must
        // have /../ like sequences in it
        return canonicalDocBase.equals(docBase.toString());
    }

    /**
     * @param cn
     * @param war
     */
    protected void deployWAR(ContextName cn, File war) {

        // Checking for a nested /META-INF/context.xml
        JarFile jar = null;
        JarEntry entry = null;
        InputStream istream = null;
        BufferedOutputStream ostream = null;
        File xml;
        if (copyXML) {
            xml = new File(configBase(), cn.getBaseName() + ".xml");
        } else {
            xml = new File(host.getAppBaseFile(),
                    cn.getBaseName() + "/META-INF/context.xml");
        }
        boolean xmlInWar = false;

        if (deployXML && !xml.exists()) {
            try {
                jar = new JarFile(war);
                entry = jar.getJarEntry(Constants.ApplicationContextXml);
                if (entry != null) {
                    xmlInWar = true;
                }
                if ((copyXML || unpackWARs) && xmlInWar) {
                    istream = jar.getInputStream(entry);

                    ostream =
                        new BufferedOutputStream
                        (new FileOutputStream(xml), 1024);
                    byte buffer[] = new byte[1024];
                    while (true) {
                        int n = istream.read(buffer);
                        if (n < 0) {
                            break;
                        }
                        ostream.write(buffer, 0, n);
                    }
                    ostream.flush();
                    ostream.close();
                    ostream = null;
                    istream.close();
                    istream = null;
                }
            } catch (IOException e) {
                /* Ignore */
            } finally {
                if (ostream != null) {
                    try {
                        ostream.close();
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    }
                    ostream = null;
                }
                if (istream != null) {
                    try {
                        istream.close();
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    }
                    istream = null;
                }
                entry = null;
                if (jar != null) {
                    try {
                        jar.close();
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    }
                    jar = null;
                }
            }
        }

        DeployedApplication deployedApp = new DeployedApplication(cn.getName());

        // Deploy the application in this WAR file
        if(log.isInfoEnabled())
            log.info(sm.getString("hostConfig.deployWar",
                    war.getAbsolutePath()));

        try {
            Context context = null;
            if (deployXML && xml.exists()) {
                synchronized (digester) {
                    try {
                        context = (Context) digester.parse(xml);
                        if (context == null) {
                            log.error(sm.getString(
                                    "hostConfig.deployDescriptor.error",
                                    war.getAbsolutePath()));
                            return;
                        }
                    } finally {
                        digester.reset();
                    }
                }
                context.setConfigFile(xml.toURI().toURL());
            } else if (deployXML && xmlInWar) {
                synchronized (digester) {
                    try {
                        jar = new JarFile(war);
                        entry =
                            jar.getJarEntry(Constants.ApplicationContextXml);
                        istream = jar.getInputStream(entry);
                        context = (Context) digester.parse(istream);

                        if (context == null) {
                            log.error(sm.getString(
                                    "hostConfig.deployDescriptor.error",
                                    war.getAbsolutePath()));
                            return;
                        }
                        context.setConfigFile(new URL("jar:" +
                                war.toURI().toString() + "!/" +
                                Constants.ApplicationContextXml));
                    } finally {
                        if (istream != null) {
                            try {
                                istream.close();
                            } catch (IOException e) {
                                /* Ignore */
                            }
                            istream = null;
                        }
                        entry = null;
                        if (jar != null) {
                            try {
                                jar.close();
                            } catch (IOException e) {
                                /* Ignore */
                            }
                            jar = null;
                        }
                        digester.reset();
                    }
                }
            } else {
                context = (Context) Class.forName(contextClass).newInstance();
            }

            // Populate redeploy resources with the WAR file
            deployedApp.redeployResources.put
                (war.getAbsolutePath(), Long.valueOf(war.lastModified()));

            if (deployXML && xml.exists() && copyXML) {
                deployedApp.redeployResources.put(xml.getAbsolutePath(),
                        Long.valueOf(xml.lastModified()));
            }

            Class<?> clazz = Class.forName(host.getConfigClass());
            LifecycleListener listener =
                (LifecycleListener) clazz.newInstance();
            context.addLifecycleListener(listener);

            context.setName(cn.getName());
            context.setPath(cn.getPath());
            context.setWebappVersion(cn.getVersion());
            context.setDocBase(cn.getBaseName() + ".war");
            host.addChild(context);
            // If we're unpacking WARs, the docBase will be mutated after
            // starting the context
            if (unpackWARs && (context.getDocBase() != null)) {
                File docBase = new File(host.getAppBaseFile(), cn.getBaseName());
                deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        Long.valueOf(docBase.lastModified()));
                addWatchedResources(deployedApp, docBase.getAbsolutePath(),
                        context);
                if (deployXML && !copyXML && (xmlInWar || xml.exists())) {
                    deployedApp.redeployResources.put(xml.getAbsolutePath(),
                            Long.valueOf(xml.lastModified()));
                }
            } else {
                addWatchedResources(deployedApp, null, context);
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployWar.error",
                    war.getAbsolutePath()), t);
        }

        deployed.put(cn.getName(), deployedApp);
    }


    /**
     * Deploy directories.
     */
    protected void deployDirectories(File appBase, String[] files) {

        if (files == null)
            return;

        ExecutorService es = host.getStartStopExecutor();
        List<Future<?>> results = new ArrayList<Future<?>>();

        for (int i = 0; i < files.length; i++) {

            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File dir = new File(appBase, files[i]);
            if (dir.isDirectory()) {
                ContextName cn = new ContextName(files[i]);

                if (isServiced(cn.getName()) || deploymentExists(cn.getName()))
                    continue;

                results.add(es.submit(new DeployDirectory(this, cn, dir)));
            }
        }

        for (Future<?> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString(
                        "hostConfig.deployDir.threaded.error"), e);
            }
        }
    }


    /**
     * @param cn
     * @param dir
     */
    protected void deployDirectory(ContextName cn, File dir) {

        DeployedApplication deployedApp = new DeployedApplication(cn.getName());

        // Deploy the application in this directory
        if( log.isInfoEnabled() )
            log.info(sm.getString("hostConfig.deployDir",
                    dir.getAbsolutePath()));
        try {
            Context context = null;
            File xml = new File(dir, Constants.ApplicationContextXml);
            File xmlCopy = null;
            if (deployXML && xml.exists()) {
                synchronized (digester) {
                    try {
                        context = (Context) digester.parse(xml);
                        if (context == null) {
                            log.error(sm.getString(
                                    "hostConfig.deployDescriptor.error",
                                    xml));
                            return;
                        }
                    } finally {
                        digester.reset();
                    }
                }
                if (copyXML) {
                    xmlCopy = new File(configBase(), cn.getBaseName() + ".xml");
                    InputStream is = null;
                    OutputStream os = null;
                    try {
                        is = new FileInputStream(xml);
                        os = new FileOutputStream(xmlCopy);
                        IOTools.flow(is, os);
                        // Don't catch IOE - let the outer try/catch handle it
                    } finally {
                        try {
                            if (is != null) is.close();
                        } catch (IOException e){
                            // Ignore
                        }
                        try {
                            if (os != null) os.close();
                        } catch (IOException e){
                            // Ignore
                        }
                    }
                    context.setConfigFile(xmlCopy.toURI().toURL());
                } else {
                    context.setConfigFile(xml.toURI().toURL());
                }
            } else {
                context = (Context) Class.forName(contextClass).newInstance();
            }

            Class<?> clazz = Class.forName(host.getConfigClass());
            LifecycleListener listener =
                (LifecycleListener) clazz.newInstance();
            context.addLifecycleListener(listener);

            context.setName(cn.getName());
            context.setPath(cn.getPath());
            context.setWebappVersion(cn.getVersion());
            context.setDocBase(cn.getBaseName());
            host.addChild(context);
            deployedApp.redeployResources.put(dir.getAbsolutePath(),
                    Long.valueOf(dir.lastModified()));
            if (deployXML && xml.exists()) {
                if (xmlCopy == null) {
                    deployedApp.redeployResources.put(
                            xml.getAbsolutePath(),
                            Long.valueOf(xml.lastModified()));
                } else {
                    deployedApp.redeployResources.put(
                            xmlCopy.getAbsolutePath(),
                            Long.valueOf(xmlCopy.lastModified()));
                }
            }
            addWatchedResources(deployedApp, dir.getAbsolutePath(), context);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("hostConfig.deployDir.error",
                    dir.getAbsolutePath()), t);
        }

        deployed.put(cn.getName(), deployedApp);
    }


    /**
     * Check if a webapp is already deployed in this host.
     *
     * @param contextName of the context which will be checked
     */
    protected boolean deploymentExists(String contextName) {
        return (deployed.containsKey(contextName) ||
                (host.findChild(contextName) != null));
    }


    /**
     * Add watched resources to the specified Context.
     * @param app HostConfig deployed app
     * @param docBase web app docBase
     * @param context web application context
     */
    protected void addWatchedResources(DeployedApplication app, String docBase,
            Context context) {
        // FIXME: Feature idea. Add support for patterns (ex: WEB-INF/*,
        //        WEB-INF/*.xml), where we would only check if at least one
        //        resource is newer than app.timestamp
        File docBaseFile = null;
        if (docBase != null) {
            docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                docBaseFile = new File(host.getAppBaseFile(), docBase);
            }
        }
        String[] watchedResources = context.findWatchedResources();
        for (int i = 0; i < watchedResources.length; i++) {
            File resource = new File(watchedResources[i]);
            if (!resource.isAbsolute()) {
                if (docBase != null) {
                    resource = new File(docBaseFile, watchedResources[i]);
                } else {
                    if(log.isDebugEnabled())
                        log.debug("Ignoring non-existent WatchedResource '" +
                                resource.getAbsolutePath() + "'");
                    continue;
                }
            }
            if(log.isDebugEnabled())
                log.debug("Watching WatchedResource '" +
                        resource.getAbsolutePath() + "'");
            app.reloadResources.put(resource.getAbsolutePath(),
                    Long.valueOf(resource.lastModified()));
        }
    }


    /**
     * Check resources for redeployment and reloading.
     */
    protected synchronized void checkResources(DeployedApplication app) {
        String[] resources =
            app.redeployResources.keySet().toArray(new String[0]);
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);
            if (log.isDebugEnabled())
                log.debug("Checking context[" + app.name +
                        "] redeploy resource " + resource);
            if (resource.exists()) {
                long lastModified =
                    app.redeployResources.get(resources[i]).longValue();
                if ((!resource.isDirectory()) &&
                        resource.lastModified() > lastModified) {
                    // Undeploy application
                    if (log.isInfoEnabled())
                        log.info(sm.getString("hostConfig.undeploy", app.name));
                    Container context = host.findChild(app.name);
                    try {
                        host.removeChild(context);
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        log.warn(sm.getString
                                 ("hostConfig.context.remove", app.name), t);
                    }
                    // Delete other redeploy resources
                    for (int j = i + 1; j < resources.length; j++) {
                        try {
                            File current = new File(resources[j]);
                            current = current.getCanonicalFile();
                            if ((current.getAbsolutePath().startsWith(
                                    host.getAppBaseFile().getAbsolutePath() +
                                    File.separator))
                                    || (current.getAbsolutePath().startsWith(
                                            configBase().getAbsolutePath()))) {
                                if (log.isDebugEnabled())
                                    log.debug("Delete " + current);
                                ExpandWar.delete(current);
                            }
                        } catch (IOException e) {
                            log.warn(sm.getString
                                    ("hostConfig.canonicalizing", app.name), e);
                        }
                    }
                    deployed.remove(app.name);
                    return;
                }
            } else {
                // There is a chance the the resource was only missing
                // temporarily eg renamed during a text editor save
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    // Ignore
                }
                // Recheck the resource to see if it was really deleted
                if (resource.exists()) {
                    continue;
                }
                long lastModified =
                    app.redeployResources.get(resources[i]).longValue();
                if (lastModified == 0L) {
                    continue;
                }
                // Undeploy application
                if (log.isInfoEnabled())
                    log.info(sm.getString("hostConfig.undeploy", app.name));
                Container context = host.findChild(app.name);
                try {
                    host.removeChild(context);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.warn(sm.getString
                             ("hostConfig.context.remove", app.name), t);
                }
                // Delete all redeploy resources
                for (int j = i + 1; j < resources.length; j++) {
                    try {
                        File current = new File(resources[j]);
                        current = current.getCanonicalFile();
                        if ((current.getAbsolutePath().startsWith(
                                host.getAppBaseFile().getAbsolutePath() + File.separator))
                            || (current.getAbsolutePath().startsWith(
                                    configBase().getAbsolutePath()))) {
                            if (log.isDebugEnabled())
                                log.debug("Delete " + current);
                            ExpandWar.delete(current);
                        }
                    } catch (IOException e) {
                        log.warn(sm.getString
                                ("hostConfig.canonicalizing", app.name), e);
                    }
                }
                // Delete reload resources as well (to remove any remaining .xml
                // descriptor)
                String[] resources2 =
                    app.reloadResources.keySet().toArray(new String[0]);
                for (int j = 0; j < resources2.length; j++) {
                    try {
                        File current = new File(resources2[j]);
                        current = current.getCanonicalFile();
                        if ((current.getAbsolutePath().startsWith(
                                host.getAppBaseFile().getAbsolutePath() + File.separator))
                            || ((current.getAbsolutePath().startsWith(
                                    configBase().getAbsolutePath())
                                 && (current.getAbsolutePath().endsWith(".xml"))))) {
                            if (log.isDebugEnabled())
                                log.debug("Delete " + current);
                            ExpandWar.delete(current);
                        }
                    } catch (IOException e) {
                        log.warn(sm.getString
                                ("hostConfig.canonicalizing", app.name), e);
                    }
                }
                deployed.remove(app.name);
                return;
            }
        }
        resources = app.reloadResources.keySet().toArray(new String[0]);
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);
            if (log.isDebugEnabled())
                log.debug("Checking context[" + app.name +
                        "] reload resource " + resource);
            long lastModified =
                app.reloadResources.get(resources[i]).longValue();
            if ((!resource.exists() && lastModified != 0L)
                || (resource.lastModified() != lastModified)) {
                // Reload application
                if(log.isInfoEnabled())
                    log.info(sm.getString("hostConfig.reload", app.name));
                Container context = host.findChild(app.name);
                try {
                    // Might not have started if start failed last time
                    if (context.getState().isAvailable()) {
                        context.stop();
                    }
                } catch (Exception e) {
                    log.warn(sm.getString
                             ("hostConfig.context.restart", app.name), e);
                }
                // If the context was not started (for example an error
                // in web.xml) we'll still get to try to start
                try {
                    context.start();
                } catch (Exception e) {
                    log.warn(sm.getString
                             ("hostConfig.context.restart", app.name), e);
                }
                // Update times
                app.reloadResources.put(resources[i],
                        Long.valueOf(resource.lastModified()));
                app.timestamp = System.currentTimeMillis();
                return;
            }
        }
    }


    /**
     * Process a "start" event for this Host.
     */
    public void start() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.start"));

        try {
            ObjectName hostON = host.getObjectName();
            oname = new ObjectName
                (hostON.getDomain() + ":type=Deployer,host=" + host.getName());
            Registry.getRegistry(null, null).registerComponent
                (this, oname, this.getClass().getName());
        } catch (Exception e) {
            log.error(sm.getString("hostConfig.jmx.register", oname), e);
        }

        if (host.getCreateDirs()) {
            File[] dirs = new File[] {host.getAppBaseFile(),configBase()};
            for (int i=0; i<dirs.length; i++) {
                if (!dirs[i].mkdirs() && !dirs[i].isDirectory()) {
                    log.error(sm.getString("hostConfig.createDirs",dirs[i]));
                }
            }
        }

        if (!host.getAppBaseFile().isDirectory()) {
            log.error(sm.getString("hostConfig.appBase", host.getName(),
                    host.getAppBaseFile().getPath()));
            host.setDeployOnStartup(false);
            host.setAutoDeploy(false);
        }

        if (host.getDeployOnStartup())
            deployApps();

    }


    /**
     * Process a "stop" event for this Host.
     */
    public void stop() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.stop"));

        if (oname != null) {
            try {
                Registry.getRegistry(null, null).unregisterComponent(oname);
            } catch (Exception e) {
                log.error(sm.getString("hostConfig.jmx.unregister", oname), e);
            }
        }
        oname = null;
        configBase = null;

    }


    /**
     * Check status of all webapps.
     */
    protected void check() {

        if (host.getAutoDeploy()) {
            // Check for resources modification to trigger redeployment
            DeployedApplication[] apps =
                deployed.values().toArray(new DeployedApplication[0]);
            for (int i = 0; i < apps.length; i++) {
                if (!isServiced(apps[i].name))
                    checkResources(apps[i]);
            }
            // Hotdeploy applications
            deployApps();
        }

    }


    /**
     * Check status of a specific webapp, for use with stuff like management webapps.
     */
    public void check(String name) {
        DeployedApplication app = deployed.get(name);
        if (app != null) {
            checkResources(app);
        } else {
            deployApps(name);
        }
    }

    /**
     * Add a new Context to be managed by us.
     * Entry point for the admin webapp, and other JMX Context controllers.
     */
    public void manageApp(Context context)  {

        String contextName = context.getName();

        if (deployed.containsKey(contextName))
            return;

        DeployedApplication deployedApp = new DeployedApplication(contextName);

        // Add the associated docBase to the redeployed list if it's a WAR
        boolean isWar = false;
        if (context.getDocBase() != null) {
            File docBase = new File(context.getDocBase());
            if (!docBase.isAbsolute()) {
                docBase = new File(host.getAppBaseFile(), context.getDocBase());
            }
            deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                    Long.valueOf(docBase.lastModified()));
            if (docBase.getAbsolutePath().toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                isWar = true;
            }
        }
        host.addChild(context);
        // Add the eventual unpacked WAR and all the resources which will be
        // watched inside it
        if (isWar && unpackWARs) {
            File docBase = new File(host.getAppBaseFile(), context.getBaseName());
            deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        Long.valueOf(docBase.lastModified()));
            addWatchedResources(deployedApp, docBase.getAbsolutePath(), context);
        } else {
            addWatchedResources(deployedApp, null, context);
        }
        deployed.put(contextName, deployedApp);
    }

    /**
     * Remove a webapp from our control.
     * Entry point for the admin webapp, and other JMX Context controllers.
     */
    public void unmanageApp(String contextName) {
        if(isServiced(contextName)) {
            deployed.remove(contextName);
            host.removeChild(host.findChild(contextName));
        }
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * This class represents the state of a deployed application, as well as
     * the monitored resources.
     */
    protected static class DeployedApplication {
        public DeployedApplication(String name) {
            this.name = name;
        }

        /**
         * Application context path. The assertion is that
         * (host.getChild(name) != null).
         */
        public String name;

        /**
         * Any modification of the specified (static) resources will cause a
         * redeployment of the application. If any of the specified resources is
         * removed, the application will be undeployed. Typically, this will
         * contain resources like the context.xml file, a compressed WAR path.
         * The value is the last modification time.
         */
        public LinkedHashMap<String, Long> redeployResources =
            new LinkedHashMap<String, Long>();

        /**
         * Any modification of the specified (static) resources will cause a
         * reload of the application. This will typically contain resources
         * such as the web.xml of a webapp, but can be configured to contain
         * additional descriptors.
         * The value is the last modification time.
         */
        public HashMap<String, Long> reloadResources =
            new HashMap<String, Long>();

        /**
         * Instant where the application was last put in service.
         */
        public long timestamp = System.currentTimeMillis();
    }

    private static class DeployDescriptor implements Runnable {

        private HostConfig config;
        private ContextName cn;
        private File descriptor;

        public DeployDescriptor(HostConfig config, ContextName cn,
                File descriptor) {
            this.config = config;
            this.cn = cn;
            this.descriptor= descriptor;
        }

        @Override
        public void run() {
            config.deployDescriptor(cn, descriptor);
        }
    }

    private static class DeployWar implements Runnable {

        private HostConfig config;
        private ContextName cn;
        private File war;

        public DeployWar(HostConfig config, ContextName cn, File war) {
            this.config = config;
            this.cn = cn;
            this.war = war;
        }

        @Override
        public void run() {
            config.deployWAR(cn, war);
        }
    }

    private static class DeployDirectory implements Runnable {

        private HostConfig config;
        private ContextName cn;
        private File dir;

        public DeployDirectory(HostConfig config, ContextName cn, File dir) {
            this.config = config;
            this.cn = cn;
            this.dir = dir;
        }

        @Override
        public void run() {
            config.deployDirectory(cn, dir);
        }
    }
}
