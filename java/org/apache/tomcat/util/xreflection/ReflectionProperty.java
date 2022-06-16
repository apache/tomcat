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
package org.apache.tomcat.util.xreflection;

import java.lang.reflect.Method;
import java.net.InetAddress;

final class ReflectionProperty implements Comparable<ReflectionProperty> {
    private final String clazz;
    private final String propertyName;
    private final Class<?> propertyType;
    private final Method setMethod;
    private final Method getMethod;

    ReflectionProperty(String clazz, String propertyName, Class<?> propertyType, Method setMethod, Method getMethod) {
        this.clazz = clazz;
        this.propertyName = propertyName;
        this.propertyType = propertyType;
        this.setMethod = setMethod;
        this.getMethod = getMethod;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Class<?> getPropertyType() {
        return propertyType;
    }

    public boolean hasSetPropertySetter() {
        return hasSetter() && "setProperty".equals(setMethod.getName());
    }

    public boolean hasGetPropertyGetter() {
        return hasGetter() && "getProperty".equals(getMethod.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ReflectionProperty property1 = (ReflectionProperty) o;

        if (!clazz.equals(property1.clazz)) {
            return false;
        }
        return propertyName.equals(property1.propertyName);
    }

    @Override
    public int hashCode() {
        int result = clazz.hashCode();
        result = 31 * result + propertyName.hashCode();
        return result;
    }

    public String getClazz() {
        return clazz;
    }

    public Method getGetMethod() {
        return getMethod;
    }

    public String getConversion(String valueVarName) {
        if (getPropertyType() == String.class) {
            return valueVarName;
        }
        if (getPropertyType() == Boolean.TYPE) {
            return "Boolean.valueOf(" + valueVarName + ")";
        }
        if (getPropertyType() == Long.TYPE) {
            return "Long.valueOf(" + valueVarName + ")";
        }
        if (getPropertyType() == Integer.TYPE) {
            return "Integer.valueOf(" + valueVarName + ")";
        }
        if (getPropertyType() == InetAddress.class) {
            return "getInetAddress(" + valueVarName + ")";
        }
        throw new IllegalStateException("Unexpected Type:" + getPropertyType());

    }

    public boolean hasSetter() {
        return setMethod != null;
    }

    public boolean hasGetter() {
        return getMethod != null;
    }

    public Method getSetMethod() {
        return setMethod;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ReflectionProperty{");
        sb.append("name='").append(propertyName).append('\'');
        sb.append(", type=").append(propertyType);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int compareTo(ReflectionProperty o) {
        // Class then property name
        int result = clazz.compareTo(o.clazz);
        if (result == 0) {
            result = propertyName.compareTo(o.propertyName);
        }
        return result;
    }
}
