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


import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.apache.tomcat.util.openssl.openssl_h.*;
import static org.apache.tomcat.util.openssl.openssl_h_Compatibility.*;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.openssl.OpenSSLStatus;
import org.apache.tomcat.util.net.openssl.ciphers.OpenSSLCipherConfigurationParser;
import org.apache.tomcat.util.openssl.openssl_h_Compatibility;
import org.apache.tomcat.util.res.StringManager;



/**
 * Implementation of a global initialization of OpenSSL according to specified
 * configuration parameters.
 * Using this from a listener is completely optional, but is needed for
 * configuration and full cleanup of a few native memory allocations.
 */
public class OpenSSLLibrary {

    private static final Log log = LogFactory.getLog(OpenSSLLibrary.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(OpenSSLLibrary.class);


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

    // Guarded by lock
    private static int referenceCount = 0;

    static MemorySegment enginePointer = MemorySegment.NULL;

    static void initLibrary() {
        synchronized (lock) {
            if (OpenSSLStatus.isLibraryInitialized()) {
                return;
            }
            long initParam = (OpenSSL_version_num() >= 0x3000000fL) ? 0 : OPENSSL_INIT_ENGINE_ALL_BUILTIN();
            OPENSSL_init_ssl(initParam, MemorySegment.NULL);
            OpenSSLStatus.setLibraryInitialized(true);
        }
    }

    /*
    { BN_get_rfc3526_prime_8192, NULL, 6145 },
    { BN_get_rfc3526_prime_6144, NULL, 4097 },
    { BN_get_rfc3526_prime_4096, NULL, 3073 },
    { BN_get_rfc3526_prime_3072, NULL, 2049 },
    { BN_get_rfc3526_prime_2048, NULL, 1025 },
    { BN_get_rfc2409_prime_1024, NULL, 0 }
     */
    static final class DHParam {
        final MemorySegment dh;
        final int min;
        private DHParam(MemorySegment dh, int min) {
            this.dh = dh;
            this.min = min;
        }
    }
    static final DHParam[] dhParameters = new DHParam[6];

    private static void initDHParameters() {
        var dh = DH_new();
        var p = BN_get_rfc3526_prime_8192(MemorySegment.NULL);
        var g = BN_new();
        BN_set_word(g, 2);
        DH_set0_pqg(dh, p, MemorySegment.NULL, g);
        dhParameters[0] = new DHParam(dh, 6145);
        dh = DH_new();
        p = BN_get_rfc3526_prime_6144(MemorySegment.NULL);
        g = BN_new();
        BN_set_word(g, 2);
        DH_set0_pqg(dh, p, MemorySegment.NULL, g);
        dhParameters[1] = new DHParam(dh, 4097);
        dh = DH_new();
        p = BN_get_rfc3526_prime_4096(MemorySegment.NULL);
        g = BN_new();
        BN_set_word(g, 2);
        DH_set0_pqg(dh, p, MemorySegment.NULL, g);
        dhParameters[2] = new DHParam(dh, 3073);
        dh = DH_new();
        p = BN_get_rfc3526_prime_3072(MemorySegment.NULL);
        g = BN_new();
        BN_set_word(g, 2);
        DH_set0_pqg(dh, p, MemorySegment.NULL, g);
        dhParameters[3] = new DHParam(dh, 2049);
        dh = DH_new();
        p = BN_get_rfc3526_prime_2048(MemorySegment.NULL);
        g = BN_new();
        BN_set_word(g, 2);
        DH_set0_pqg(dh, p, MemorySegment.NULL, g);
        dhParameters[4] = new DHParam(dh, 1025);
        dh = DH_new();
        p = BN_get_rfc2409_prime_1024(MemorySegment.NULL);
        g = BN_new();
        BN_set_word(g, 2);
        DH_set0_pqg(dh, p, MemorySegment.NULL, g);
        dhParameters[5] = new DHParam(dh, 0);
    }

    private static void freeDHParameters() {
        for (int i = 0; i < dhParameters.length; i++) {
            if (dhParameters[i] != null) {
                MemorySegment dh = dhParameters[i].dh;
                if (dh != null && !MemorySegment.NULL.equals(dh)) {
                    DH_free(dh);
                    dhParameters[i] = null;
                }
            }
        }
    }

    public static void init() {
        synchronized (lock) {

            if (referenceCount++ != 0) {
                // Already loaded (note test is performed before reference count is incremented)
                return;
            }
            if (OpenSSLStatus.isInitialized()) {
                return;
            }
            OpenSSLStatus.setInitialized(true);

            if ("off".equalsIgnoreCase(SSLEngine)) {
                return;
            }

            try (var memorySession = Arena.ofConfined()) {

                // Main library init
                initLibrary();

                OpenSSLStatus.setVersion(OpenSSL_version_num());
                if (openssl_h_Compatibility.OPENSSL3) {
                    OpenSSLStatus.setName(OpenSSLStatus.Name.OPENSSL3);
                } else if (openssl_h_Compatibility.OPENSSL) {
                    OpenSSLStatus.setName(OpenSSLStatus.Name.OPENSSL);
                } else if (openssl_h_Compatibility.LIBRESSL) {
                    OpenSSLStatus.setName(OpenSSLStatus.Name.LIBRESSL);
                } else if (openssl_h_Compatibility.BORINGSSL) {
                    OpenSSLStatus.setName(OpenSSLStatus.Name.BORINGSSL);
                }

                // OpenSSL 3 onwards uses providers

                // Setup engine
                String engineName = "on".equalsIgnoreCase(SSLEngine) ? null : SSLEngine;
                if (!openssl_h_Compatibility.OPENSSL3 && !openssl_h_Compatibility.BORINGSSL && engineName != null) {
                    if ("auto".equals(engineName)) {
                        ENGINE_register_all_complete();
                    } else {
                        var engine = memorySession.allocateFrom(engineName);
                        enginePointer = ENGINE_by_id(engine);
                        if (MemorySegment.NULL.equals(enginePointer)) {
                            enginePointer = ENGINE_by_id(memorySession.allocateFrom("dynamic"));
                            if (enginePointer != null) {
                                if (ENGINE_ctrl_cmd_string(enginePointer, memorySession.allocateFrom("SO_PATH"), engine, 0) == 0
                                        || ENGINE_ctrl_cmd_string(enginePointer, memorySession.allocateFrom("LOAD"),
                                                MemorySegment.NULL, 0) == 0) {
                                    // Engine load error
                                    ENGINE_free(enginePointer);
                                    enginePointer = MemorySegment.NULL;
                                }
                            }
                        }
                        if (!MemorySegment.NULL.equals(enginePointer)) {
                            if (ENGINE_set_default(enginePointer, ENGINE_METHOD_ALL()) == 0) {
                                // Engine load error
                                ENGINE_free(enginePointer);
                                enginePointer = MemorySegment.NULL;
                            }
                        }
                        if (MemorySegment.NULL.equals(enginePointer)) {
                            throw new IllegalStateException(sm.getString("openssllibrary.engineError"));
                        }
                    }
                }

                // Set the random seed, translated to the Java way
                boolean seedDone = false;
                if (SSLRandomSeed != null && SSLRandomSeed.length() != 0 && !"builtin".equals(SSLRandomSeed)) {
                    var randomSeed = memorySession.allocateFrom(SSLRandomSeed);
                    seedDone = RAND_load_file(randomSeed, 128) > 0;
                    if (!seedDone) {
                        log.warn(sm.getString("openssllibrary.errorSettingSSLRandomSeed", SSLRandomSeed, OpenSSLLibrary.getLastError()));
                    }
                }
                if (!seedDone) {
                    // Use a regular random to get some bytes
                    SecureRandom random = new SecureRandom();
                    byte[] randomBytes = random.generateSeed(128);
                    RAND_seed(memorySession.allocateFrom(ValueLayout.JAVA_BYTE, randomBytes), 128);
                }

                if (!openssl_h_Compatibility.OPENSSL3 && !openssl_h_Compatibility.BORINGSSL) {
                    initDHParameters();
                }

                if (openssl_h_Compatibility.OPENSSL3 || !(null == FIPSMode || "off".equalsIgnoreCase(FIPSMode))) {
                    fipsModeActive = false;
                    final boolean enterFipsMode;
                    int fipsModeState = FIPS_OFF;
                    if (openssl_h_Compatibility.OPENSSL3) {
                        var md = EVP_MD_fetch(MemorySegment.NULL, memorySession.allocateFrom("SHA-512"), MemorySegment.NULL);
                        var provider = EVP_MD_get0_provider(md);
                        String name = OSSL_PROVIDER_get0_name(provider).getString(0);
                        EVP_MD_free(md);
                        if ("fips".equals(name)) {
                            fipsModeState = FIPS_ON;
                        }
                    } else {
                        fipsModeState = FIPS_mode();
                    }

                    if(log.isDebugEnabled()) {
                        log.debug(sm.getString("openssllibrary.currentFIPSMode", Integer.valueOf(fipsModeState)));
                    }

                    if (null == FIPSMode || "off".equalsIgnoreCase(FIPSMode)) {
                        if (fipsModeState == FIPS_ON) {
                            fipsModeActive = true;
                        }
                        enterFipsMode = false;
                    } else if ("on".equalsIgnoreCase(FIPSMode)) {
                        if (fipsModeState == FIPS_ON) {
                            if (!openssl_h_Compatibility.OPENSSL3) {
                                log.info(sm.getString("openssllibrary.skipFIPSInitialization"));
                            }
                            fipsModeActive = true;
                            enterFipsMode = false;
                        } else {
                            if (openssl_h_Compatibility.OPENSSL3) {
                                throw new IllegalStateException(sm.getString("openssllibrary.FIPSProviderNotDefault", FIPSMode));
                            } else {
                                enterFipsMode = true;
                            }
                        }
                    } else if ("require".equalsIgnoreCase(FIPSMode)) {
                        if (fipsModeState == FIPS_ON) {
                            fipsModeActive = true;
                            enterFipsMode = false;
                        } else {
                            if (openssl_h_Compatibility.OPENSSL3) {
                                throw new IllegalStateException(sm.getString("openssllibrary.FIPSProviderNotDefault", FIPSMode));
                            } else {
                                throw new IllegalStateException(sm.getString("openssllibrary.requireNotInFIPSMode"));
                            }
                        }
                    } else if ("enter".equalsIgnoreCase(FIPSMode)) {
                        if (fipsModeState == FIPS_OFF) {
                            if (openssl_h_Compatibility.OPENSSL3) {
                                throw new IllegalStateException(sm.getString("openssllibrary.FIPSProviderNotDefault", FIPSMode));
                            } else {
                                enterFipsMode = true;
                            }
                        } else {
                            if (openssl_h_Compatibility.OPENSSL3) {
                                fipsModeActive = true;
                                enterFipsMode = false;
                            } else {
                                throw new IllegalStateException(sm.getString(
                                        "openssllibrary.enterAlreadyInFIPSMode", Integer.valueOf(fipsModeState)));
                            }
                        }
                    } else {
                        throw new IllegalArgumentException(sm.getString(
                                "openssllibrary.wrongFIPSMode", FIPSMode));
                    }

                    if (enterFipsMode) {
                        log.info(sm.getString("openssllibrary.initializingFIPS"));

                        fipsModeState = FIPS_mode_set(FIPS_ON);
                        if (fipsModeState != FIPS_ON) {
                            // This case should be handled by the native method,
                            // but we'll make absolutely sure, here.
                            String message = sm.getString("openssllibrary.initializeFIPSFailed");
                            log.error(message);
                            throw new IllegalStateException(message);
                        }

                        fipsModeActive = true;
                        log.info(sm.getString("openssllibrary.initializeFIPSSuccess"));
                    }

                    if (openssl_h_Compatibility.OPENSSL3 && fipsModeActive) {
                        log.info(sm.getString("aprListener.usingFIPSProvider"));
                    }
                }

                log.info(sm.getString("openssllibrary.initializedOpenSSL", OpenSSL_version(0).getString(0)));
                OpenSSLStatus.setAvailable(true);
            }
        }
    }


    public static void destroy() {
        synchronized (lock) {
            if (!OpenSSLStatus.isInitialized()) {
                return;
            }
            if (--referenceCount != 0) {
                // Still being used (note test is performed after reference count is decremented)
                return;
            }
            OpenSSLStatus.setAvailable(false);

            try {
                if (OpenSSL_version_num() < 0x3000000fL) {
                    // There could be unreferenced SSL_CTX still waiting for GC
                    System.gc();
                    freeDHParameters();
                    if (!MemorySegment.NULL.equals(enginePointer)) {
                        ENGINE_free(enginePointer);
                        enginePointer = MemorySegment.NULL;
                    }
                    FIPS_mode_set(0);
                }
            } finally {
                OpenSSLStatus.setInitialized(false);
                fipsModeActive = false;
            }
        }
    }

    public static String getSSLEngine() {
        return SSLEngine;
    }

    public static void setSSLEngine(String SSLEngine) {
        if (!SSLEngine.equals(OpenSSLLibrary.SSLEngine)) {
            // Ensure that the SSLEngine is consistent with that used for SSL init
            if (OpenSSLStatus.isInitialized()) {
                throw new IllegalStateException(
                        sm.getString("openssllibrary.tooLateForSSLEngine"));
            }

            OpenSSLLibrary.SSLEngine = SSLEngine;
        }
    }

    public static String getSSLRandomSeed() {
        return SSLRandomSeed;
    }

    public static void setSSLRandomSeed(String SSLRandomSeed) {
        if (!SSLRandomSeed.equals(OpenSSLLibrary.SSLRandomSeed)) {
            // Ensure that the random seed is consistent with that used for SSL init
            if (OpenSSLStatus.isInitialized()) {
                throw new IllegalStateException(
                        sm.getString("openssllibrary.tooLateForSSLRandomSeed"));
            }

            OpenSSLLibrary.SSLRandomSeed = SSLRandomSeed;
        }
    }

    public static String getFIPSMode() {
        return FIPSMode;
    }

    public static void setFIPSMode(String FIPSMode) {
        if (!FIPSMode.equals(OpenSSLLibrary.FIPSMode)) {
            // Ensure that the FIPS mode is consistent with that used for SSL init
            if (OpenSSLStatus.isInitialized()) {
                throw new IllegalStateException(
                        sm.getString("openssllibrary.tooLateForFIPSMode"));
            }

            OpenSSLLibrary.FIPSMode = FIPSMode;
        }
    }

    public static boolean isFIPSModeActive() {
        return fipsModeActive;
    }

    public static List<String> findCiphers(String ciphers) {
        ArrayList<String> ciphersList = new ArrayList<>();
        try (var localArena = Arena.ofConfined()) {
            initLibrary();
            var sslCtx = SSL_CTX_new(TLS_server_method());
            try {
                openssl_h_Compatibility.SSL_CTX_set_options(sslCtx, SSL_OP_ALL());
                SSL_CTX_set_cipher_list(sslCtx, localArena.allocateFrom(ciphers));
                var ssl = SSL_new(sslCtx);
                SSL_set_accept_state(ssl);
                try {
                    for (String c : getCiphers(ssl)) {
                        // Filter out bad input.
                        if (c == null || c.length() == 0 || ciphersList.contains(c)) {
                            continue;
                        }
                        ciphersList.add(OpenSSLCipherConfigurationParser.openSSLToJsse(c));
                    }
                } finally {
                    SSL_free(ssl);
                }
            } finally {
                SSL_CTX_free(sslCtx);
            }
        } catch (Exception e) {
            log.warn(sm.getString("openssllibrary.ciphersFailure"), e);
        }
        return ciphersList;
    }

    static String[] getCiphers(MemorySegment ssl) {
        MemorySegment sk = SSL_get_ciphers(ssl);
        int len = openssl_h_Compatibility.OPENSSL_sk_num(sk);
        if (len <= 0) {
            return null;
        }
        ArrayList<String> ciphers = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            MemorySegment cipher = openssl_h_Compatibility.OPENSSL_sk_value(sk, i);
            MemorySegment cipherName = SSL_CIPHER_get_name(cipher);
            ciphers.add(cipherName.getString(0));
        }
        return ciphers.toArray(new String[0]);
    }

    private static final int OPENSSL_ERROR_MESSAGE_BUFFER_SIZE = 256;

    /**
     * Many calls to SSL methods do not check the last error. Those that do
     * check the last error need to ensure that any previously ignored error is
     * cleared prior to the method call else errors may be falsely reported.
     * Ideally, before any SSL_read, SSL_write, clearLastError should always
     * be called, and getLastError should be called after on any negative or
     * zero result.
     * @return the first error in the stack
     */
    static String getLastError() {
        String sslError = null;
        long error = ERR_get_error();
        if (error != SSL_ERROR_NONE()) {
            try (var localArena = Arena.ofConfined()) {
                do {
                    // Loop until getLastErrorNumber() returns SSL_ERROR_NONE
                    var buf = localArena.allocate(ValueLayout.JAVA_BYTE, OPENSSL_ERROR_MESSAGE_BUFFER_SIZE);
                    ERR_error_string_n(error, buf, OPENSSL_ERROR_MESSAGE_BUFFER_SIZE);
                    String err = buf.getString(0);
                    if (sslError == null) {
                        sslError = err;
                    }
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("engine.openSSLError", Long.toString(error), err));
                    }
                } while ((error = ERR_get_error()) != SSL_ERROR_NONE());
            }
        }
        return sslError;
    }


}
