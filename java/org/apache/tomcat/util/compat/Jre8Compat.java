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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

class Jre8Compat extends JreCompat {

    private static final Method setUseCipherSuitesOrderMethod;


    static {
        Method m1 = null;
        try {
            // Get this class first since it is Java 8+ only
            Class<?> c1 = Class.forName("javax.net.ssl.SSLParameters");
            m1 = c1.getMethod("setUseCipherSuitesOrder", boolean.class);
        } catch (SecurityException e) {
            // Should never happen
        } catch (NoSuchMethodException e) {
            // Expected on Java < 8
        } catch (ClassNotFoundException e) {
            // Should never happen
        }
        setUseCipherSuitesOrderMethod = m1;
    }


    static boolean isSupported() {
        return setUseCipherSuitesOrderMethod != null;
    }


    @Override
    public void setUseServerCipherSuitesOrder(SSLEngine engine,
            boolean useCipherSuitesOrder) {
        SSLParameters sslParameters = engine.getSSLParameters();
        try {
            setUseCipherSuitesOrderMethod.invoke(sslParameters,
                    Boolean.valueOf(useCipherSuitesOrder));
            engine.setSSLParameters(sslParameters);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException(e);
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(e);
        } catch (InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
