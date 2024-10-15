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
package org.apache.catalina.storeconfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.util.ContextName;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.descriptor.web.ApplicationParameter;
import org.apache.tomcat.util.http.CookieProcessor;

/**
 * Store server.xml Context element with all children
 * <ul>
 * <li>Store all context at server.xml</li>
 * <li>Store existing app.xml context a conf/enginename/hostname/app.xml</li>
 * <li>Store with backup</li>
 * </ul>
 */
public class StandardContextSF extends StoreFactoryBase {

    private static Log log = LogFactory.getLog(StandardContextSF.class);

    /**
     * Store a Context as Separate file as configFile value from context exists. filename can be relative to
     * catalina.base.
     *
     * @see org.apache.catalina.storeconfig.IStoreFactory#store(java.io.PrintWriter, int, java.lang.Object)
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aContext) throws Exception {

        if (aContext instanceof StandardContext) {
            StoreDescription desc = getRegistry().findDescription(aContext.getClass());
            if (desc != null && desc.isStoreSeparate()) {
                URL configFile = ((StandardContext) aContext).getConfigFile();
                if (configFile != null) {
                    if (desc.isExternalAllowed()) {
                        if (desc.isBackup()) {
                            storeWithBackup((StandardContext) aContext);
                        } else {
                            storeContextSeparate(aWriter, indent, (StandardContext) aContext);
                        }
                        return;
                    }
                } else if (desc.isExternalOnly()) {
                    // Set a configFile so that the configuration is actually saved
                    Context context = ((StandardContext) aContext);
                    Host host = (Host) context.getParent();
                    File configBase = host.getConfigBaseFile();
                    ContextName cn = new ContextName(context.getName(), false);
                    String baseName = cn.getBaseName();
                    File xml = new File(configBase, baseName + ".xml");
                    context.setConfigFile(xml.toURI().toURL());
                    if (desc.isBackup()) {
                        storeWithBackup((StandardContext) aContext);
                    } else {
                        storeContextSeparate(aWriter, indent, (StandardContext) aContext);
                    }
                    return;
                }
            }
        }
        super.store(aWriter, indent, aContext);

    }

    /**
     * Store a Context without backup add separate file or when configFile = null a aWriter.
     *
     * @param aWriter  Current output writer
     * @param indent   Indentation level
     * @param aContext The context which will be stored
     *
     * @throws Exception Configuration storing error
     */
    protected void storeContextSeparate(PrintWriter aWriter, int indent, StandardContext aContext) throws Exception {
        URL configFile = aContext.getConfigFile();
        if (configFile != null) {
            File config = new File(configFile.toURI());
            if (!config.isAbsolute()) {
                config = new File(System.getProperty(Globals.CATALINA_BASE_PROP), config.getPath());
            }
            if ((!config.isFile()) || (!config.canWrite())) {
                throw new IOException(sm.getString("standardContextSF.cannotWriteFile", configFile));
            }
            if (log.isInfoEnabled()) {
                log.info(sm.getString("standardContextSF.storeContext", aContext.getPath(), config));
            }
            try (FileOutputStream fos = new FileOutputStream(config);
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(fos, getRegistry().getEncoding()))) {
                storeXMLHead(writer);
                super.store(writer, -2, aContext);
            }
        } else {
            super.store(aWriter, indent, aContext);
        }
    }

    /**
     * Store the Context with a Backup.
     *
     * @param aContext The context which will be stored
     *
     * @throws Exception Configuration storing error
     */
    protected void storeWithBackup(StandardContext aContext) throws Exception {
        StoreFileMover mover = getConfigFileWriter(aContext);
        if (mover != null) {
            // Bugzilla 37781 Check to make sure we can write this output file
            if ((mover.getConfigOld() == null) || (mover.getConfigOld().isDirectory()) ||
                    (mover.getConfigOld().exists() && !mover.getConfigOld().canWrite())) {
                throw new IOException(sm.getString("standardContextSF.moveFailed", mover.getConfigOld()));
            }
            File dir = mover.getConfigSave().getParentFile();
            if (dir != null && dir.isDirectory() && (!dir.canWrite())) {
                throw new IOException(sm.getString("standardContextSF.cannotWriteFile", mover.getConfigSave()));
            }
            if (log.isInfoEnabled()) {
                log.info(sm.getString("standardContextSF.storeContextWithBackup", aContext.getPath(),
                        mover.getConfigSave()));
            }
            try (PrintWriter writer = mover.getWriter()) {
                storeXMLHead(writer);
                super.store(writer, -2, aContext);
            }
            mover.move();
        }
    }

    /**
     * Get explicit writer for context (context.getConfigFile()).
     *
     * @param context The context which will be stored
     *
     * @return The file mover
     *
     * @throws Exception Error getting a writer for the configuration file
     */
    protected StoreFileMover getConfigFileWriter(Context context) throws Exception {
        URL configFile = context.getConfigFile();
        StoreFileMover mover = null;
        if (configFile != null) {
            File config = new File(configFile.toURI());
            if (!config.isAbsolute()) {
                config = new File(System.getProperty(Globals.CATALINA_BASE_PROP), config.getPath());
            }
            // Open an output writer for the new configuration file
            mover = new StoreFileMover("", config.getCanonicalPath(), getRegistry().getEncoding());
        }
        return mover;
    }

    /**
     * Store the specified context element children.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aContext, StoreDescription parentDesc)
            throws Exception {
        if (aContext instanceof StandardContext) {
            StandardContext context = (StandardContext) aContext;
            // Store nested <Listener> elements
            LifecycleListener listeners[] = context.findLifecycleListeners();
            List<LifecycleListener> listenersArray = new ArrayList<>();
            for (LifecycleListener listener : listeners) {
                if (!(listener instanceof ThreadLocalLeakPreventionListener)) {
                    listenersArray.add(listener);
                }
            }
            storeElementArray(aWriter, indent, listenersArray.toArray());

            // Store nested <Valve> elements
            Valve valves[] = context.getPipeline().getValves();
            storeElementArray(aWriter, indent, valves);

            // Store nested <Loader> elements
            Loader loader = context.getLoader();
            storeElement(aWriter, indent, loader);

            // Store nested <Manager> elements
            if (context.getCluster() == null || !context.getDistributable()) {
                Manager manager = context.getManager();
                storeElement(aWriter, indent, manager);
            }

            // Store nested <Realm> element
            Realm realm = context.getRealm();
            if (realm != null) {
                Realm parentRealm = null;
                // @TODO is this case possible?
                if (context.getParent() != null) {
                    parentRealm = context.getParent().getRealm();
                }
                if (realm != parentRealm) {
                    storeElement(aWriter, indent, realm);
                }
            }
            // Store nested resources
            WebResourceRoot resources = context.getResources();
            storeElement(aWriter, indent, resources);

            // Store nested <WrapperListener> elements
            String wLifecycles[] = context.findWrapperLifecycles();
            getStoreAppender().printTagArray(aWriter, "WrapperListener", indent + 2, wLifecycles);
            // Store nested <WrapperLifecycle> elements
            String wListeners[] = context.findWrapperListeners();
            getStoreAppender().printTagArray(aWriter, "WrapperLifecycle", indent + 2, wListeners);

            // Store nested <Parameter> elements
            ApplicationParameter[] appParams = context.findApplicationParameters();
            storeElementArray(aWriter, indent, appParams);

            // Store nested naming resources elements (EJB,Resource,...)
            NamingResourcesImpl nresources = context.getNamingResources();
            storeElement(aWriter, indent, nresources);

            // Store nested watched resources <WatchedResource>
            String[] wresources = context.findWatchedResources();
            wresources = filterWatchedResources(context, wresources);
            getStoreAppender().printTagArray(aWriter, "WatchedResource", indent + 2, wresources);

            // Store nested <JarScanner> elements
            JarScanner jarScanner = context.getJarScanner();
            storeElement(aWriter, indent, jarScanner);

            // Store nested <CookieProcessor> elements
            CookieProcessor cookieProcessor = context.getCookieProcessor();
            storeElement(aWriter, indent, cookieProcessor);
        }
    }

    /**
     * Return a File object representing the "configuration root" directory for our associated Host.
     *
     * @param context The context instance
     *
     * @return a file to the configuration base path
     */
    protected File configBase(Context context) {

        File file = new File(System.getProperty(Globals.CATALINA_BASE_PROP), "conf");
        Container host = context.getParent();

        if (host instanceof Host) {
            Container engine = host.getParent();
            if (engine instanceof Engine) {
                file = new File(file, engine.getName());
            }
            file = new File(file, host.getName());
            try {
                file = file.getCanonicalFile();
            } catch (IOException e) {
                log.error(sm.getString("standardContextSF.canonicalPathError"), e);
            }
        }
        return file;

    }

    /**
     * Filter out the default watched resources, to remove standard ones.
     * <p>
     * TODO relative watched resources
     * <p>
     * TODO absolute handling configFile
     * <p>
     * TODO Filename case handling for Windows?
     * <p>
     * TODO digester variable substitution $catalina.base, $catalina.home
     *
     * @param context    The context instance
     * @param wresources The raw watched resources list
     *
     * @return The filtered watched resources
     *
     * @throws Exception Configuration storing error
     */
    protected String[] filterWatchedResources(StandardContext context, String[] wresources) throws Exception {
        File configBase = configBase(context);
        String confContext =
                new File(System.getProperty(Globals.CATALINA_BASE_PROP), "conf/context.xml").getCanonicalPath();
        String confWeb = new File(System.getProperty(Globals.CATALINA_BASE_PROP), "conf/web.xml").getCanonicalPath();
        String confHostDefault = new File(configBase, "context.xml.default").getCanonicalPath();
        String configFile =
                (context.getConfigFile() != null ? new File(context.getConfigFile().toURI()).getCanonicalPath() : null);
        String webxml = "WEB-INF/web.xml";
        String tomcatwebxml = "WEB-INF/tomcat-web.xml";

        List<String> resource = new ArrayList<>();
        for (String wresource : wresources) {
            if (wresource.equals(confContext)) {
                continue;
            }
            if (wresource.equals(confWeb)) {
                continue;
            }
            if (wresource.equals(confHostDefault)) {
                continue;
            }
            if (wresource.equals(configFile)) {
                continue;
            }
            if (wresource.equals(webxml)) {
                continue;
            }
            if (wresource.equals(tomcatwebxml)) {
                continue;
            }
            resource.add(wresource);
        }
        return resource.toArray(new String[0]);
    }

}
