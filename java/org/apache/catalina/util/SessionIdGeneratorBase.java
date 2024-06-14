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
package org.apache.catalina.util;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.SessionIdGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public abstract class SessionIdGeneratorBase extends LifecycleBase implements SessionIdGenerator {

    private final Log log = LogFactory.getLog(SessionIdGeneratorBase.class); // must not be static

    private static final StringManager sm = StringManager.getManager("org.apache.catalina.util");

    public static final String DEFAULT_SECURE_RANDOM_ALGORITHM;

    static {
        /*
         * The default is normally SHA1PRNG. This was chosen because a) it is quick and b) it available by default in
         * all JREs. However, it may not be available in some configurations such as those that use a FIPS certified
         * provider. In those cases, use the platform default.
         */
        Set<String> algorithmNames = Security.getAlgorithms("SecureRandom");
        if (algorithmNames.contains("SHA1PRNG")) {
            DEFAULT_SECURE_RANDOM_ALGORITHM = "SHA1PRNG";
        } else {
            // Empty string - This will trigger the use of the platform default.
            DEFAULT_SECURE_RANDOM_ALGORITHM = "";
            Log log = LogFactory.getLog(SessionIdGeneratorBase.class);
            log.warn(sm.getString("sessionIdGeneratorBase.noSHA1PRNG"));
        }
    }

    /**
     * Queue of random number generator objects to be used when creating session identifiers. If the queue is empty when
     * a random number generator is required, a new random number generator object is created. This is designed this way
     * since random number generators use a sync to make them thread-safe and the sync makes using a single object
     * slow(er).
     */
    private final Queue<SecureRandom> randoms = new ConcurrentLinkedQueue<>();

    private String secureRandomClass = null;

    private String secureRandomAlgorithm = DEFAULT_SECURE_RANDOM_ALGORITHM;

    private String secureRandomProvider = null;


    /** Node identifier when in a cluster. Defaults to the empty string. */
    private String jvmRoute = "";


    /** Number of bytes in a session ID. Defaults to 16. */
    private int sessionIdLength = 16;


    /**
     * Get the class name of the {@link SecureRandom} implementation used to generate session IDs.
     *
     * @return The fully qualified class name. {@code null} indicates that the JRE provided {@link SecureRandom}
     *             implementation will be used
     */
    public String getSecureRandomClass() {
        return secureRandomClass;
    }


    /**
     * Specify a non-default {@link SecureRandom} implementation to use. The implementation must be self-seeding and
     * have a zero-argument constructor. If not specified, an instance of {@link SecureRandom} will be generated.
     *
     * @param secureRandomClass The fully-qualified class name
     */
    public void setSecureRandomClass(String secureRandomClass) {
        this.secureRandomClass = secureRandomClass;
    }


    /**
     * Get the name of the algorithm used to create the {@link SecureRandom} instances which generate new session IDs.
     *
     * @return The name of the algorithm. {@code null} or the empty string means that platform default will be used
     */
    public String getSecureRandomAlgorithm() {
        return secureRandomAlgorithm;
    }


    /**
     * Specify a non-default algorithm to use to create instances of {@link SecureRandom} which are used to generate
     * session IDs. If no algorithm is specified, SHA1PRNG will be used. If SHA1PRNG is not available, the platform
     * default will be used. To use the platform default (which may be SHA1PRNG), specify {@code null} or the empty
     * string. If an invalid algorithm and/or provider is specified the {@link SecureRandom} instances will be created
     * using the defaults for this {@link SessionIdGenerator} implementation. If that fails, the {@link SecureRandom}
     * instances will be created using platform defaults.
     *
     * @param secureRandomAlgorithm The name of the algorithm
     */
    public void setSecureRandomAlgorithm(String secureRandomAlgorithm) {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }


    /**
     * Get the name of the provider used to create the {@link SecureRandom} instances which generate new session IDs.
     *
     * @return The name of the provider. {@code null} or the empty string means that platform default will be used
     */
    public String getSecureRandomProvider() {
        return secureRandomProvider;
    }


    /**
     * Specify a non-default provider to use to create instances of {@link SecureRandom} which are used to generate
     * session IDs. If no provider is specified, the platform default is used. To use the platform default specify
     * {@code null} or the empty string. If an invalid algorithm and/or provider is specified the {@link SecureRandom}
     * instances will be created using the defaults for this {@link SessionIdGenerator} implementation. If that fails,
     * the {@link SecureRandom} instances will be created using platform defaults.
     *
     * @param secureRandomProvider The name of the provider
     */
    public void setSecureRandomProvider(String secureRandomProvider) {
        this.secureRandomProvider = secureRandomProvider;
    }


    @Override
    public String getJvmRoute() {
        return jvmRoute;
    }


    @Override
    public void setJvmRoute(String jvmRoute) {
        this.jvmRoute = jvmRoute;
    }


    @Override
    public int getSessionIdLength() {
        return sessionIdLength;
    }


    @Override
    public void setSessionIdLength(int sessionIdLength) {
        this.sessionIdLength = sessionIdLength;
    }

    @Override
    public String generateSessionId() {
        return generateSessionId(jvmRoute);
    }


    protected void getRandomBytes(byte bytes[]) {

        SecureRandom random = randoms.poll();
        if (random == null) {
            random = createSecureRandom();
        }
        random.nextBytes(bytes);
        randoms.add(random);
    }


    /**
     * Create a new random number generator instance we should use for generating session identifiers.
     */
    private SecureRandom createSecureRandom() {

        SecureRandom result = null;

        long t1 = System.currentTimeMillis();
        if (secureRandomClass != null) {
            try {
                // Construct and seed a new random number generator
                Class<?> clazz = Class.forName(secureRandomClass);
                result = (SecureRandom) clazz.getConstructor().newInstance();
            } catch (Exception e) {
                log.error(sm.getString("sessionIdGeneratorBase.random", secureRandomClass), e);
            }
        }

        boolean error = false;
        if (result == null) {
            // No secureRandomClass or creation failed. Use SecureRandom.
            try {
                if (secureRandomProvider != null && secureRandomProvider.length() > 0) {
                    result = SecureRandom.getInstance(secureRandomAlgorithm, secureRandomProvider);
                } else if (secureRandomAlgorithm != null && secureRandomAlgorithm.length() > 0) {
                    result = SecureRandom.getInstance(secureRandomAlgorithm);
                }
            } catch (NoSuchAlgorithmException e) {
                error = true;
                log.error(sm.getString("sessionIdGeneratorBase.randomAlgorithm", secureRandomAlgorithm), e);
            } catch (NoSuchProviderException e) {
                error = true;
                log.error(sm.getString("sessionIdGeneratorBase.randomProvider", secureRandomProvider), e);
            }
        }

        // In theory, DEFAULT_SECURE_RANDOM_ALGORITHM should always work but
        // with custom providers that might not be the case.
        if (result == null && error && !DEFAULT_SECURE_RANDOM_ALGORITHM.equals(secureRandomAlgorithm)) {
            // Invalid provider / algorithm - use the default
            try {
                result = SecureRandom.getInstance(DEFAULT_SECURE_RANDOM_ALGORITHM);
            } catch (NoSuchAlgorithmException e) {
                log.error(sm.getString("sessionIdGeneratorBase.randomAlgorithm", secureRandomAlgorithm), e);
            }
        }

        if (result == null) {
            // Nothing works - use platform default
            result = new SecureRandom();
        }

        // Force seeding to take place
        result.nextInt();

        long t2 = System.currentTimeMillis();
        if ((t2 - t1) > 100) {
            log.warn(sm.getString("sessionIdGeneratorBase.createRandom", result.getAlgorithm(), Long.valueOf(t2 - t1)));
        }
        return result;
    }


    @Override
    protected void initInternal() throws LifecycleException {
        // NO-OP
    }


    @Override
    protected void startInternal() throws LifecycleException {
        // Ensure SecureRandom has been initialised
        generateSessionId();

        setState(LifecycleState.STARTING);
    }


    @Override
    protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
        randoms.clear();
    }


    @Override
    protected void destroyInternal() throws LifecycleException {
        // NO-OP
    }
}
