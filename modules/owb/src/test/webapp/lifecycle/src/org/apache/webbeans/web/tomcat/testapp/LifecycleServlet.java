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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/lifecycle")
public class LifecycleServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private LifecycleGreeting greeting;

    @PostConstruct
    void postConstruct() {
        writeMarker("postConstruct.marker");
    }

    @PreDestroy
    void preDestroy() {
        writeMarker("preDestroy.marker");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String listener = (String) getServletContext().getAttribute("owb.listener.greeting");
        String filter = (String) request.getAttribute("owb.filter.greeting");
        response.setContentType("text/plain");
        response.getWriter().println(greeting.message() + "|" + listener + "|" + filter);
    }

    private static void writeMarker(String name) {
        String markerDirectory = System.getProperty("owb.test.marker.dir");
        if (markerDirectory != null) {
            try {
                Files.writeString(Path.of(markerDirectory).resolve(name), "marker");
            } catch (IOException e) {
                throw new IllegalStateException("Unable to write lifecycle marker " + name, e);
            }
        }
    }
}
