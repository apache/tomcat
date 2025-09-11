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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class Jre20Compat extends Jre19Compat {

    private static final Log log = LogFactory.getLog(Jre20Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre20Compat.class);

    private static final boolean supported;
    private static final Method setNamedGroupsMethod;


    static {
        Class<?> c1 = null;
        Method m1 = null;

        try {
            c1 = Class.forName("javax.net.ssl.SSLParameters");
            m1 = c1.getMethod("setNamedGroups", String[].class);
        } catch (ClassNotFoundException e) {
            // Must be pre-Java 20
            log.debug(sm.getString("jre20Compat.javaPre20"), e);
        } catch (ReflectiveOperationException e) {
            // Should never happen
            log.error(sm.getString("jre20Compat.unexpected"), e);
        }
        supported = (c1 != null);
        setNamedGroupsMethod = m1;
    }

    static boolean isSupported() {
        return supported;
    }

    @Override
    public void setNamedGroupsMethod(Object sslParameters, String[] names) {
        try {
            setNamedGroupsMethod.invoke(sslParameters, (Object[]) names);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
