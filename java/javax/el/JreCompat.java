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
package javax.el;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Modifier;

/*
 * This is cut down version of org.apache.tomcat.util.JreCompat that provides
 * only the methods required by the EL implementation.
 */
class JreCompat {

    private static final JreCompat instance;

    static {
        if (Jre9Compat.isSupported()) {
            instance = new Jre9Compat();
        } else {
            instance = new JreCompat();
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    /**
     * Is the accessibleObject of the given type accessible on the provided
     * instance of that type.
     *
     * @param type  The type the accessible object belongs to
     * @param base  The specific instance of the type to be tested. Unused prior
     *                  to Java 9.
     * @param accessibleObject  The method/field/constructor to be tested.
     *                              Unused prior to Java 9.
     *
     * @return {code true} if the AccessibleObject can be accessed otherwise
     *         {code false}
     */
    public boolean canAcccess(Class<?> type, Object base, AccessibleObject accessibleObject) {
        return Modifier.isPublic(type.getModifiers());
    }
}
