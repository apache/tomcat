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
package org.apache.catalina.util;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.Manager;

/**
 * Utility class used to help generate return values for calls to
 * {@link Object#toString()}.
 */
public class ToStringUtil {

    private ToStringUtil() {
        // Utility class. Hide default constructor
    }


    public static final String toString(Contained contained) {
        return toString(contained, contained.getContainer());
    }


    public static final String toString(Object obj, Container container) {
        return containedToString(obj, container, "Container");
    }


    public static final String toString(Object obj, Manager manager) {
        return containedToString(obj, manager, "Manager");
    }


    private static final String containedToString(Object contained, Object container,
            String containerTypeName) {
        StringBuilder sb = new StringBuilder(contained.getClass().getSimpleName());
        sb.append('[');
        if (container == null) {
            sb.append(containerTypeName);
            sb.append(" is null");
        } else {
            sb.append(container.toString());
        }
        sb.append(']');
        return sb.toString();
    }
}
