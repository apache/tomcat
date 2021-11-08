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
package org.apache.tomcat.util.net.openssl.panama;


import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SegmentAllocator;

import static org.apache.tomcat.util.openssl.openssl_h.*;

import java.security.SecureRandom;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;



/**
 * Implementation of <code>LifecycleListener</code> that will do the global
 * initialization of OpenSSL according to specified configuration parameters.
 * Using the listener is completely optional, but is needed for configuration.
 */
public class OpenSSLLifecycleListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(OpenSSLLifecycleListener.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(OpenSSLLifecycleListener.class);


    // ---------------------------------------------- Properties
    protected static String SSLEngine = "on"; //default on
    protected static String FIPSMode = "off"; // default off, valid only when SSLEngine="on"
    protected static String SSLRandomSeed = "builtin";
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

        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
            synchronized (lock) {
                try {
                    init();
                } catch (Throwable t) {
                    t = ExceptionUtils.unwrapInvocationTargetException(t);
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("listener.sslInit"), t);
                }
                // Failure to initialize FIPS mode is fatal
                if (!(null == FIPSMode || "off".equalsIgnoreCase(FIPSMode)) && !isFIPSModeActive()) {
                    String errorMessage = sm.getString("listener.initializeFIPSFailed");
                    Error e = new Error(errorMessage);
                    // Log here, because thrown error might be not logged
                    log.fatal(errorMessage, e);
                    throw e;
                }
            }
        } else if (Lifecycle.AFTER_DESTROY_EVENT.equals(event.getType())) {
            synchronized (lock) {
                if (!OpenSSLStatus.isAvailable()) {
                    return;
                }
                try {
                    OpenSSLStatus.setAvailable(false);
                    OpenSSLStatus.setInitialized(false);
                    fipsModeActive = false;
                } catch (Throwable t) {
                    t = ExceptionUtils.unwrapInvocationTargetException(t);
                    ExceptionUtils.handleThrowable(t);
                    log.info(sm.getString("listener.destroy"));
                }
            }
        }

    }

    static MemoryAddress enginePointer = MemoryAddress.NULL;

    static void initLibrary() {
        synchronized (OpenSSLStatus.class) {
            if (OpenSSLStatus.isLibraryInitialized()) {
                return;
            }
            OpenSSLStatus.setLibraryInitialized(true);
            OPENSSL_init_ssl(OPENSSL_INIT_ENGINE_ALL_BUILTIN(), MemoryAddress.NULL);
        }
    }

    static void init() throws Exception {

        if (OpenSSLStatus.isInitialized()) {
            return;
        }
        OpenSSLStatus.setInitialized(true);

        if ("off".equalsIgnoreCase(SSLEngine)) {
            return;
        }

        var scope = ResourceScope.globalScope();
        var allocator = SegmentAllocator.ofScope(scope);

        // FIXME: implement ssl_init_cleanup to use if there's an error or when the library is unloaded, possibly only ENGINE_free

        // Main library init
        initLibrary();

        // Setup engine
        String engineName = "on".equalsIgnoreCase(SSLEngine) ? null : SSLEngine;
        if (engineName != null) {
            if ("auto".equals(engineName)) {
                ENGINE_register_all_complete();
            } else {
                var engine = CLinker.toCString(engineName, scope);
                enginePointer = ENGINE_by_id(engine);
                if (MemoryAddress.NULL.equals(enginePointer)) {
                    enginePointer = ENGINE_by_id(CLinker.toCString("dynamic", scope));
                    if (enginePointer != null) {
                        if (ENGINE_ctrl_cmd_string(enginePointer, CLinker.toCString("SO_PATH", scope), engine, 0) == 0
                                || ENGINE_ctrl_cmd_string(enginePointer, CLinker.toCString("LOAD", scope),
                                        MemoryAddress.NULL, 0) == 0) {
                            // Engine load error
                            ENGINE_free(enginePointer);
                            enginePointer = MemoryAddress.NULL;
                        }
                    }
                }
                if (!MemoryAddress.NULL.equals(enginePointer)) {
                    if (ENGINE_set_default(enginePointer, ENGINE_METHOD_ALL()) == 0) {
                        // Engine load error
                        ENGINE_free(enginePointer);
                        enginePointer = MemoryAddress.NULL;
                    }
                }
                if (MemoryAddress.NULL.equals(enginePointer)) {
                    throw new IllegalStateException(sm.getString("listener.engineError"));
                }
            }
        }

        // Set the random seed, translated to the Java way
        boolean seedDone = false;
        if (SSLRandomSeed != null || SSLRandomSeed.length() != 0 || !"builtin".equals(SSLRandomSeed)) {
            var randomSeed = CLinker.toCString(SSLRandomSeed, scope);
            seedDone = RAND_load_file(randomSeed, 128) > 0;
        }
        if (!seedDone) {
            // Use a regular random to get some bytes
            SecureRandom random = new SecureRandom();
            byte[] randomBytes = random.generateSeed(128);
            RAND_seed(allocator.allocateArray(CLinker.C_CHAR, randomBytes), 128);
        }

        // init_dh_params is done in OpenSSLContext static init
        
        // FIXME: Keylog ?

        if (!(null == FIPSMode || "off".equalsIgnoreCase(FIPSMode))) {

            fipsModeActive = false;

            final boolean enterFipsMode;
            int fipsModeState = FIPS_mode();

            if(log.isDebugEnabled()) {
                log.debug(sm.getString("listener.currentFIPSMode",
                        Integer.valueOf(fipsModeState)));
            }

            if ("on".equalsIgnoreCase(FIPSMode)) {
                if (fipsModeState == FIPS_ON) {
                    log.info(sm.getString("listener.skipFIPSInitialization"));
                    fipsModeActive = true;
                    enterFipsMode = false;
                } else {
                    enterFipsMode = true;
                }
            } else if ("require".equalsIgnoreCase(FIPSMode)) {
                if (fipsModeState == FIPS_ON) {
                    fipsModeActive = true;
                    enterFipsMode = false;
                } else {
                    throw new IllegalStateException(
                            sm.getString("listener.requireNotInFIPSMode"));
                }
            } else if ("enter".equalsIgnoreCase(FIPSMode)) {
                if (fipsModeState == FIPS_OFF) {
                    enterFipsMode = true;
                } else {
                    throw new IllegalStateException(sm.getString(
                            "listener.enterAlreadyInFIPSMode",
                            Integer.valueOf(fipsModeState)));
                }
            } else {
                throw new IllegalArgumentException(sm.getString(
                        "listener.wrongFIPSMode", FIPSMode));
            }

            if (enterFipsMode) {
                log.info(sm.getString("listener.initializingFIPS"));

                fipsModeState = FIPS_mode_set(FIPS_ON);
                if (fipsModeState != FIPS_ON) {
                    // This case should be handled by the native method,
                    // but we'll make absolutely sure, here.
                    String message = sm.getString("listener.initializeFIPSFailed");
                    log.error(message);
                    throw new IllegalStateException(message);
                }

                fipsModeActive = true;
                log.info(sm.getString("listener.initializeFIPSSuccess"));
            }
        }

        log.info(sm.getString("listener.initializedOpenSSL", new String(OPENSSL_VERSION_TEXT().toByteArray())));
        OpenSSLStatus.setAvailable(true);
    }

    public String getSSLEngine() {
        return SSLEngine;
    }

    public void setSSLEngine(String SSLEngine) {
        if (!SSLEngine.equals(OpenSSLLifecycleListener.SSLEngine)) {
            // Ensure that the SSLEngine is consistent with that used for SSL init
            if (OpenSSLStatus.isInitialized()) {
                throw new IllegalStateException(
                        sm.getString("listener.tooLateForSSLEngine"));
            }

            OpenSSLLifecycleListener.SSLEngine = SSLEngine;
        }
    }

    public String getSSLRandomSeed() {
        return SSLRandomSeed;
    }

    public void setSSLRandomSeed(String SSLRandomSeed) {
        if (!SSLRandomSeed.equals(OpenSSLLifecycleListener.SSLRandomSeed)) {
            // Ensure that the random seed is consistent with that used for SSL init
            if (OpenSSLStatus.isInitialized()) {
                throw new IllegalStateException(
                        sm.getString("listener.tooLateForSSLRandomSeed"));
            }

            OpenSSLLifecycleListener.SSLRandomSeed = SSLRandomSeed;
        }
    }

    public String getFIPSMode() {
        return FIPSMode;
    }

    public void setFIPSMode(String FIPSMode) {
        if (!FIPSMode.equals(OpenSSLLifecycleListener.FIPSMode)) {
            // Ensure that the FIPS mode is consistent with that used for SSL init
            if (OpenSSLStatus.isInitialized()) {
                throw new IllegalStateException(
                        sm.getString("listener.tooLateForFIPSMode"));
            }

            OpenSSLLifecycleListener.FIPSMode = FIPSMode;
        }
    }

    public boolean isFIPSModeActive() {
        return fipsModeActive;
    }

    public static boolean isInstanceCreated() {
        return OpenSSLStatus.isInstanceCreated();
    }

}
