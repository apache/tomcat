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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import org.apache.juli.logging.LogFactory;

public class TestCustomObjectInputStream {

    /*
     * A minimal, serializable {@link InvocationHandler} so that a dynamic proxy instance can be serialized and then
     * deserialized. Its class lives in {@code org.apache.catalina.util}, so it is permitted by the allow pattern used
     * in the test below; only the proxied interface is not permitted.
     */
    public static class NoOpInvocationHandler implements InvocationHandler, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }
    }


    /*
     * A dynamic proxy is deserialized via {@link CustomObjectInputStream#resolveProxyClass(String[])} rather than
     * {@link CustomObjectInputStream#resolveClass(java.io.ObjectStreamClass)}. This test confirms that the configured
     * {@code allowedClassNamePattern} is also applied to the interfaces implemented by a proxy class. If it is not, the
     * class name filter can be bypassed simply by wrapping a disallowed interface in a dynamic proxy.
     */
    @Test
    public void testResolveProxyClassAppliesAllowedClassNamePattern() throws Exception {

        // A serializable dynamic proxy implementing java.lang.Runnable.
        Object proxy = Proxy.newProxyInstance(TestCustomObjectInputStream.class.getClassLoader(),
                new Class<?>[] { Runnable.class }, new NoOpInvocationHandler());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(proxy);
        }

        // Permit everything that deserializing the proxy resolves via resolveClass() - the InvocationHandler in this
        // package and the java.lang.reflect.Proxy superclass - but NOT the proxied java.lang.Runnable interface. That
        // way the only class name that can trigger rejection is the interface, which is resolved via
        // resolveProxyClass().
        Pattern allowed = Pattern.compile("org\\.apache\\.catalina\\.util\\..*|java\\.lang\\.reflect\\.Proxy");

        try (CustomObjectInputStream cois = new CustomObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()), TestCustomObjectInputStream.class.getClassLoader(),
                LogFactory.getLog(TestCustomObjectInputStream.class), allowed, false)) {

            Assert.assertThrows("Deserialization of a proxy implementing a disallowed interface must be rejected",
                    InvalidClassException.class, cois::readObject);
        }
    }


    /*
     * The counterpart to {@link #testResolveProxyClassAppliesAllowedClassNamePattern()}: when the proxied interface
     * <em>does</em> match the configured {@code allowedClassNamePattern}, deserialization of the proxy must succeed.
     */
    @Test
    public void testResolveProxyClassAllowsPermittedInterface() throws Exception {

        // A serializable dynamic proxy implementing java.lang.Runnable.
        Object proxy = Proxy.newProxyInstance(TestCustomObjectInputStream.class.getClassLoader(),
                new Class<?>[] { Runnable.class }, new NoOpInvocationHandler());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(proxy);
        }

        // Permit everything the proxy resolves, including the proxied java.lang.Runnable interface.
        Pattern allowed = Pattern.compile(
                "org\\.apache\\.catalina\\.util\\..*|java\\.lang\\.reflect\\.Proxy|java\\.lang\\.Runnable");

        try (CustomObjectInputStream cois = new CustomObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()), TestCustomObjectInputStream.class.getClassLoader(),
                LogFactory.getLog(TestCustomObjectInputStream.class), allowed, false)) {

            Object result = cois.readObject();
            Assert.assertTrue("Deserialized object should be a dynamic proxy", Proxy.isProxyClass(result.getClass()));
            Assert.assertTrue("Deserialized proxy should implement the permitted interface", result instanceof Runnable);
        }
    }
}
