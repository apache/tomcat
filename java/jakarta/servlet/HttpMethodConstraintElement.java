/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet;

import java.util.ResourceBundle;

/**
 * Programmatic equivalent of a security constraint defined for a single HTTP method.
 *
 * @since Servlet 3.0
 */
public class HttpMethodConstraintElement extends HttpConstraintElement {

    // Can't inherit from HttpConstraintElement as API does not allow it
    private static final String LSTRING_FILE = "jakarta.servlet.LocalStrings";
    private static final ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

    private final String methodName;

    /**
     * Construct an instance for the given HTTP method name and a default {@link HttpConstraintElement}.
     *
     * @param methodName The HTTP method name
     */
    public HttpMethodConstraintElement(String methodName) {
        if (methodName == null || methodName.length() == 0) {
            throw new IllegalArgumentException(lStrings.getString("httpMethodConstraintElement.invalidMethod"));
        }
        this.methodName = methodName;
    }

    /**
     * Construct an instance for the given HTTP method name and {@link HttpConstraintElement}.
     *
     * @param methodName The HTTP method name
     * @param constraint The constraint for the given method
     */
    public HttpMethodConstraintElement(String methodName, HttpConstraintElement constraint) {
        super(constraint.getEmptyRoleSemantic(), constraint.getTransportGuarantee(), constraint.getRolesAllowed());
        if (methodName == null || methodName.length() == 0) {
            throw new IllegalArgumentException(lStrings.getString("httpMethodConstraintElement.invalidMethod"));
        }
        this.methodName = methodName;
    }

    /**
     * Obtain the name of the HTTP method for which this constraint was created.
     *
     * @return The HTTP method name as provided to the constructor
     */
    public String getMethodName() {
        return methodName;
    }
}