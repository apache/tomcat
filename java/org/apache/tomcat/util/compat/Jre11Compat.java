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

public class Jre11Compat extends Jre9Compat {

    private static final Method stripMethod;
    private static final Method stripLeadingMethod;
    private static final Method stripTrailingMethod;

    static {
        Method m1 = null;
        Method m2 = null;
        Method m3 = null;

        try {
            m1 = String.class.getMethod("strip");
            m2 = String.class.getMethod("stripLeading");
            m3 = String.class.getMethod("stripTrailing");
        } catch (NoSuchMethodException e) {
            // Must be pre-Java 11
        }

        stripMethod = m1;
        stripLeadingMethod = m2;
        stripTrailingMethod = m3;
    }


    static boolean isSupported() {
        return stripMethod != null;
    }


    @Override
    public String strip(String input) {
        try {
            return (String) stripMethod.invoke(input);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public String stripLeading(String input) {
        try {
            return (String) stripLeadingMethod.invoke(input);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public String stripTrailing(String input) {
        try {
            return (String) stripTrailingMethod.invoke(input);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
