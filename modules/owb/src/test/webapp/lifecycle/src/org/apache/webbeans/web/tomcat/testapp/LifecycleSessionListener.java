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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

@WebListener
public class LifecycleSessionListener implements HttpSessionListener {

    @Inject
    private LifecycleGreeting greeting;

    @PostConstruct
    void postConstruct() {
        LifecycleMarkerWriter.write("sessionListenerPostConstruct.marker");
    }

    @PreDestroy
    void preDestroy() {
        LifecycleMarkerWriter.write("sessionListenerPreDestroy.marker");
    }

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        event.getSession().setAttribute("owb.session.listener.greeting", greeting.message());
    }
}
