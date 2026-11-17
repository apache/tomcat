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

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.CDI;

@Dependent
public class Cdi41Features {

    public boolean matching() {
        BeanContainer container = CDI.current().getBeanContainer();

        Set<Type> beanTypes = Set.of(MatchingTarget.class, Object.class);
        Set<Annotation> qualifiers = Set.of(Default.Literal.INSTANCE);

        boolean match = container.isMatchingBean(beanTypes, qualifiers, MatchingTarget.class, qualifiers);
        boolean noMatch = container.isMatchingBean(beanTypes, qualifiers, String.class, qualifiers);

        return match && !noMatch;
    }

    public boolean matchingEvent() {
        BeanContainer container = CDI.current().getBeanContainer();
        Set<Annotation> qualifiers = Set.of(Default.Literal.INSTANCE);

        boolean match = container.isMatchingEvent(Cdi41Event.class, qualifiers, Cdi41Event.class, qualifiers);
        boolean noMatch = container.isMatchingEvent(Cdi41Event.class, qualifiers, String.class, qualifiers);

        return match && !noMatch;
    }

    public boolean contexts() {
        return !CDI.current().getBeanContainer().getContexts(RequestScoped.class).isEmpty();
    }
}
