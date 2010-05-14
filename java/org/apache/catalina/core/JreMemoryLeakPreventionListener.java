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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provide a workaround for known places where the Java Runtime environment can
 * cause a memory leak or lock files.
 * <p>
 * Memory leaks occur when JRE code uses
 * the context class loader to load a singleton as this will cause a memory leak
 * if a web application class loader happens to be the context class loader at
 * the time. The work-around is to initialise these singletons when Tomcat's
 * common class loader is the context class loader.
 * <p>
 * Locked files usually occur when a resource inside a JAR is accessed without
 * first disabling Jar URL connection caching. The workaround is to disable this
 * caching by default. 
 */
public class JreMemoryLeakPreventionListener implements LifecycleListener {

    private static final Log log =
        LogFactory.getLog(JreMemoryLeakPreventionListener.class);
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * Protect against the memory leak caused when the first call to
     * <code>sun.awt.AppContext.getAppContext()</code> is triggered by a web
     * application. Defaults to <code>true</code>.
     */
    private boolean appContextProtection = true;
    public boolean isAppContextProtection() { return appContextProtection; }
    public void setAppContextProtection(boolean appContextProtection) {
        this.appContextProtection = appContextProtection;
    }
    
    /**
     * Protect against the memory leak caused when the first call to
     * <code>sun.misc.GC.requestLatency(long)</code> is triggered by a web
     * application. This first call will start a GC Daemon thread with the
     * thread's context class loader configured to be the web application class
     * loader. Defaults to <code>true</code>.
     */
    private boolean gcDaemonProtection = true;
    public boolean isGcDaemonProtection() { return gcDaemonProtection; }
    public void setGcDaemonProtection(boolean gcDaemonProtection) {
        this.gcDaemonProtection = gcDaemonProtection;
    }

    /**
     * Protect against the memory leak, when the initialization of the
     * Java Cryptography Architecture is triggered by initializing
     * a MessageDigest during web application deployment.
     * This will occasionally start a Token Poller thread with the thread's
     * context class loader equal to the web application class loader.
     * Instead we initialize JCA early.
     * Defaults to <code>true</code>.
     */
    private boolean tokenPollerProtection = true;
    public boolean isTokenPollerProtection() { return tokenPollerProtection; }
    public void setTokenPollerProtection(boolean tokenPollerProtection) {
        this.tokenPollerProtection = tokenPollerProtection;
    }

    /**
     * Protect against resources being read for JAR files and, as a side-effect,
     * the JAR file becoming locked. Note this disables caching for all
     * {@link URLConnection}s, regardless of type. Defaults to
     * <code>true</code>.
     */
    private boolean urlCacheProtection = true;
    public boolean isUrlCacheProtection() { return urlCacheProtection; }
    public void setUrlCacheProtection(boolean urlCacheProtection) {
        this.urlCacheProtection = urlCacheProtection;
    }

    /**
     * XML parsing can pin a web application class loader in memory. This is
     * particularly nasty as profilers (at least YourKit and Eclipse MAT) don't
     * identify any GC roots related to this.
     * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6916498">
     * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6916498</a>
     */
    private boolean xmlParsingProtection = true;
    public boolean isXmlParsingProtection() { return xmlParsingProtection; }
    public void setXmlParsingProtection(boolean xmlParsingProtection) {
        this.xmlParsingProtection = xmlParsingProtection;
    }
    
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        // Initialise these classes when Tomcat starts
        if (Lifecycle.INIT_EVENT.equals(event.getType())) {
            /*
             * Several components end up calling:
             * sun.awt.AppContext.getAppContext()
             * 
             * Those libraries / components known to trigger memory leaks due to
             * eventual calls to getAppContext() are:
             * - Google Web Toolkit via its use of javax.imageio
             * - Tomcat via its use of java.beans.Introspector.flushCaches() in
             *   1.6.0_15 onwards
             * - others TBD
             */
            
            // Trigger a call to sun.awt.AppContext.getAppContext(). This will
            // pin the common class loader in memory but that shouldn't be an
            // issue.
            if (appContextProtection) {
                ImageIO.getCacheDirectory();
            }
            
            /*
             * Several components end up calling:
             * sun.misc.GC.requestLatency(long)
             * 
             * Those libraries / components known to trigger memory leaks due to
             * eventual calls to requestLatency(long) are:
             * - javax.management.remote.rmi.RMIConnectorServer.start()
             */
            if (gcDaemonProtection) {
                try {
                    Class<?> clazz = Class.forName("sun.misc.GC");
                    Method method = clazz.getDeclaredMethod("requestLatency",
                            new Class[] {long.class});
                    method.invoke(null, Long.valueOf(3600000));
                } catch (ClassNotFoundException e) {
                    if (System.getProperty("java.vendor").startsWith("Sun")) {
                        log.error(sm.getString(
                                "jreLeakListener.gcDaemonFail"), e);
                    } else {
                        log.debug(sm.getString(
                                "jreLeakListener.gcDaemonFail"), e);
                    }
                } catch (SecurityException e) {
                    log.error(sm.getString("jreLeakListener.gcDaemonFail"), e);
                } catch (NoSuchMethodException e) {
                    log.error(sm.getString("jreLeakListener.gcDaemonFail"), e);
                } catch (IllegalArgumentException e) {
                    log.error(sm.getString("jreLeakListener.gcDaemonFail"), e);
                } catch (IllegalAccessException e) {
                    log.error(sm.getString("jreLeakListener.gcDaemonFail"), e);
                } catch (InvocationTargetException e) {
                    log.error(sm.getString("jreLeakListener.gcDaemonFail"), e);
                }
            }
            
            /*
             * Creating a MessageDigest during web application startup
             * initializes the Java Cryptography Architecture. Under certain
             * conditions this starts a Token poller thread with TCCL equal
             * to the web application class loader.
             * 
             * Instead we initialize JCA right now.
             */
            if (tokenPollerProtection) {
                java.security.Security.getProviders();
            }
            
            /*
             * Several components end up opening JarURLConnections without first
             * disabling caching. This effectively locks the file. Whilst more
             * noticeable and harder to ignore on Windows, it affects all
             * operating systems.
             * 
             * Those libraries/components known to trigger this issue include:
             * - log4j versions 1.2.15 and earlier
             * - javax.xml.bind.JAXBContext.newInstance()
             */
            
            // Set the default URL caching policy to not to cache
            if (urlCacheProtection) {
                try {
                    // Doesn't matter that this JAR doesn't exist - just as long as
                    // the URL is well-formed
                    URL url = new URL("jar:file://dummy.jar!/");
                    URLConnection uConn = url.openConnection();
                    uConn.setDefaultUseCaches(false);
                } catch (MalformedURLException e) {
                    log.error(sm.getString(
                            "jreLeakListener.jarUrlConnCacheFail"), e);
                } catch (IOException e) {
                    log.error(sm.getString(
                            "jreLeakListener.jarUrlConnCacheFail"), e);
                }
            }
            
            /*
             * Haven't got to the root of what is going on with this leak but if
             * a web app is the first to make the calls below the web
             * application class loader will be pinned in memory.
             */
            if (xmlParsingProtection) {
                DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
                try {
                    factory.newDocumentBuilder();
                } catch (ParserConfigurationException e) {
                    log.error(sm.getString("jreLeakListener.xmlParseFail"), e);
                }
            }
        }
    }

}
