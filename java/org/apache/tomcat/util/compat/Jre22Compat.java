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

import java.lang.reflect.Method;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class Jre22Compat extends Jre21Compat {

    private static final Log log = LogFactory.getLog(Jre22Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre22Compat.class);

    private static final boolean hasPanama;


    static {
        // FIXME: Improve check using a new class in 22 later
        Class<?> c1 = null;
        Class<?> c2 = null;
        Method m1 = null;

        try {
            c1 = Class.forName("java.lang.foreign.MemorySegment");
            c2 = Class.forName("java.io.Console");
            m1 = c1.getMethod("getString", long.class);
            c2.getMethod("isTerminal");
        } catch (ClassNotFoundException e) {
            // Must be pre-Java 22
            log.debug(sm.getString("jre22Compat.javaPre22"), e);
        } catch (ReflectiveOperationException e) {
            // Likely a previous Panama API version
            log.debug(sm.getString("jre22Compat.unexpected"), e);
        }
        hasPanama = (m1 != null);
    }

    static boolean isSupported() {
        return hasPanama;
    }

}
