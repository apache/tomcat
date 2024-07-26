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
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

import javax.security.auth.Subject;

import org.apache.tomcat.util.res.StringManager;

/**
 * This is the base implementation class for JRE compatibility and provides an
 * implementation based on Java 11. Sub-classes may extend this class and provide
 * alternative implementations for later JRE versions
 */
public class JreCompat {

    private static final JreCompat instance;
    private static final boolean graalAvailable;
    private static final boolean jre16Available;
    private static final boolean jre19Available;
    private static final boolean jre21Available;
    private static final boolean jre22Available;
    private static final StringManager sm = StringManager.getManager(JreCompat.class);

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

        // This is Tomcat 10.1.x with a minimum Java version of Java 11.
        // Look for the highest supported JVM first
        if (Jre22Compat.isSupported()) {
            instance = new Jre22Compat();
            jre22Available = true;
            jre21Available = true;
            jre19Available = true;
            jre16Available = true;
        } else if (Jre21Compat.isSupported()) {
            instance = new Jre21Compat();
            jre22Available = false;
            jre21Available = true;
            jre19Available = true;
            jre16Available = true;
        } else if (Jre19Compat.isSupported()) {
            instance = new Jre19Compat();
            jre22Available = false;
            jre21Available = false;
            jre19Available = true;
            jre16Available = true;
        } else if (Jre16Compat.isSupported()) {
            instance = new Jre16Compat();
            jre22Available = false;
            jre21Available = false;
            jre19Available = false;
            jre16Available = true;
        } else {
            instance = new JreCompat();
            jre22Available = false;
            jre21Available = false;
            jre19Available = false;
            jre16Available = false;
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    public static boolean isGraalAvailable() {
        return graalAvailable;
    }


    public static boolean isJre16Available() {
        return jre16Available;
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


    // Java 11 implementations of Java 16 methods

    /**
     * Return Unix domain socket address for given path.
     * @param path The path
     * @return the socket address
     */
    public SocketAddress getUnixDomainSocketAddress(String path) {
        return null;
    }


    /**
     * Create server socket channel using the Unix domain socket ProtocolFamily.
     * @return the server socket channel
     */
    public ServerSocketChannel openUnixDomainServerSocketChannel() {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noUnixDomainSocket"));
    }


    /**
     * Create socket channel using the Unix domain socket ProtocolFamily.
     * @return the socket channel
     */
    public SocketChannel openUnixDomainSocketChannel() {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noUnixDomainSocket"));
    }


    // Java 11 implementations of Java 19 methods

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


    // Java 11 implementations of Java 21 methods

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
     * Java 11 implementation of a method replaced between Java 18 and 22 with the replacement method being used by
     * Tomcat when running on Java 21 onwards.
     */

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
}
