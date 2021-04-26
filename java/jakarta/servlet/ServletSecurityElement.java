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
package jakarta.servlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import jakarta.servlet.annotation.HttpMethodConstraint;
import jakarta.servlet.annotation.ServletSecurity;

/**
 *
 * @since Servlet 3.0
 * TODO SERVLET3 - Add comments
 */
public class ServletSecurityElement extends HttpConstraintElement {

    private final Map<String,HttpMethodConstraintElement> methodConstraints =
        new HashMap<>();

    /**
     * Use default HttpConstraint.
     */
    public ServletSecurityElement() {
        super();
    }

    /**
     * Use specified HttpConstraintElement.
     * @param httpConstraintElement The constraint
     */
    public ServletSecurityElement(HttpConstraintElement httpConstraintElement) {
        this (httpConstraintElement, null);
    }

    /**
     * Use specific constraints for specified methods and default
     * HttpConstraintElement for all other methods.
     * @param httpMethodConstraints Method constraints
     * @throws IllegalArgumentException if a method name is specified more than
     * once
     */
    public ServletSecurityElement(
            Collection<HttpMethodConstraintElement> httpMethodConstraints) {
        super();
        addHttpMethodConstraints(httpMethodConstraints);
    }


    /**
     * Use specified HttpConstraintElement as default and specific constraints
     * for specified methods.
     * @param httpConstraintElement Default constraint
     * @param httpMethodConstraints Method constraints
     * @throws IllegalArgumentException if a method name is specified more than
     */
    public ServletSecurityElement(HttpConstraintElement httpConstraintElement,
            Collection<HttpMethodConstraintElement> httpMethodConstraints) {
        super(httpConstraintElement.getEmptyRoleSemantic(),
                httpConstraintElement.getTransportGuarantee(),
                httpConstraintElement.getRolesAllowed());
        addHttpMethodConstraints(httpMethodConstraints);
    }

    /**
     * Create from an annotation.
     * @param annotation Annotation to use as the basis for the new instance
     * @throws IllegalArgumentException if a method name is specified more than
     */
    public ServletSecurityElement(ServletSecurity annotation) {
        this(new HttpConstraintElement(annotation.value().value(),
                annotation.value().transportGuarantee(),
                annotation.value().rolesAllowed()));

        List<HttpMethodConstraintElement> l = new ArrayList<>();
        HttpMethodConstraint[] constraints = annotation.httpMethodConstraints();
        if (constraints != null) {
            for (HttpMethodConstraint constraint : constraints) {
                HttpMethodConstraintElement e =
                        new HttpMethodConstraintElement(constraint.value(),
                                new HttpConstraintElement(
                                        constraint.emptyRoleSemantic(),
                                        constraint.transportGuarantee(),
                                        constraint.rolesAllowed()));
                l.add(e);
            }
        }
        addHttpMethodConstraints(l);
    }

    public Collection<HttpMethodConstraintElement> getHttpMethodConstraints() {
        Collection<HttpMethodConstraintElement> result = new HashSet<>(methodConstraints.values());
        return result;
    }

    public Collection<String> getMethodNames() {
        Collection<String> result = new HashSet<>(methodConstraints.keySet());
        return result;
    }

    private void addHttpMethodConstraints(
            Collection<HttpMethodConstraintElement> httpMethodConstraints) {
        if (httpMethodConstraints == null) {
            return;
        }
        for (HttpMethodConstraintElement constraint : httpMethodConstraints) {
            String method = constraint.getMethodName();
            if (methodConstraints.containsKey(method)) {
                throw new IllegalArgumentException(
                        "Duplicate method name: " + method);
            }
            methodConstraints.put(method, constraint);
        }
    }
}
