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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

class Jre16Compat extends Jre9Compat {

    private static final Log log = LogFactory.getLog(Jre16Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre16Compat.class);

    private static final Class<?> unixDomainSocketAddressClazz;
    private static final Method openServerSocketChannelFamilyMethod;
    private static final Method unixDomainSocketAddressOfMethod;
    private static final Method openSocketChannelFamilyMethod;

    static {
        Class<?> c1 = null;
        Method m1 = null;
        Method m2 = null;
        Method m3 = null;
        try {
            c1 = Class.forName("java.net.UnixDomainSocketAddress");
            m1 = ServerSocketChannel.class.getMethod("open", ProtocolFamily.class);
            m2 = c1.getMethod("of", String.class);
            m3 = SocketChannel.class.getMethod("open", ProtocolFamily.class);
        } catch (ClassNotFoundException e) {
            // Must be pre-Java 16
            log.debug(sm.getString("jre16Compat.javaPre16"), e);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // Should never happen
            log.error(sm.getString("jre16Compat.unexpected"), e);
        }
        unixDomainSocketAddressClazz = c1;
        openServerSocketChannelFamilyMethod = m1;
        unixDomainSocketAddressOfMethod = m2;
        openSocketChannelFamilyMethod = m3;
    }

    static boolean isSupported() {
        return unixDomainSocketAddressClazz != null;
    }

    @Override
    public SocketAddress getUnixDomainSocketAddress(String path) {
        try {
            return (SocketAddress) unixDomainSocketAddressOfMethod.invoke(null, path);
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public ServerSocketChannel openUnixDomainServerSocketChannel() {
        try {
            return (ServerSocketChannel) openServerSocketChannelFamilyMethod.invoke
                    (null, StandardProtocolFamily.valueOf("UNIX"));
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public SocketChannel openUnixDomainSocketChannel() {
        try {
            return (SocketChannel) openSocketChannelFamilyMethod.invoke
                    (null, StandardProtocolFamily.valueOf("UNIX"));
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
