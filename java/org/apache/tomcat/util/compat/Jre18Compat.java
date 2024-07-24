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

public class Jre18Compat extends Jre16Compat {

    private static final Log log = LogFactory.getLog(Jre18Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre18Compat.class);

    private static final Method callAsMethod;

    static {
        Method m1 = null;

        try {
            m1 = Subject.class.getMethod("callAs", Subject.class, Callable.class);
        } catch (NoSuchMethodException e) {
            // Must before-Java 18
            log.debug(sm.getString("jre18Compat.javaPre18"), e);
        }

        callAsMethod = m1;
    }


    static boolean isSupported() {
        return callAsMethod != null;
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
