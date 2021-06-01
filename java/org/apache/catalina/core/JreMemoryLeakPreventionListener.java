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
import java.net.URLConnection;
import java.sql.DriverManager;
import java.util.StringTokenizer;
import java.util.concurrent.ForkJoinPool;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.startup.SafeForkJoinWorkerThreadFactory;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.compat.JreVendor;
import org.apache.tomcat.util.res.StringManager;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;

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

    private static final String FORK_JOIN_POOL_THREAD_FACTORY_PROPERTY =
            "java.util.concurrent.ForkJoinPool.common.threadFactory";
    /**
     * Protect against the memory leak caused when the first call to
     * <code>sun.awt.AppContext.getAppContext()</code> is triggered by a web
     * application. Defaults to <code>false</code> since Tomcat code no longer
     * triggers this although application code may.
     */
    private boolean appContextProtection = false;
    public boolean isAppContextProtection() { return appContextProtection; }
    public void setAppContextProtection(boolean appContextProtection) {
        this.appContextProtection = appContextProtection;
    }

    /**
     * Protect against the memory leak caused when the first call to
     * <code>java.awt.Toolkit.getDefaultToolkit()</code> is triggered
     * by a web application. Defaults to <code>false</code> because a new
     * Thread is launched.
     */
    private boolean awtThreadProtection = false;
    public boolean isAWTThreadProtection() { return awtThreadProtection; }
    public void setAWTThreadProtection(boolean awtThreadProtection) {
      this.awtThreadProtection = awtThreadProtection;
    }

    /**
     * Protect against the memory leak caused when the first call to
     * <code>sun.misc.GC.requestLatency(long)</code> is triggered by a web
     * application. This first call will start a GC Daemon thread with the
     * thread's context class loader configured to be the web application class
     * loader. Defaults to <code>true</code>.
     *
     * @see "http://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8157570"
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
     * XML parsing can pin a web application class loader in memory. There are
     * multiple root causes for this. Some of these are particularly nasty as
     * profilers may not identify any GC roots related to the leak. For example,
     * with YourKit you need to ensure that HPROF format memory snapshots are
     * used to be able to trace some of the leaks.
     */
    private boolean xmlParsingProtection = true;
    public boolean isXmlParsingProtection() { return xmlParsingProtection; }
    public void setXmlParsingProtection(boolean xmlParsingProtection) {
        this.xmlParsingProtection = xmlParsingProtection;
    }

    /**
     * <code>com.sun.jndi.ldap.LdapPoolManager</code> class spawns a thread when
     * it is initialized if the system property
     * <code>com.sun.jndi.ldap.connect.pool.timeout</code> is greater than 0.
     * That thread inherits the context class loader of the current thread, so
     * that there may be a web application class loader leak if the web app
     * is the first to use <code>LdapPoolManager</code>.
     *
     * @see "http://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8156824"
     */
    private boolean ldapPoolProtection = true;
    public boolean isLdapPoolProtection() { return ldapPoolProtection; }
    public void setLdapPoolProtection(boolean ldapPoolProtection) {
        this.ldapPoolProtection = ldapPoolProtection;
    }

    /**
     * The first access to {@link DriverManager} will trigger the loading of
     * all {@link java.sql.Driver}s in the the current class loader. The web
     * application level memory leak protection can take care of this in most
     * cases but triggering the loading here has fewer side-effects.
     */
    private boolean driverManagerProtection = true;
    public boolean isDriverManagerProtection() {
        return driverManagerProtection;
    }
    public void setDriverManagerProtection(boolean driverManagerProtection) {
        this.driverManagerProtection = driverManagerProtection;
    }

    /**
     * {@link ForkJoinPool#commonPool()} creates a thread pool that, by default,
     * creates threads that retain references to the thread context class
     * loader.
     *
     * @see "http://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8172726"
     */
    private boolean forkJoinCommonPoolProtection = true;
    public boolean getForkJoinCommonPoolProtection() {
        return forkJoinCommonPoolProtection;
    }
    public void setForkJoinCommonPoolProtection(boolean forkJoinCommonPoolProtection) {
        this.forkJoinCommonPoolProtection = forkJoinCommonPoolProtection;
    }

    /**
     * List of comma-separated fully qualified class names to load and initialize during
     * the startup of this Listener. This allows to pre-load classes that are known to
     * provoke classloader leaks if they are loaded during a request processing.
     */
    private String classesToInitialize = null;
    public String getClassesToInitialize() {
        return classesToInitialize;
    }
    public void setClassesToInitialize(String classesToInitialize) {
        this.classesToInitialize = classesToInitialize;
    }



    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        // Initialise these classes when Tomcat starts
        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {

            /*
             * First call to this loads all drivers visible to the current class
             * loader and its parents.
             *
             * Note: This is called before the context class loader is changed
             *       because we want any drivers located in CATALINA_HOME/lib
             *       and/or CATALINA_HOME/lib to be visible to DriverManager.
             *       Users wishing to avoid having JDBC drivers loaded by this
             *       class loader should add the JDBC driver(s) to the class
             *       path so they are loaded by the system class loader.
             */
            if (driverManagerProtection) {
                DriverManager.getDrivers();
            }

            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            try
            {
                // Use the system classloader as the victim for all this
                // ClassLoader pinning we're about to do.
                Thread.currentThread().setContextClassLoader(
                        ClassLoader.getSystemClassLoader());

                /*
                 * Several components end up calling:
                 * sun.awt.AppContext.getAppContext()
                 *
                 * Those libraries / components known to trigger memory leaks
                 * due to eventual calls to getAppContext() are:
                 * - Google Web Toolkit via its use of javax.imageio
                 * - Batik
                 * - others TBD
                 *
                 * Note tha a call to sun.awt.AppContext.getAppContext() results
                 * in a thread being started named AWT-AppKit that requires a
                 * graphical environment to be available.
                 */

                // Trigger a call to sun.awt.AppContext.getAppContext(). This
                // will pin the system class loader in memory but that shouldn't
                // be an issue.
                if (appContextProtection) {
                    ImageIO.getCacheDirectory();
                }

                // Trigger the creation of the AWT (AWT-Windows, AWT-XAWT,
                // etc.) thread.
                // Note this issue is fixed in Java 8 update 05 onwards.
                if (awtThreadProtection && !JreCompat.isJre9Available()) {
                    java.awt.Toolkit.getDefaultToolkit();
                }

                /*
                 * Several components end up calling
                 * sun.misc.GC.requestLatency(long) which creates a daemon
                 * thread without setting the TCCL.
                 *
                 * Those libraries / components known to trigger memory leaks
                 * due to eventual calls to requestLatency(long) are:
                 * - javax.management.remote.rmi.RMIConnectorServer.start()
                 *
                 * Note: Long.MAX_VALUE is a special case that causes the thread
                 *       to terminate
                 *
                 * Fixed in Java 9 onwards (from early access build 130)
                 */
                if (gcDaemonProtection && !JreCompat.isJre9Available()) {
                    try {
                        Class<?> clazz = Class.forName("sun.misc.GC");
                        Method method = clazz.getDeclaredMethod(
                                "requestLatency",
                                new Class[] {long.class});
                        method.invoke(null, Long.valueOf(Long.MAX_VALUE - 1));
                    } catch (ClassNotFoundException e) {
                        if (JreVendor.IS_ORACLE_JVM) {
                            log.error(sm.getString(
                                    "jreLeakListener.gcDaemonFail"), e);
                        } else {
                            log.debug(sm.getString(
                                    "jreLeakListener.gcDaemonFail"), e);
                        }
                    } catch (SecurityException | NoSuchMethodException | IllegalArgumentException |
                            IllegalAccessException e) {
                        log.error(sm.getString("jreLeakListener.gcDaemonFail"),
                                e);
                    } catch (InvocationTargetException e) {
                        ExceptionUtils.handleThrowable(e.getCause());
                        log.error(sm.getString("jreLeakListener.gcDaemonFail"),
                                e);
                    }
                }

                /*
                 * Creating a MessageDigest during web application startup
                 * initializes the Java Cryptography Architecture. Under certain
                 * conditions this starts a Token poller thread with TCCL equal
                 * to the web application class loader.
                 *
                 * Instead we initialize JCA right now.
                 *
                 * Fixed in Java 9 onwards (from early access build 133)
                 */
                if (tokenPollerProtection && !JreCompat.isJre9Available()) {
                    java.security.Security.getProviders();
                }

                /*
                 * Several components end up opening JarURLConnections without
                 * first disabling caching. This effectively locks the file.
                 * Whilst more noticeable and harder to ignore on Windows, it
                 * affects all operating systems.
                 *
                 * Those libraries/components known to trigger this issue
                 * include:
                 * - log4j versions 1.2.15 and earlier
                 * - javax.xml.bind.JAXBContext.newInstance()
                 *
                 * https://bugs.openjdk.java.net/browse/JDK-8163449
                 *
                 * Java 9 onwards disables caching for JAR URLConnections
                 * Java 8 and earlier disables caching for all URLConnections
                 */

                // Set the default URL caching policy to not to cache
                if (urlCacheProtection) {
                    try {
                        JreCompat.getInstance().disableCachingForJarUrlConnections();
                    } catch (IOException e) {
                        log.error(sm.getString("jreLeakListener.jarUrlConnCacheFail"), e);
                    }
                }

                /*
                 * Fixed in Java 9 onwards (from early access build 133)
                 */
                if (xmlParsingProtection && !JreCompat.isJre9Available()) {
                    // There are two known issues with XML parsing that affect
                    // Java 8+. The issues both relate to cached Exception
                    // instances that retain a link to the TCCL via the
                    // backtrace field. Note that YourKit only shows this field
                    // when using the HPROF format memory snapshots.
                    // https://bz.apache.org/bugzilla/show_bug.cgi?id=58486
                    // https://bugs.openjdk.java.net/browse/JDK-8146961
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    try {
                        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
                        // Issue 1
                        // com.sun.org.apache.xml.internal.serialize.DOMSerializerImpl
                        Document document = documentBuilder.newDocument();
                        document.createElement("dummy");
                        DOMImplementationLS implementation =
                                (DOMImplementationLS)document.getImplementation();
                        implementation.createLSSerializer().writeToString(document);
                        // Issue 1
                        // com.sun.org.apache.xerces.internal.dom.DOMNormalizer
                        document.normalize();
                    } catch (ParserConfigurationException e) {
                        log.error(sm.getString("jreLeakListener.xmlParseFail"),
                                e);
                    }
                }

                /*
                 * Fixed in Java 9 onwards (from early access build 130)
                 */
                if (ldapPoolProtection && !JreCompat.isJre9Available()) {
                    try {
                        Class.forName("com.sun.jndi.ldap.LdapPoolManager");
                    } catch (ClassNotFoundException e) {
                        if (JreVendor.IS_ORACLE_JVM) {
                            log.error(sm.getString(
                                    "jreLeakListener.ldapPoolManagerFail"), e);
                        } else {
                            log.debug(sm.getString(
                                    "jreLeakListener.ldapPoolManagerFail"), e);
                        }
                    }
                }

                /*
                 * Present in Java 7 onwards
                 * Fixed in Java 9 (from early access build 156)
                 */
                if (forkJoinCommonPoolProtection && !JreCompat.isJre9Available()) {
                    // Don't override any explicitly set property
                    if (System.getProperty(FORK_JOIN_POOL_THREAD_FACTORY_PROPERTY) == null) {
                        System.setProperty(FORK_JOIN_POOL_THREAD_FACTORY_PROPERTY,
                                SafeForkJoinWorkerThreadFactory.class.getName());
                    }
                }

                if (classesToInitialize != null) {
                    StringTokenizer strTok =
                        new StringTokenizer(classesToInitialize, ", \r\n\t");
                    while (strTok.hasMoreTokens()) {
                        String classNameToLoad = strTok.nextToken();
                        try {
                            Class.forName(classNameToLoad);
                        } catch (ClassNotFoundException e) {
                            log.error(
                                sm.getString("jreLeakListener.classToInitializeFail",
                                    classNameToLoad), e);
                            // continue with next class to load
                        }
                    }
                }

            } finally {
                Thread.currentThread().setContextClassLoader(loader);
            }
        }
    }
}
