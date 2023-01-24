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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class Jre14Compat extends JreCompat {

    private static final Log log = LogFactory.getLog(Jre14Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre14Compat.class);

    private static final boolean supported;

    static {
        // Don't need any Java 19 specific classes (yet) so just test for one of
        // the new ones for now.
        Class<?> c1 = null;
        try {
            c1 = Class.forName("java.io.Serial");
        } catch (ClassNotFoundException cnfe) {
            // Must be pre-Java 16
            log.debug(sm.getString("jre14Compat.javaPre14"), cnfe);
        }

        supported = (c1 != null);
    }

    static boolean isSupported() {
        return supported;
    }


    @Override
    public void setSocketoptionIpMulticastLoop(MulticastSocket socket, boolean enabled) throws IOException {
        /*
         * Java < 14, a value of true means loopback is disabled. Java 14+ a value of true means loopback is enabled.
         */
        socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, Boolean.valueOf(enabled));
    }

}
