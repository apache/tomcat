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
package org.apache.tomcat.util.security;

import java.lang.reflect.Field;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class PrivilegedSetAccessControlContext implements PrivilegedAction<Void> {

    private static final Log log = LogFactory.getLog(PrivilegedSetAccessControlContext.class);
    private static final StringManager sm = StringManager.getManager(PrivilegedSetAccessControlContext.class);

    private static final AccessControlContext acc;
    private static final Field field;

    static {
        acc = AccessController.getContext();
        Field f = null;
        try {
            f = Thread.class.getDeclaredField("inheritedAccessControlContext");
            f.trySetAccessible();
        } catch (NoSuchFieldException | SecurityException e) {
            log.warn(sm.getString("privilegedSetAccessControlContext.lookupFailed"), e);
        }
        field = f;
    }

    private final Thread t;


    public PrivilegedSetAccessControlContext(Thread t) {
        this.t = t;
    }


    @Override
    public Void run() {
        try {
            if (field != null) {
                field.set(t,  acc);
            }
        } catch (IllegalArgumentException | IllegalAccessException e) {
            log.warn(sm.getString("privilegedSetAccessControlContext.setFailed"), e);
        }
        return null;
    }
}