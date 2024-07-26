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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

import javax.security.auth.Subject;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class Jre21Compat extends Jre19Compat {

    private static final Log log = LogFactory.getLog(Jre21Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre21Compat.class);

    private static final Method nameMethod;
    private static final Method startMethod;
    private static final Method ofVirtualMethod;
    private static final Method callAsMethod;


    static {
        Class<?> c1 = null;
        Method m1 = null;
        Method m2 = null;
        Method m3 = null;
        Method m4 = null;

        try {
            c1 = Class.forName("java.lang.Thread$Builder");
            m1 = c1.getMethod("name", String.class, long.class);
            m2 = c1.getMethod("start", Runnable.class);
            m3 = Thread.class.getMethod("ofVirtual", (Class<?>[]) null);
            m4 = Subject.class.getMethod("callAs", Subject.class, Callable.class);
        } catch (ClassNotFoundException e) {
            // Must be pre-Java 21
            log.debug(sm.getString("jre21Compat.javaPre21"), e);
        } catch (ReflectiveOperationException e) {
            // Should never happen
            log.error(sm.getString("jre21Compat.unexpected"), e);
        }
        nameMethod = m1;
        startMethod = m2;
        ofVirtualMethod = m3;
        callAsMethod = m4;
    }

    static boolean isSupported() {
        return ofVirtualMethod != null;
    }

    @Override
    public Object createVirtualThreadBuilder(String name) {
        try {
            Object threadBuilder = ofVirtualMethod.invoke(null, (Object[]) null);
            nameMethod.invoke(threadBuilder, name, Long.valueOf(0));
            return threadBuilder;
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public void threadBuilderStart(Object threadBuilder, Runnable command) {
        try {
            startMethod.invoke(threadBuilder, command);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T> T callAs(Subject subject, Callable<T> action) throws CompletionException {
        try {
            return (T) callAsMethod.invoke(null, subject, action);
        } catch (IllegalAccessException e) {
            throw new CompletionException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException) {
                throw (CompletionException) cause;
            }
            throw new CompletionException(e);
        }
    }
}
