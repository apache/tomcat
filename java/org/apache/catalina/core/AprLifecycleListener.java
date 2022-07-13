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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.LibraryNotFoundError;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * Implementation of <code>LifecycleListener</code> that will init and
 * and destroy APR.
 * <p>
 * This listener must only be nested within {@link Server} elements.
 * <p>
 * <strong>Note</strong>: If you are running Tomcat in an embedded fashion and
 * have more than one Server instance per JVM, this listener <em>must not</em>
 * be added to the {@code Server} instances, but handled outside by the calling
 * code which is bootstrapping the embedded Tomcat instances. Not doing so will
 * lead to JVM crashes.
 *
 * @since 4.1
 */
public class AprLifecycleListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(AprLifecycleListener.class);

    /**
     * Info messages during init() are cached until Lifecycle.BEFORE_INIT_EVENT
     * so that, in normal (non-error) cases, init() related log messages appear
     * at the expected point in the lifecycle.
     */
    private static final List<String> initInfoLogMessages = new ArrayList<>(3);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(AprLifecycleListener.class);


    // ---------------------------------------------- Constants

    protected static final int TCN_REQUIRED_MAJOR = 1;
    protected static final int TCN_REQUIRED_MINOR = 2;
    protected static final int TCN_REQUIRED_PATCH = 34;
    protected static final int TCN_RECOMMENDED_MAJOR = 2;
    protected static final int TCN_RECOMMENDED_MINOR = 0;
    protected static final int TCN_RECOMMENDED_PV = 1;


    // ---------------------------------------------- Properties

    private static int tcnMajor = 0;
    private static int tcnMinor = 0;
    private static int tcnPatch = 0;
    private static int tcnVersion = 0;

    protected static String SSLEngine = "on"; //default on
    protected static String FIPSMode = "off"; // default off, valid only when SSLEngine="on"
    protected static String SSLRandomSeed = "builtin";
    protected static boolean sslInitialized = false;
    protected static boolean fipsModeActive = false;

    /**
     * The "FIPS mode" level that we use as the argument to OpenSSL method
     * <code>FIPS_mode_set()</code> to enable FIPS mode and that we expect as
     * the return value of <code>FIPS_mode()</code> when FIPS mode is enabled.
     * <p>
     * In the future the OpenSSL library might grow support for different
     * non-zero "FIPS" modes that specify different allowed subsets of ciphers
     * or whatever, but nowadays only "1" is the supported value.
     * </p>
     * @see <a href="http://wiki.openssl.org/index.php/FIPS_mode_set%28%29">OpenSSL method FIPS_mode_set()</a>
     * @see <a href="http://wiki.openssl.org/index.php/FIPS_mode%28%29">OpenSSL method FIPS_mode()</a>
     */
    private static final int FIPS_ON = 1;

    private static final int FIPS_OFF = 0;

    protected static final Object lock = new Object();

    public static boolean isAprAvailable() {
        //https://bz.apache.org/bugzilla/show_bug.cgi?id=48613
        if (AprStatus.isInstanceCreated()) {
            synchronized (lock) {
                init();
            }
        }
        return AprStatus.isAprAvailable();
    }

    public AprLifecycleListener() {
        AprStatus.setInstanceCreated(true);
    }

    // ---------------------------------------------- LifecycleListener Methods

    /**
     * Primary entry point for startup and shutdown events.
     *
     * @param event The event that has occurred
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
            synchronized (lock) {
                if (!(event.getLifecycle() instanceof Server)) {
                    log.warn(sm.getString("listener.notServer",
                            event.getLifecycle().getClass().getSimpleName()));
                }
                init();
                for (String msg : initInfoLogMessages) {
                    log.info(msg);
                }
                initInfoLogMessages.clear();
                if (AprStatus.isAprAvailable()) {
                    try {
                        initializeSSL();
                    } catch (Throwable t) {
                        t = ExceptionUtils.unwrapInvocationTargetException(t);
                        ExceptionUtils.handleThrowable(t);
                        log.error(sm.getString("aprListener.sslInit"), t);
                    }
                }
                // Failure to initialize FIPS mode is fatal
                if (!(null == FIPSMode || "off".equalsIgnoreCase(FIPSMode)) && !isFIPSModeActive()) {
                    String errorMessage = sm.getString("aprListener.initializeFIPSFailed");
                    Error e = new Error(errorMessage);
                    // Log here, because thrown error might be not logged
                    log.fatal(errorMessage, e);
                    throw e;
                }
            }
        } else if (Lifecycle.AFTER_DESTROY_EVENT.equals(event.getType())) {
            synchronized (lock) {
                if (!AprStatus.isAprAvailable()) {
                    return;
                }
                try {
                    terminateAPR();
                } catch (Throwable t) {
                    t = ExceptionUtils.unwrapInvocationTargetException(t);
                    ExceptionUtils.handleThrowable(t);
                    log.info(sm.getString("aprListener.aprDestroy"));
                }
            }
        }

    }

    private static void terminateAPR() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {
        String methodName = "terminate";
        Method method = Class.forName("org.apache.tomcat.jni.Library")
            .getMethod(methodName, (Class [])null);
        method.invoke(null, (Object []) null);
        AprStatus.setAprAvailable(false);
        AprStatus.setAprInitialized(false);
        sslInitialized = false; // Well we cleaned the pool in terminate.
        fipsModeActive = false;
    }

    private static void init() {
        int rqver = TCN_REQUIRED_MAJOR * 1000 + TCN_REQUIRED_MINOR * 100 + TCN_REQUIRED_PATCH;
        int rcver = TCN_RECOMMENDED_MAJOR * 1000 + TCN_RECOMMENDED_MINOR * 100 + TCN_RECOMMENDED_PV;

        if (AprStatus.isAprInitialized()) {
            return;
        }
        AprStatus.setAprInitialized(true);

        try {
            Library.initialize(null);
            tcnMajor = Library.TCN_MAJOR_VERSION;
            tcnMinor = Library.TCN_MINOR_VERSION;
            tcnPatch = Library.TCN_PATCH_VERSION;
            tcnVersion = tcnMajor * 1000 + tcnMinor * 100 + tcnPatch;
        } catch (LibraryNotFoundError lnfe) {
            // Library not on path
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("aprListener.aprInitDebug",
                        lnfe.getLibraryNames(), System.getProperty("java.library.path"),
                        lnfe.getMessage()), lnfe);
            }
            initInfoLogMessages.add(sm.getString("aprListener.aprInit",
                    System.getProperty("java.library.path")));
            return;
        } catch (Throwable t) {
            // Library present but failed to load
            t = ExceptionUtils.unwrapInvocationTargetException(t);
            ExceptionUtils.handleThrowable(t);
            log.warn(sm.getString("aprListener.aprInitError", t.getMessage()), t);
            return;
        }
        if (tcnMajor > 1 && "off".equalsIgnoreCase(SSLEngine)) {
            log.error(sm.getString("aprListener.sslRequired", SSLEngine, Library.versionString()));
            try {
                // Tomcat Native 2.x onwards requires SSL
                terminateAPR();
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
            }
            return;
        }
        if (tcnVersion < rqver) {
            log.error(sm.getString("aprListener.tcnInvalid",
                    Library.versionString(),
                    TCN_REQUIRED_MAJOR + "." +
                    TCN_REQUIRED_MINOR + "." +
                    TCN_REQUIRED_PATCH));
            try {
                // Terminate the APR in case the version
                // is below required.
                terminateAPR();
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
            }
            return;
        }
        if (tcnVersion < rcver) {
            initInfoLogMessages.add(sm.getString("aprListener.tcnVersion",
                    Library.versionString(),
                    TCN_RECOMMENDED_MAJOR + "." +
                    TCN_RECOMMENDED_MINOR + "." +
                    TCN_RECOMMENDED_PV));
        }

        initInfoLogMessages.add(sm.getString("aprListener.tcnValid",
                Library.versionString(),
                Library.aprVersionString()));

        AprStatus.setAprAvailable(true);
    }

    private static void initializeSSL() throws Exception {

        if ("off".equalsIgnoreCase(SSLEngine)) {
            return;
        }
        if (sslInitialized) {
            // Only once per VM
            return;
        }

        sslInitialized = true;

        String methodName = "randSet";
        Class<?> paramTypes[] = new Class[1];
        paramTypes[0] = String.class;
        Object paramValues[] = new Object[1];
        paramValues[0] = SSLRandomSeed;
        Class<?> clazz = Class.forName("org.apache.tomcat.jni.SSL");
        Method method = clazz.getMethod(methodName, paramTypes);
        method.invoke(null, paramValues);


        methodName = "initialize";
        paramValues[0] = "on".equalsIgnoreCase(SSLEngine)?null:SSLEngine;
        method = clazz.getMethod(methodName, paramTypes);
        method.invoke(null, paramValues);

        // OpenSSL 3 onwards uses providers
        boolean usingProviders = tcnMajor > 1 || (tcnVersion > 1233 && (SSL.version() & 0xF0000000L) > 0x20000000);

        // Tomcat Native 1.x built with OpenSSL 1.x without explicitly enabling
        // FIPS and Tomcat Native < 1.2.34 built with OpenSSL 3.x will fail if
        // any calls are made to SSL.fipsModeGet or SSL.fipsModeSet
        if (usingProviders || !(null == FIPSMode || "off".equalsIgnoreCase(FIPSMode))) {
            fipsModeActive = false;
            final boolean enterFipsMode;
            int fipsModeState = SSL.fipsModeGet();

            if(log.isDebugEnabled()) {
                log.debug(sm.getString("aprListener.currentFIPSMode", Integer.valueOf(fipsModeState)));
            }

            if (null == FIPSMode || "off".equalsIgnoreCase(FIPSMode)) {
                if (fipsModeState == FIPS_ON) {
                    fipsModeActive = true;
                }
                enterFipsMode = false;
            } else if ("on".equalsIgnoreCase(FIPSMode)) {
                if (fipsModeState == FIPS_ON) {
                    if (!usingProviders) {
                        log.info(sm.getString("aprListener.skipFIPSInitialization"));
                    }
                    fipsModeActive = true;
                    enterFipsMode = false;
                } else {
                    if (usingProviders) {
                        throw new IllegalStateException(sm.getString("aprListener.FIPSProviderNotDefault", FIPSMode));
                    } else {
                        enterFipsMode = true;
                    }
                }
            } else if ("require".equalsIgnoreCase(FIPSMode)) {
                if (fipsModeState == FIPS_ON) {
                    fipsModeActive = true;
                    enterFipsMode = false;
                } else {
                    if (usingProviders) {
                        throw new IllegalStateException(sm.getString("aprListener.FIPSProviderNotDefault", FIPSMode));
                    } else {
                        throw new IllegalStateException(sm.getString("aprListener.requireNotInFIPSMode"));
                    }
                }
            } else if ("enter".equalsIgnoreCase(FIPSMode)) {
                if (fipsModeState == FIPS_OFF) {
                    if (usingProviders) {
                        throw new IllegalStateException(sm.getString("aprListener.FIPSProviderNotDefault", FIPSMode));
                    } else {
                        enterFipsMode = true;
                    }
                } else {
                    if (usingProviders) {
                        fipsModeActive = true;
                        enterFipsMode = false;
                    } else {
                        throw new IllegalStateException(sm.getString(
                                "aprListener.enterAlreadyInFIPSMode", Integer.valueOf(fipsModeState)));
                    }
                }
            } else {
                throw new IllegalArgumentException(sm.getString(
                        "aprListener.wrongFIPSMode", FIPSMode));
            }

            if (enterFipsMode) {
                log.info(sm.getString("aprListener.initializingFIPS"));

                fipsModeState = SSL.fipsModeSet(FIPS_ON);
                if (fipsModeState != FIPS_ON) {
                    // This case should be handled by the native method,
                    // but we'll make absolutely sure, here.
                    String message = sm.getString("aprListener.initializeFIPSFailed");
                    log.error(message);
                    throw new IllegalStateException(message);
                }

                fipsModeActive = true;
                log.info(sm.getString("aprListener.initializeFIPSSuccess"));
            }

            if (usingProviders && fipsModeActive) {
                log.info(sm.getString("aprListener.usingFIPSProvider"));
            }
        }

        log.info(sm.getString("aprListener.initializedOpenSSL", SSL.versionString()));
    }

    public String getSSLEngine() {
        return SSLEngine;
    }

    public void setSSLEngine(String SSLEngine) {
        if (!SSLEngine.equals(AprLifecycleListener.SSLEngine)) {
            // Ensure that the SSLEngine is consistent with that used for SSL init
            if (sslInitialized) {
                throw new IllegalStateException(
                        sm.getString("aprListener.tooLateForSSLEngine"));
            }

            AprLifecycleListener.SSLEngine = SSLEngine;
        }
    }

    public String getSSLRandomSeed() {
        return SSLRandomSeed;
    }

    public void setSSLRandomSeed(String SSLRandomSeed) {
        if (!SSLRandomSeed.equals(AprLifecycleListener.SSLRandomSeed)) {
            // Ensure that the random seed is consistent with that used for SSL init
            if (sslInitialized) {
                throw new IllegalStateException(
                        sm.getString("aprListener.tooLateForSSLRandomSeed"));
            }

            AprLifecycleListener.SSLRandomSeed = SSLRandomSeed;
        }
    }

    public String getFIPSMode() {
        return FIPSMode;
    }

    public void setFIPSMode(String FIPSMode) {
        if (!FIPSMode.equals(AprLifecycleListener.FIPSMode)) {
            // Ensure that the FIPS mode is consistent with that used for SSL init
            if (sslInitialized) {
                throw new IllegalStateException(
                        sm.getString("aprListener.tooLateForFIPSMode"));
            }

            AprLifecycleListener.FIPSMode = FIPSMode;
        }
    }

    public boolean isFIPSModeActive() {
        return fipsModeActive;
    }

    public void setUseOpenSSL(boolean useOpenSSL) {
        if (useOpenSSL != AprStatus.getUseOpenSSL()) {
            AprStatus.setUseOpenSSL(useOpenSSL);
        }
    }

    public static boolean getUseOpenSSL() {
        return AprStatus.getUseOpenSSL();
    }

    public static boolean isInstanceCreated() {
        return AprStatus.isInstanceCreated();
    }

}
