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

import java.lang.reflect.Method;

/**
 * Abstract base class for mapping EL function names to Java {@link java.lang.reflect.Method}
 * objects. During expression evaluation, the EL implementation uses the FunctionMapper to
 * resolve function references of the form {@code prefix:functionName} to the corresponding
 * static Java methods. Implementations maintain a registry of function prefixes and names
 * mapped to their underlying methods.
 *
 * @since EL 2.1
 */
public abstract class FunctionMapper {

    /**
     * Constructs a FunctionMapper. Subclasses should invoke this constructor to initialize
     * the base mapper state.
     */
    public FunctionMapper() {
    }

    /**
     * Resolves a function reference to its corresponding static {@link java.lang.reflect.Method}.
     *
     * @param prefix    the namespace prefix of the function
     * @param localName the local name of the function
     *
     * @return the {@link Method} object for the resolved function, or {@code null} if not found
     */
    public abstract Method resolveFunction(String prefix, String localName);

    /**
     * Map a method to a function name.
     *
     * @param prefix    Function prefix
     * @param localName Function name
     * @param method    Method
     *
     * @since EL 3.0
     */
    public void mapFunction(String prefix, String localName, Method method) {
        // NO-OP
    }
}
