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
package jakarta.el;

import java.util.Arrays;

/**
 * Holds metadata about a method that an EL method expression resolves to, including the method name,
 * return type, and parameter types.
 */
public class MethodInfo {

    /** The name of the method. */
    private final String name;

    /** The parameter types of the method. */
    private final Class<?>[] paramTypes;

    /** The return type of the method. */
    private final Class<?> returnType;

    /**
     * Constructs a MethodInfo with the given name, return type, and parameter types.
     *
     * @param name       The name of the method
     * @param returnType The return type of the method
     * @param paramTypes The parameter types of the method
     */
    public MethodInfo(String name, Class<?> returnType, Class<?>[] paramTypes) {
        this.name = name;
        this.returnType = returnType;
        this.paramTypes = paramTypes;
    }

    /**
     * Returns the name of the method.
     *
     * @return the method name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the return type of the method.
     *
     * @return the return type
     */
    public Class<?> getReturnType() {
        return this.returnType;
    }

    /**
     * Returns the parameter types of the method.
     *
     * @return the parameter types
     */
    public Class<?>[] getParamTypes() {
        return this.paramTypes;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + Arrays.hashCode(paramTypes);
        result = prime * result + ((returnType == null) ? 0 : returnType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MethodInfo other = (MethodInfo) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (!Arrays.equals(paramTypes, other.paramTypes)) {
            return false;
        }
        if (returnType == null) {
            return other.returnType == null;
        } else {
            return returnType.equals(other.returnType);
        }
    }
}
