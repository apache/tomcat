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
package org.apache.catalina.tribes.util;

import java.io.IOException;
import java.net.MulticastSocket;
import java.net.StandardSocketOptions;

/**
 * This is the base implementation class for JRE compatibility and provides an implementation based on Java 11.
 * Sub-classes may extend this class and provide alternative implementations for later JRE versions
 */
public class JreCompat {

    private static final JreCompat instance;

    static {
        // This is Tomcat 11.0.x with a minimum Java version of Java 11.
        // Look for the highest supported JVM first
        if (Jre14Compat.isSupported()) {
            instance = new Jre14Compat();
        } else {
            instance = new JreCompat();
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    // Java 11 implementations of Java 14 methods

    public void setSocketoptionIpMulticastLoop(MulticastSocket socket, boolean enabled) throws IOException {
        /*
         * Java < 14, a value of true means loopback is disabled. Java 14+ a value of true means loopback is enabled.
         */
        socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, Boolean.valueOf(!enabled));
    }
}
