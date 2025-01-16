/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.compat;

import java.lang.reflect.Field;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

import javax.security.auth.Subject;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * This is the base implementation class for JRE compatibility and provides an implementation based on Java 17.
 * Sub-classes may extend this class and provide alternative implementations for later JRE versions
 */
public class JreCompat {

    private static final Log log = LogFactory.getLog(JreCompat.class);
    private static final StringManager sm = StringManager.getManager(JreCompat.class);

    private static final JreCompat instance;
    private static final boolean graalAvailable;
    private static final boolean jre19Available;
    private static final boolean jre21Available;
    private static final boolean jre22Available;

    private static final Field useCanonCachesField;

    static {
        boolean result = false;
        try {
            Class<?> nativeImageClazz = Class.forName("org.graalvm.nativeimage.ImageInfo");
            result = Boolean.TRUE.equals(nativeImageClazz.getMethod("inImageCode").invoke(null));
        } catch (ClassNotFoundException e) {
            // Must be Graal
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // Should never happen
        }
        graalAvailable = result || System.getProperty("org.graalvm.nativeimage.imagecode") != null;

        // This is Tomcat 11.0.x with a minimum Java version of Java 17.
        // Look for the highest supported JVM first
        if (Jre22Compat.isSupported()) {
            instance = new Jre22Compat();
            jre22Available = true;
            jre21Available = true;
            jre19Available = true;
        } else if (Jre21Compat.isSupported()) {
            instance = new Jre21Compat();
            jre22Available = false;
            jre21Available = true;
            jre19Available = true;
        } else if (Jre19Compat.isSupported()) {
            instance = new Jre19Compat();
            jre22Available = false;
            jre21Available = false;
            jre19Available = true;
        } else {
            instance = new JreCompat();
            jre22Available = false;
            jre21Available = false;
            jre19Available = false;
        }

        Field f1 = null;
        try {
            Class<?> clazz = Class.forName("java.io.FileSystem");
            f1 = clazz.getDeclaredField("useCanonCaches");
            f1.setAccessible(true);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            /*
             * Log at debug level as this will only be an issue if the field needs to be accessed and most
             * configurations will not need to do so. Appropriate warnings will be logged if an attempt is made to use
             * the field when it could not be found/accessed.
             */
            log.debug(sm.getString("jreCompat.useCanonCaches.init"), e);
        }
        useCanonCachesField = f1;
    }


    public static JreCompat getInstance() {
        return instance;
    }


    public static boolean isGraalAvailable() {
        return graalAvailable;
    }


    public static boolean isJre19Available() {
        return jre19Available;
    }


    public static boolean isJre21Available() {
        return jre21Available;
    }


    public static boolean isJre22Available() {
        return jre22Available;
    }


    // Java 17 implementations of Java 19 methods

    /**
     * Obtains the executor, if any, used to create the provided thread.
     *
     * @param thread The thread to examine
     *
     * @return The executor, if any, that created the provided thread
     *
     * @throws NoSuchFieldException     If a field used via reflection to obtain the executor cannot be found
     * @throws SecurityException        If a security exception occurs while trying to identify the executor
     * @throws IllegalArgumentException If the instance object does not match the class of the field when obtaining a
     *                                      field value via reflection
     * @throws IllegalAccessException   If a field is not accessible due to access restrictions
     */
    public Object getExecutor(Thread thread)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

        Object result = null;

        // Runnable wrapped by Thread
        // "target" in Sun/Oracle JDK
        // "runnable" in IBM JDK
        // "action" in Apache Harmony
        Object target = null;
        for (String fieldName : new String[] { "target", "runnable", "action" }) {
            try {
                Field targetField = thread.getClass().getDeclaredField(fieldName);
                targetField.setAccessible(true);
                target = targetField.get(thread);
                break;
            } catch (NoSuchFieldException nfe) {
                continue;
            }
        }

        // "java.util.concurrent" code is in public domain,
        // so all implementations are similar including our
        // internal fork.
        if (target != null && target.getClass().getCanonicalName() != null && (target.getClass().getCanonicalName()
                .equals("org.apache.tomcat.util.threads.ThreadPoolExecutor.Worker") ||
                target.getClass().getCanonicalName().equals("java.util.concurrent.ThreadPoolExecutor.Worker"))) {
            Field executorField = target.getClass().getDeclaredField("this$0");
            executorField.setAccessible(true);
            result = executorField.get(target);
        }

        return result;
    }


    // Java 17 implementations of Java 21 methods

    /**
     * Create a thread builder for virtual threads using the given name to name the threads.
     *
     * @param name The base name for the threads
     *
     * @return The thread buidler for virtual threads
     */
    public Object createVirtualThreadBuilder(String name) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noVirtualThreads"));
    }


    /**
     * Create a thread with the given thread builder and use it to execute the given runnable.
     *
     * @param threadBuilder The thread builder to use to create a thread
     * @param command       The command to run
     */
    public void threadBuilderStart(Object threadBuilder, Runnable command) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noVirtualThreads"));
    }


    /*
     * This is a slightly different usage of JreCompat.
     *
     * Subject.doAs() was deprecated in Java 18 and replaced with Subject.callAs(). As of Java 23, calling
     * Subject.doAs() will trigger an UnsupportedOperationException unless the java.security.manager system property is
     * set. To avoid Tomcat installations using Spnego authentication having to set this value, JreCompat is used to
     * call Subject.callAs() instead.
     *
     * Because Java versions 18 to 22 inclusive support both the old and the new method, the switch over can occur at
     * any Java version from 18 to 22 inclusive. Java 21 onwards was selected as it as an LTS version and that removes
     * the need to add a Jre18Compat class.
     *
     * So, the slightly longer description for this method is:
     *
     * Java 17 implementation of a method replaced between Java 18 and 22 with the replacement method being used by
     * Tomcat when running on Java 21 onwards.
     */

    @SuppressWarnings("removal")
    public <T> T callAs(Subject subject, Callable<T> action) throws CompletionException {
        try {
            return Subject.doAs(subject, new PrivilegedExceptionAction<T>() {

                @Override
                public T run() throws Exception {
                    return action.call();
                }
            });
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }


    /*
     * The behaviour of the canonical file cache varies by Java version.
     *
     * The cache was removed in Java 21 so these methods and the associated code can be removed once the minimum Java
     * version is 21.
     *
     * For 12 <= Java <= 20, the cache was present but disabled by default. Since the user may have changed the default
     * Tomcat has to assume the cache is enabled unless proven otherwise.
     *
     * For Java < 12, the cache was enabled by default. Tomcat assumes the cache is enabled unless proven otherwise.
     */
    public boolean isCanonCachesDisabled() {
        if (useCanonCachesField == null) {
            // No need to log a warning. The warning will be logged when trying to disable the cache.
            return false;
        }
        boolean result = false;
        try {
            result = !((Boolean) useCanonCachesField.get(null)).booleanValue();
        } catch (ReflectiveOperationException e) {
            // No need to log a warning. The warning will be logged when trying to disable the cache.
        }
        return result;
    }


    /**
     * Disable the global canonical file cache.
     *
     * @return {@code true} if the global canonical file cache was already disabled prior to this call or was disabled
     *             as a result of this call, otherwise {@code false}
     */
    public boolean disableCanonCaches() {
        if (useCanonCachesField == null) {
            log.warn(sm.getString("jreCompat.useCanonCaches.none"));
            return false;
        }
        try {
            useCanonCachesField.set(null, Boolean.FALSE);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            log.warn(sm.getString("jreCompat.useCanonCaches.failed"), e);
            return false;
        }
        return true;
    }
}
