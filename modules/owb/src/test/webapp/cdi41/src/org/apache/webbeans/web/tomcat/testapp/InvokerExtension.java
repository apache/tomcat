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

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.invoke.Invoker;

public class InvokerExtension implements Extension {

    private static volatile Invoker<InvokerTarget, ?> invoker;

    void process(@Observes ProcessManagedBean<InvokerTarget> event) {
        AnnotatedMethod<? super InvokerTarget> method = event.getAnnotatedBeanClass().getMethods().stream()
                .filter(candidate -> "hello".equals(candidate.getJavaMember().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("InvokerTarget.hello() was not found"));
        invoker = event.createInvoker(method).withInstanceLookup().withArgumentLookup(0).build();
    }

    public static String invoke() {
        Invoker<InvokerTarget, ?> current = invoker;
        if (current == null) {
            throw new IllegalStateException("The method invoker was not initialized");
        }
        try {
            return (String) current.invoke(null, new Object[] { null });
        } catch (Exception e) {
            throw new IllegalStateException("Method invoker failed", e);
        }
    }
}
