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

import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

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
        if (Jre16Compat.isSupported()) {
            instance = new Jre16Compat();
            jre16Available = true;
        } else {
            instance = new JreCompat();
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
}
