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
package org.apache.el.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

/*
 * This is a cut down version of org.apache.tomcat.util.Jre9Compat that provides
 * only the methods required by the EL implementation.
 *
 * This class is duplicated in javax.el
 * When making changes keep the two in sync.
 */
class Jre9Compat extends JreCompat {

    private static final Method canAccessMethod;


    static {
        Method m1 = null;
        try {
            m1 = AccessibleObject.class.getMethod("canAccess", new Class<?>[] { Object.class });
        } catch (NoSuchMethodException e) {
            // Expected for Java 8
        }
        canAccessMethod = m1;
    }


    public static boolean isSupported() {
        return canAccessMethod != null;
    }


    @Override
    public boolean canAccess(Object base, AccessibleObject accessibleObject) {
        try {
            return ((Boolean) canAccessMethod.invoke(accessibleObject, base)).booleanValue();
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return false;
        }
    }
}
