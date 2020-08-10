/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util;

final class XReflectionIntrospectionUtils {

    static boolean isEnabled() {
        return false;
    }

    /**
     * Always throws {@link UnsupportedOperationException}
     *
     * @param o     Unused
     * @param name  Unused
     *
     * @return Never returns normally
     */
    static Object getPropertyInternal(Object o, String name) {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws {@link UnsupportedOperationException}
     *
     * @param o                 Unused
     * @param name              Unused
     * @param value             Unused
     * @param invokeSetProperty Unused
     *
     * @return Never returns normally
     */
    static boolean setPropertyInternal(Object o, String name, String value, boolean invokeSetProperty) {
        throw new UnsupportedOperationException();
    }

}
