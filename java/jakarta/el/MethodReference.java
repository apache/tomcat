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

import java.lang.annotation.Annotation;

/**
 * Provides information about the method to which a method expression resolves.
 */
public class MethodReference {

    private final Object base;
    private final MethodInfo methodInfo;
    private final Annotation[] annotations;
    private final Object[] evaluatedParameters;


    public MethodReference(Object base, MethodInfo methodInfo, Annotation[] annotations, Object[] evaluatedParameters) {
        this.base = base;
        this.methodInfo = methodInfo;
        this.annotations = annotations;
        this.evaluatedParameters = evaluatedParameters;
    }


    /**
     * Obtain the base object on which the method will be invoked.
     *
     * @return The base object on which the method will be invoked or
     *         {@code null} for literal method expressions.
     */
    public Object getBase() {
        return base;
    }


    /**
     * Obtain the {@link MethodInfo} for the {@link MethodExpression} for which
     * this {@link MethodReference} has been generated.
     *
     * @return The {@link MethodInfo} for the {@link MethodExpression} for which
     *         this {@link MethodReference} has been generated.
     */
    public MethodInfo getMethodInfo() {
        return this.methodInfo;
    }


    /**
     * Obtain the annotations on the method to which the associated expression
     * resolves.
     *
     * @return The annotations on the method to which the associated expression
     *         resolves. If the are no annotations, then an empty array is
     *         returned.
     */
    public Annotation[] getAnnotations() {
        return annotations;
    }


    /**
     * Obtain the evaluated parameter values that will be passed to the method
     * to which the associated expression resolves.
     *
     *  @return The evaluated parameters.
     */
    public Object[] getEvaluatedParameters() {
        return evaluatedParameters;
    }
}
