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


import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.openssl.OpenSSLStatus;
import org.apache.tomcat.util.res.StringManager;


/**
 * Implementation of <code>LifecycleListener</code> that will do the global initialization of OpenSSL according to
 * specified configuration parameters. Using the listener is completely optional, but is needed for configuration and
 * full cleanup of a few native memory allocations.
 */
public class OpenSSLLifecycleListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(OpenSSLLifecycleListener.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(OpenSSLLifecycleListener.class);

    protected static final Object lock = new Object();

    public static boolean isAvailable() {
        // https://bz.apache.org/bugzilla/show_bug.cgi?id=48613
        if (OpenSSLStatus.isInstanceCreated()) {
            synchronized (lock) {
                if (!JreCompat.isJre22Available()) {
                    OpenSSLStatus.setInitialized(true);
                } else {
                    try {
                        Class<?> openSSLLibraryClass =
                                Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
                        openSSLLibraryClass.getMethod("init").invoke(null);
                    } catch (Throwable t) {
                        t = ExceptionUtils.unwrapInvocationTargetException(t);
                        ExceptionUtils.handleThrowable(t);
                        log.error(sm.getString("openssllistener.sslInit"), t);
                    }
                }
            }
        }
        return OpenSSLStatus.isAvailable();
    }

    public OpenSSLLifecycleListener() {
        OpenSSLStatus.setInstanceCreated(true);
    }

    // ---------------------------------------------- LifecycleListener Methods

    /**
     * Primary entry point for startup and shutdown events.
     *
     * @param event The event that has occurred
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        boolean initError = false;
        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
            if (!(event.getLifecycle() instanceof Server)) {
                log.warn(sm.getString("listener.notServer", event.getLifecycle().getClass().getSimpleName()));
            }
            synchronized (lock) {
                if (!JreCompat.isJre22Available()) {
                    log.info(sm.getString("openssllistener.java22"));
                    OpenSSLStatus.setInitialized(true);
                    return;
                }
                try {
                    Class<?> openSSLLibraryClass =
                            Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
                    openSSLLibraryClass.getMethod("init").invoke(null);
                } catch (Throwable t) {
                    t = ExceptionUtils.unwrapInvocationTargetException(t);
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("openssllistener.sslInit"), t);
                    initError = true;
                }
                // Failure to initialize FIPS mode is fatal
                if (!(null == getFIPSMode() || "off".equalsIgnoreCase(getFIPSMode())) && !isFIPSModeActive()) {
                    String errorMessage = sm.getString("openssllistener.initializeFIPSFailed");
                    Error e = new Error(errorMessage);
                    // Log here, because thrown error might be not logged
                    log.fatal(errorMessage, e);
                    initError = true;
                }
            }
        }
        if (initError || Lifecycle.AFTER_DESTROY_EVENT.equals(event.getType())) {
            synchronized (lock) {
                if (!JreCompat.isJre22Available()) {
                    return;
                }
                // Note: Without the listener, destroy will never be called (which is not a significant problem)
                try {
                    Class<?> openSSLLibraryClass =
                            Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
                    openSSLLibraryClass.getMethod("destroy").invoke(null);
                } catch (Throwable t) {
                    t = ExceptionUtils.unwrapInvocationTargetException(t);
                    ExceptionUtils.handleThrowable(t);
                    log.info(sm.getString("openssllistener.destroy"));
                }
            }
        }

    }

    public String getSSLEngine() {
        if (JreCompat.isJre22Available()) {
            try {
                Class<?> openSSLLibraryClass =
                        Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
                return (String) openSSLLibraryClass.getMethod("getSSLEngine").invoke(null);
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
            }
        }
        return null;
    }

    public void setSSLEngine(String SSLEngine) {
        if (JreCompat.isJre22Available()) {
            try {
                Class<?> openSSLLibraryClass =
                        Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
                openSSLLibraryClass.getMethod("setSSLEngine").invoke(null, SSLEngine);
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
            }
        }
    }

    public String getSSLRandomSeed() {
        if (JreCompat.isJre22Available()) {
            try {
                Class<?> openSSLLibraryClass =
                        Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
                return (String) openSSLLibraryClass.getMethod("getSSLRandomSeed").invoke(null);
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
            }
        }
        return null;
    }

    public void setSSLRandomSeed(String SSLRandomSeed) {
        if (JreCompat.isJre22Available()) {
            try {
                Class<?> openSSLLibraryClass =
                        Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
                openSSLLibraryClass.getMethod("setSSLRandomSeed").invoke(null, SSLRandomSeed);
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
            }
        }
    }

    public String getFIPSMode() {
        if (JreCompat.isJre22Available()) {
            try {
                Class<?> openSSLLibraryClass =
                        Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
                return (String) openSSLLibraryClass.getMethod("getFIPSMode").invoke(null);
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
            }
        }
        return null;
    }

    public void setFIPSMode(String FIPSMode) {
        if (JreCompat.isJre22Available()) {
            try {
                Class<?> openSSLLibraryClass =
                        Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
                openSSLLibraryClass.getMethod("setFIPSMode").invoke(null, FIPSMode);
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
            }
        }
    }

    public boolean isFIPSModeActive() {
        if (JreCompat.isJre22Available()) {
            try {
                Class<?> openSSLLibraryClass =
                        Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
                return ((Boolean) openSSLLibraryClass.getMethod("isFIPSModeActive").invoke(null)).booleanValue();
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
            }
        }
        return false;
    }

    public void setUseOpenSSL(boolean useOpenSSL) {
        if (useOpenSSL != OpenSSLStatus.getUseOpenSSL()) {
            OpenSSLStatus.setUseOpenSSL(useOpenSSL);
        }
    }

    public static boolean getUseOpenSSL() {
        return OpenSSLStatus.getUseOpenSSL();
    }

}
