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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLConnection;

class Jre9Compat extends Jre8Compat {

    private static final Class<?> inaccessibleObjectExceptionClazz;
    private static final Method setDefaultUseCaches;

    static {
        Class<?> c1 = null;
        Method m4 = null;

        try {
            c1 = Class.forName("java.lang.reflect.InaccessibleObjectException");
            m4 = URLConnection.class.getMethod("setDefaultUseCaches", String.class, boolean.class);
        } catch (SecurityException e) {
            // Should never happen
        } catch (ClassNotFoundException e) {
            // Must be Java 8
        } catch (NoSuchMethodException e) {
            // Should never happen
        }
        inaccessibleObjectExceptionClazz = c1;
        setDefaultUseCaches = m4;
    }


    static boolean isSupported() {
        return inaccessibleObjectExceptionClazz != null;
    }


    @Override
    public boolean isInstanceOfInaccessibleObjectException(Throwable t) {
        if (t == null) {
            return false;
        }

        return inaccessibleObjectExceptionClazz.isAssignableFrom(t.getClass());
    }


    @Override
    public void disableCachingForJarUrlConnections() throws IOException {
        try {
            setDefaultUseCaches.invoke(null, "JAR", Boolean.FALSE);
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(e);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException(e);
        } catch (InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
