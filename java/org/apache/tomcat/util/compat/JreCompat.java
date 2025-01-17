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

/**
 * This is the base implementation class for JRE compatibility and provides an implementation based on Java 21.
 * Sub-classes may extend this class and provide alternative implementations for later JRE versions
 */
public class JreCompat {

    private static final JreCompat instance;
    private static final boolean graalAvailable;
    private static final boolean jre24Available;
    private static final boolean jre22Available;

    static {
        boolean result = false;
        try {
            Class<?> nativeImageClazz = Class.forName("org.graalvm.nativeimage.ImageInfo");
            result = Boolean.TRUE.equals(nativeImageClazz.getMethod("inImageCode").invoke(null));
        } catch (ClassNotFoundException e) {
            // Must be Graal
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // Should never happen
        }
        graalAvailable = result || System.getProperty("org.graalvm.nativeimage.imagecode") != null;

        // This is Tomcat 12.0.x with a minimum Java version of Java 21.
        // Look for the highest supported JVM first
        if (Jre24Compat.isSupported()) {
            instance = new Jre24Compat();
            jre24Available = true;
            jre22Available = true;
        } else if (Jre22Compat.isSupported()) {
            instance = new Jre22Compat();
            jre24Available = false;
            jre22Available = true;
        } else {
            instance = new JreCompat();
            jre24Available = false;
            jre22Available = false;
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    public static boolean isGraalAvailable() {
        return graalAvailable;
    }


    public static boolean isJre22Available() {
        return jre22Available;
    }


    public static boolean isJre24Available() {
        return jre24Available;
    }
}
