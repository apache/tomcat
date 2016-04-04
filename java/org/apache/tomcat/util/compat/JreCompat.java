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

import javax.net.ssl.SSLEngine;

import org.apache.tomcat.util.res.StringManager;

/**
 * This is the base implementation class for JRE compatibility and provides an
 * implementation based on Java 7. Sub-classes may extend this class and provide
 * alternative implementations for later JRE versions
 */
public class JreCompat {

    private static final JreCompat instance;
    private static StringManager sm =
            StringManager.getManager(JreCompat.class.getPackage().getName());
    private static final boolean jre8Available;


    static {
        // This is Tomcat 8 with a minimum Java version of Java 7. The latest
        // Java version the optional features require is Java 8.
        // Look for the highest supported JVM first
        if (Jre8Compat.isSupported()) {
            instance = new Jre8Compat();
            jre8Available = true;
        } else {
            instance = new JreCompat();
            jre8Available = false;
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    // Java 7 implementation of Java 8 methods

    public static boolean isJre8Available() {
        return jre8Available;
    }


    @SuppressWarnings("unused")
    public void setUseServerCipherSuitesOrder(SSLEngine engine,
            boolean useCipherSuitesOrder) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noServerCipherSuiteOrder"));
    }

}
