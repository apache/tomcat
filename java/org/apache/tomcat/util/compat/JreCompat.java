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
 * This is the base implementation class for JRE compatibility and provides an
 * implementation based on Java 8. Sub-classes may extend this class and provide
 * alternative implementations for later JRE versions
 */
public class JreCompat {

    private static final JreCompat instance;
    private static final boolean jre9Available;


    static {
        // This is Tomcat 9 with a minimum Java version of Java 8.
        // At this point there are no option features that require Java > 8 but
        // the memory leak detection code does need to know if it is running on
        // Java 9+ since the modularisation changes break some of the reflection
        // used
        // Look for the highest supported JVM first
        if (Jre9Compat.isSupported()) {
            instance = new Jre9Compat();
            jre9Available = true;
        } else {
            instance = new JreCompat();
            jre9Available = false;
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    // Java 8 implementation of Java 9 methods

    public static boolean isJre9Available() {
        return jre9Available;
    }


    /**
     * Test if the provided exception is an instance of
     * java.lang.reflect.InaccessibleObjectException.
     *
     * @param e The exception to test
     *
     * @return {@code true} if the exception is an instance of
     *         InaccessibleObjectException, otherwise {@code false}
     */
    public boolean isInstanceOfInaccessibleObjectException(Exception e) {
        // Exception does not exist prior to Java 9
        return false;
    }
}
