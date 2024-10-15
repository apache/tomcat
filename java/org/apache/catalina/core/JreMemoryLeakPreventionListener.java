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

import java.net.URLConnection;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provide a workaround for known places where the Java Runtime environment can cause a memory leak or lock files.
 * <p>
 * Memory leaks occur when JRE code uses the context class loader to load a singleton as this will cause a memory leak
 * if a web application class loader happens to be the context class loader at the time. The work-around is to
 * initialise these singletons when Tomcat's common class loader is the context class loader.
 * <p>
 * Locked files usually occur when a resource inside a JAR is accessed without first disabling Jar URL connection
 * caching. The workaround is to disable this caching by default.
 * <p>
 * This listener must only be nested within {@link Server} elements.
 */
public class JreMemoryLeakPreventionListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(JreMemoryLeakPreventionListener.class);
    private static final StringManager sm = StringManager.getManager(JreMemoryLeakPreventionListener.class);

    /**
     * Protect against the memory leak caused when the first call to <code>sun.awt.AppContext.getAppContext()</code> is
     * triggered by a web application. Defaults to <code>false</code> since Tomcat code no longer triggers this although
     * application code may.
     */
    private boolean appContextProtection = false;

    public boolean isAppContextProtection() {
        return appContextProtection;
    }

    public void setAppContextProtection(boolean appContextProtection) {
        this.appContextProtection = appContextProtection;
    }

    /**
     * Protect against resources being read for JAR files and, as a side-effect, the JAR file becoming locked. Note this
     * disables caching for all {@link URLConnection}s, regardless of type. Defaults to <code>true</code>.
     */
    private boolean urlCacheProtection = true;

    public boolean isUrlCacheProtection() {
        return urlCacheProtection;
    }

    public void setUrlCacheProtection(boolean urlCacheProtection) {
        this.urlCacheProtection = urlCacheProtection;
    }

    /**
     * The first access to {@link DriverManager} will trigger the loading of all {@link java.sql.Driver}s in the the
     * current class loader. The web application level memory leak protection can take care of this in most cases but
     * triggering the loading here has fewer side-effects.
     */
    private boolean driverManagerProtection = true;

    public boolean isDriverManagerProtection() {
        return driverManagerProtection;
    }

    public void setDriverManagerProtection(boolean driverManagerProtection) {
        this.driverManagerProtection = driverManagerProtection;
    }

    /**
     * List of comma-separated fully qualified class names to load and initialize during the startup of this Listener.
     * This allows to pre-load classes that are known to provoke classloader leaks if they are loaded during a request
     * processing.
     */
    private String classesToInitialize = null;

    public String getClassesToInitialize() {
        return classesToInitialize;
    }

    public void setClassesToInitialize(String classesToInitialize) {
        this.classesToInitialize = classesToInitialize;
    }

    /**
     * Initialize JVM seed generator. On some platforms, the JVM will create a thread for this task, which can get
     * associated with a web application depending on the timing.
     */
    private boolean initSeedGenerator = false;

    public boolean getInitSeedGenerator() {
        return this.initSeedGenerator;
    }

    public void setInitSeedGenerator(boolean initSeedGenerator) {
        this.initSeedGenerator = initSeedGenerator;
    }


    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        // Initialise these classes when Tomcat starts
        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
            if (!(event.getLifecycle() instanceof Server)) {
                log.warn(sm.getString("listener.notServer", event.getLifecycle().getClass().getSimpleName()));
            }

            /*
             * First call to this loads all drivers visible to the current class loader and its parents.
             *
             * Note: This is called before the context class loader is changed because we want any drivers located in
             * CATALINA_HOME/lib and/or CATALINA_HOME/lib to be visible to DriverManager. Users wishing to avoid having
             * JDBC drivers loaded by this class loader should add the JDBC driver(s) to the class path so they are
             * loaded by the system class loader.
             */
            if (driverManagerProtection) {
                DriverManager.getDrivers();
            }

            Thread currentThread = Thread.currentThread();
            ClassLoader loader = currentThread.getContextClassLoader();

            try {
                // Use the system classloader as the victim for all this
                // ClassLoader pinning we're about to do.
                currentThread.setContextClassLoader(ClassLoader.getSystemClassLoader());

                /*
                 * Several components end up calling: sun.awt.AppContext.getAppContext()
                 *
                 * Those libraries / components known to trigger memory leaks due to eventual calls to getAppContext()
                 * are: - Google Web Toolkit via its use of javax.imageio - Batik - others TBD
                 *
                 * Note that a call to sun.awt.AppContext.getAppContext() results in a thread being started named
                 * AWT-AppKit that requires a graphical environment to be available.
                 */

                // Trigger a call to sun.awt.AppContext.getAppContext(). This
                // will pin the system class loader in memory but that shouldn't
                // be an issue.
                if (appContextProtection) {
                    ImageIO.getCacheDirectory();
                }

                /*
                 * Several components end up opening JarURLConnections without first disabling caching. This effectively
                 * locks the file. Whilst more noticeable and harder to ignore on Windows, it affects all operating
                 * systems.
                 *
                 * Those libraries/components known to trigger this issue include: - log4j versions 1.2.15 and earlier -
                 * javax.xml.bind.JAXBContext.newInstance()
                 *
                 * https://bugs.openjdk.java.net/browse/JDK-8163449
                 *
                 * Disable caching for JAR URLConnections
                 */

                // Set the default URL caching policy to not to cache
                if (urlCacheProtection) {
                    URLConnection.setDefaultUseCaches("JAR", false);
                }

                /*
                 * Initialize the SeedGenerator of the JVM, as some platforms use a thread which could end up being
                 * associated with a webapp rather than the container.
                 */
                if (initSeedGenerator) {
                    SecureRandom.getSeed(1);
                }

                if (classesToInitialize != null) {
                    StringTokenizer strTok = new StringTokenizer(classesToInitialize, ", \r\n\t");
                    while (strTok.hasMoreTokens()) {
                        String classNameToLoad = strTok.nextToken();
                        try {
                            Class.forName(classNameToLoad);
                        } catch (ClassNotFoundException e) {
                            log.error(sm.getString("jreLeakListener.classToInitializeFail", classNameToLoad), e);
                            // continue with next class to load
                        }
                    }
                }

            } finally {
                currentThread.setContextClassLoader(loader);
            }
        }
    }
}
