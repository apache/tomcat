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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class Jre22Compat extends JreCompat {

    private static final Log log = LogFactory.getLog(Jre22Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre22Compat.class);

    private static final boolean supported;


    static {
        // Note: FFM is the main new feature from Java 22, but it was previously
        // present as a preview. As a result, it is more accurate to test for another
        // new class
        Class<?> c1 = null;
        try {
            c1 = Class.forName("java.text.ListFormat");
        } catch (ClassNotFoundException e) {
            // Must be pre-Java 22
            log.debug(sm.getString("jre22Compat.javaPre22"), e);
        }
        supported = (c1 != null);
    }

    static boolean isSupported() {
        return supported;
    }

}
