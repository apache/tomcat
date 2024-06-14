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
package org.apache.catalina.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.loader.WebappClassLoaderBase;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * Standard implementation of the <b>Host</b> interface. Each child container must be a Context implementation to
 * process the requests directed to a particular web application.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class StandardHost extends ContainerBase implements Host {

    private static final Log log = LogFactory.getLog(StandardHost.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardHost component with the default basic Valve.
     */
    public StandardHost() {

        super();
        pipeline.setBasic(new StandardHostValve());

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The set of aliases for this Host.
     */
    private String[] aliases = new String[0];

    private final Object aliasesLock = new Object();


    /**
     * The application root for this Host.
     */
    private String appBase = "webapps";
    private volatile File appBaseFile = null;


    /**
     * The legacy (Java EE) application root for this Host.
     */
    private String legacyAppBase = "webapps-javaee";
    private volatile File legacyAppBaseFile = null;


    /**
     * The XML root for this Host.
     */
    private String xmlBase = null;

    /**
     * host's default config path
     */
    private volatile File hostConfigBase = null;

    /**
     * The auto deploy flag for this Host.
     */
    private boolean autoDeploy = true;


    /**
     * The Java class name of the default context configuration class for deployed web applications.
     */
    private String configClass = "org.apache.catalina.startup.ContextConfig";


    /**
     * The Java class name of the default Context implementation class for deployed web applications.
     */
    private String contextClass = "org.apache.catalina.core.StandardContext";


    /**
     * The deploy on startup flag for this Host.
     */
    private boolean deployOnStartup = true;


    /**
     * deploy Context XML config files property.
     */
    private boolean deployXML = true;


    /**
     * Should XML files be copied to $CATALINA_BASE/conf/&lt;engine&gt;/&lt;host&gt; by default when a web application
     * is deployed?
     */
    private boolean copyXML = false;


    /**
     * The Java class name of the default error reporter implementation class for deployed web applications.
     */
    private String errorReportValveClass = "org.apache.catalina.valves.ErrorReportValve";


    /**
     * Unpack WARs property.
     */
    private boolean unpackWARs = true;


    /**
     * Work Directory base for applications.
     */
    private String workDir = null;


    /**
     * Should we create directories upon startup for appBase and xmlBase
     */
    private boolean createDirs = true;


    /**
     * Track the class loaders for the child web applications so memory leaks can be detected.
     */
    private final Map<ClassLoader,String> childClassLoaders = new WeakHashMap<>();


    /**
     * Any file or directory in {@link #appBase} that this pattern matches will be ignored by the automatic deployment
     * process (both {@link #deployOnStartup} and {@link #autoDeploy}).
     */
    private Pattern deployIgnore = null;


    private boolean undeployOldVersions = false;

    private boolean failCtxIfServletStartFails = false;


    // ------------------------------------------------------------- Properties

    @Override
    public boolean getUndeployOldVersions() {
        return undeployOldVersions;
    }


    @Override
    public void setUndeployOldVersions(boolean undeployOldVersions) {
        this.undeployOldVersions = undeployOldVersions;
    }


    @Override
    public ExecutorService getStartStopExecutor() {
        return startStopExecutor;
    }


    @Override
    public String getAppBase() {
        return this.appBase;
    }


    @Override
    public File getAppBaseFile() {

        if (appBaseFile != null) {
            return appBaseFile;
        }

        File file = new File(getAppBase());

        // If not absolute, make it absolute
        if (!file.isAbsolute()) {
            file = new File(getCatalinaBase(), file.getPath());
        }

        // Make it canonical if possible
        try {
            file = file.getCanonicalFile();
        } catch (IOException ioe) {
            // Ignore
        }

        this.appBaseFile = file;
        return file;
    }


    @Override
    public void setAppBase(String appBase) {
        if (appBase.trim().equals("")) {
            log.warn(sm.getString("standardHost.problematicAppBase", getName()));
        }
        String oldAppBase = this.appBase;
        this.appBase = appBase;
        support.firePropertyChange("appBase", oldAppBase, this.appBase);
        this.appBaseFile = null;
    }


    @Override
    public String getLegacyAppBase() {
        return this.legacyAppBase;
    }


    @Override
    public File getLegacyAppBaseFile() {
        if (legacyAppBaseFile != null) {
            return legacyAppBaseFile;
        }

        File file = new File(getLegacyAppBase());

        // If not absolute, make it absolute
        if (!file.isAbsolute()) {
            file = new File(getCatalinaBase(), file.getPath());
        }

        // Make it canonical if possible
        try {
            file = file.getCanonicalFile();
        } catch (IOException ioe) {
            // Ignore
        }

        this.legacyAppBaseFile = file;
        return file;
    }


    @Override
    public void setLegacyAppBase(String legacyAppBase) {
        if (legacyAppBase.trim().equals("")) {
            log.warn(sm.getString("standardHost.problematicLegacyAppBase", getName()));
        }
        String oldLegacyAppBase = this.legacyAppBase;
        this.legacyAppBase = legacyAppBase;
        support.firePropertyChange("legacyAppBase", oldLegacyAppBase, this.legacyAppBase);
        this.legacyAppBaseFile = null;
    }


    @Override
    public String getXmlBase() {
        return this.xmlBase;
    }


    @Override
    public void setXmlBase(String xmlBase) {
        String oldXmlBase = this.xmlBase;
        this.xmlBase = xmlBase;
        support.firePropertyChange("xmlBase", oldXmlBase, this.xmlBase);
    }


    @Override
    public File getConfigBaseFile() {
        if (hostConfigBase != null) {
            return hostConfigBase;
        }
        String path = null;
        if (getXmlBase() != null) {
            path = getXmlBase();
        } else {
            StringBuilder xmlDir = new StringBuilder("conf");
            Container parent = getParent();
            if (parent instanceof Engine) {
                xmlDir.append('/');
                xmlDir.append(parent.getName());
            }
            xmlDir.append('/');
            xmlDir.append(getName());
            path = xmlDir.toString();
        }
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(getCatalinaBase(), path);
        }
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {// ignore
        }
        this.hostConfigBase = file;
        return file;
    }


    /**
     * {@inheritDoc}
     * <p>
     * The default value for this implementation is {@code true}.
     */
    @Override
    public boolean getCreateDirs() {
        return createDirs;
    }

    @Override
    public void setCreateDirs(boolean createDirs) {
        this.createDirs = createDirs;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The default value for this implementation is {@code true}.
     */
    @Override
    public boolean getAutoDeploy() {
        return this.autoDeploy;
    }


    @Override
    public void setAutoDeploy(boolean autoDeploy) {

        boolean oldAutoDeploy = this.autoDeploy;
        this.autoDeploy = autoDeploy;
        support.firePropertyChange("autoDeploy", oldAutoDeploy, this.autoDeploy);

    }


    @Override
    public String getConfigClass() {
        return this.configClass;
    }


    @Override
    public void setConfigClass(String configClass) {

        String oldConfigClass = this.configClass;
        this.configClass = configClass;
        support.firePropertyChange("configClass", oldConfigClass, this.configClass);

    }


    /**
     * @return the Java class name of the Context implementation class for new web applications.
     */
    public String getContextClass() {
        return this.contextClass;
    }


    /**
     * Set the Java class name of the Context implementation class for new web applications.
     *
     * @param contextClass The new context implementation class
     */
    public void setContextClass(String contextClass) {

        String oldContextClass = this.contextClass;
        this.contextClass = contextClass;
        support.firePropertyChange("contextClass", oldContextClass, this.contextClass);

    }


    /**
     * {@inheritDoc}
     * <p>
     * The default value for this implementation is {@code true}.
     */
    @Override
    public boolean getDeployOnStartup() {
        return this.deployOnStartup;
    }


    @Override
    public void setDeployOnStartup(boolean deployOnStartup) {

        boolean oldDeployOnStartup = this.deployOnStartup;
        this.deployOnStartup = deployOnStartup;
        support.firePropertyChange("deployOnStartup", oldDeployOnStartup, this.deployOnStartup);

    }


    /**
     * @return <code>true</code> if XML context descriptors should be deployed.
     */
    public boolean isDeployXML() {
        return deployXML;
    }


    /**
     * Deploy XML Context config files flag mutator.
     *
     * @param deployXML <code>true</code> if context descriptors should be deployed
     */
    public void setDeployXML(boolean deployXML) {
        this.deployXML = deployXML;
    }


    /**
     * @return the copy XML config file flag for this component.
     */
    public boolean isCopyXML() {
        return this.copyXML;
    }


    /**
     * Set the copy XML config file flag for this component.
     *
     * @param copyXML The new copy XML flag
     */
    public void setCopyXML(boolean copyXML) {
        this.copyXML = copyXML;
    }


    /**
     * @return the Java class name of the error report valve class for new web applications.
     */
    public String getErrorReportValveClass() {
        return this.errorReportValveClass;
    }


    /**
     * Set the Java class name of the error report valve class for new web applications.
     *
     * @param errorReportValveClass The new error report valve class
     */
    public void setErrorReportValveClass(String errorReportValveClass) {

        String oldErrorReportValveClassClass = this.errorReportValveClass;
        this.errorReportValveClass = errorReportValveClass;
        support.firePropertyChange("errorReportValveClass", oldErrorReportValveClassClass, this.errorReportValveClass);

    }


    @Override
    public String getName() {
        return name;
    }


    @Override
    public void setName(String name) {

        if (name == null) {
            throw new IllegalArgumentException(sm.getString("standardHost.nullName"));
        }

        name = name.toLowerCase(Locale.ENGLISH); // Internally all names are lower case

        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);

    }


    /**
     * @return <code>true</code> if WARs should be unpacked on deployment.
     */
    public boolean isUnpackWARs() {
        return unpackWARs;
    }


    /**
     * Unpack WARs flag mutator.
     *
     * @param unpackWARs <code>true</code> to unpack WARs on deployment
     */
    public void setUnpackWARs(boolean unpackWARs) {
        this.unpackWARs = unpackWARs;
    }


    /**
     * @return host work directory base.
     */
    public String getWorkDir() {
        return workDir;
    }


    /**
     * Set host work directory base.
     *
     * @param workDir the new base work folder for this host
     */
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }


    @Override
    public String getDeployIgnore() {
        if (deployIgnore == null) {
            return null;
        }
        return this.deployIgnore.toString();
    }


    @Override
    public Pattern getDeployIgnorePattern() {
        return this.deployIgnore;
    }


    @Override
    public void setDeployIgnore(String deployIgnore) {
        String oldDeployIgnore;
        if (this.deployIgnore == null) {
            oldDeployIgnore = null;
        } else {
            oldDeployIgnore = this.deployIgnore.toString();
        }
        if (deployIgnore == null) {
            this.deployIgnore = null;
        } else {
            this.deployIgnore = Pattern.compile(deployIgnore);
        }
        support.firePropertyChange("deployIgnore", oldDeployIgnore, deployIgnore);
    }


    /**
     * @return <code>true</code> if a webapp start should fail if a Servlet startup fails
     */
    public boolean isFailCtxIfServletStartFails() {
        return failCtxIfServletStartFails;
    }


    /**
     * Change the behavior of Servlet startup errors on web application starts.
     *
     * @param failCtxIfServletStartFails <code>false</code> to ignore errors on Servlets which are stated when the web
     *                                       application starts
     */
    public void setFailCtxIfServletStartFails(boolean failCtxIfServletStartFails) {
        boolean oldFailCtxIfServletStartFails = this.failCtxIfServletStartFails;
        this.failCtxIfServletStartFails = failCtxIfServletStartFails;
        support.firePropertyChange("failCtxIfServletStartFails", oldFailCtxIfServletStartFails,
                failCtxIfServletStartFails);
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public void addAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        synchronized (aliasesLock) {
            // Skip duplicate aliases
            for (String s : aliases) {
                if (s.equals(alias)) {
                    return;
                }
            }
            // Add this alias to the list
            String newAliases[] = Arrays.copyOf(aliases, aliases.length + 1);
            newAliases[aliases.length] = alias;
            aliases = newAliases;
        }
        // Inform interested listeners
        fireContainerEvent(ADD_ALIAS_EVENT, alias);

    }


    /**
     * {@inheritDoc}
     * <p>
     * The child must be an implementation of <code>Context</code>.
     */
    @Override
    public void addChild(Container child) {

        if (!(child instanceof Context)) {
            throw new IllegalArgumentException(sm.getString("standardHost.notContext"));
        }

        child.addLifecycleListener(new MemoryLeakTrackingListener());

        // Avoid NPE for case where Context is defined in server.xml with only a
        // docBase
        Context context = (Context) child;
        if (context.getPath() == null) {
            ContextName cn = new ContextName(context.getDocBase(), true);
            context.setPath(cn.getPath());
        }

        super.addChild(child);

    }


    /**
     * Used to ensure the regardless of {@link Context} implementation, a record is kept of the class loader used every
     * time a context starts.
     */
    private class MemoryLeakTrackingListener implements LifecycleListener {
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (event.getType().equals(AFTER_START_EVENT)) {
                if (event.getSource() instanceof Context) {
                    Context context = ((Context) event.getSource());
                    childClassLoaders.put(context.getLoader().getClassLoader(),
                            context.getServletContext().getContextPath());
                }
            }
        }
    }


    /**
     * Attempt to identify the contexts that have a class loader memory leak. This is usually triggered on context
     * reload. Note: This method attempts to force a full garbage collection. This should be used with extreme caution
     * on a production system.
     *
     * @return a list of possibly leaking contexts
     */
    public String[] findReloadedContextMemoryLeaks() {

        System.gc();

        List<String> result = new ArrayList<>();

        for (Map.Entry<ClassLoader,String> entry : childClassLoaders.entrySet()) {
            ClassLoader cl = entry.getKey();
            if (cl instanceof WebappClassLoaderBase) {
                if (!((WebappClassLoaderBase) cl).getState().isAvailable()) {
                    result.add(entry.getValue());
                }
            }
        }

        return result.toArray(new String[0]);
    }

    @Override
    public String[] findAliases() {
        synchronized (aliasesLock) {
            return this.aliases;
        }
    }


    @Override
    public void removeAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        synchronized (aliasesLock) {

            // Make sure this alias is currently present
            int n = -1;
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias)) {
                    n = i;
                    break;
                }
            }
            if (n < 0) {
                return;
            }

            // Remove the specified alias
            int j = 0;
            String results[] = new String[aliases.length - 1];
            for (int i = 0; i < aliases.length; i++) {
                if (i != n) {
                    results[j++] = aliases[i];
                }
            }
            aliases = results;

        }

        // Inform interested listeners
        fireContainerEvent(REMOVE_ALIAS_EVENT, alias);

    }


    @Override
    protected void startInternal() throws LifecycleException {

        // Set error report valve
        String errorValve = getErrorReportValveClass();
        if ((errorValve != null) && (!errorValve.equals(""))) {
            try {
                boolean found = false;
                Valve[] valves = getPipeline().getValves();
                for (Valve valve : valves) {
                    if (errorValve.equals(valve.getClass().getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Valve valve = ErrorReportValve.class.getName().equals(errorValve) ? new ErrorReportValve() :
                            (Valve) Class.forName(errorValve).getConstructor().newInstance();
                    getPipeline().addValve(valve);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("standardHost.invalidErrorReportValveClass", errorValve), t);
            }
        }
        super.startInternal();
    }


    // -------------------- JMX --------------------
    /**
     * @return the MBean Names of the Valves associated with this Host
     *
     * @exception Exception if an MBean cannot be created or registered
     */
    public String[] getValveNames() throws Exception {
        Valve[] valves = this.getPipeline().getValves();
        String[] mbeanNames = new String[valves.length];
        for (int i = 0; i < valves.length; i++) {
            if (valves[i] instanceof JmxEnabled) {
                ObjectName oname = ((JmxEnabled) valves[i]).getObjectName();
                if (oname != null) {
                    mbeanNames[i] = oname.toString();
                }
            }
        }

        return mbeanNames;
    }

    public String[] getAliases() {
        synchronized (aliasesLock) {
            return aliases;
        }
    }

    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder keyProperties = new StringBuilder("type=Host");
        keyProperties.append(getMBeanKeyProperties());

        return keyProperties.toString();
    }

}
