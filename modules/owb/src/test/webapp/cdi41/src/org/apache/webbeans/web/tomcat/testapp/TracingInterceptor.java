/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.webbeans.web.tomcat.testapp;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Traced
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class TracingInterceptor {

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        boolean found = context.getInterceptorBinding(Traced.class) != null &&
                !context.getInterceptorBindings(Traced.class).isEmpty() &&
                context.getInterceptorBindings().stream()
                        .anyMatch(annotation -> annotation.annotationType() == Traced.class);
        return (found ? "bound:" : "missing:") + context.proceed();
    }
}
