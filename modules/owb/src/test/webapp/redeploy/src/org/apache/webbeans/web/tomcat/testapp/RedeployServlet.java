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
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/redeploy")
public class RedeployServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private RedeployGreeting greeting;

    @PostConstruct
    void postConstruct() {
        writeMarker("postConstruct.marker", UUID.randomUUID().toString());
    }

    @PreDestroy
    void preDestroy() {
        writeMarker("preDestroy.marker", "destroyed");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain");
        response.getWriter().println(greeting.message());
    }

    private static void writeMarker(String name, String content) {
        String markerDirectory = System.getProperty("owb.test.marker.dir");
        if (markerDirectory != null) {
            try {
                Files.writeString(Path.of(markerDirectory).resolve(name), content);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to write lifecycle marker " + name, e);
            }
        }
    }
}
