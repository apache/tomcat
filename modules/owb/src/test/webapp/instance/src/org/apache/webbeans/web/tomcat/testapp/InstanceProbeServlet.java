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

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.tomcat.InstanceManager;

@WebServlet("/instance-probe")
public class InstanceProbeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        InstanceManager instanceManager = (InstanceManager) getServletContext()
                .getAttribute(InstanceManager.class.getName());
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader markerLoader = new ClassLoader(original) {
        };
        Thread.currentThread().setContextClassLoader(markerLoader);
        try {
            InstanceProbe byClass = (InstanceProbe) instanceManager.newInstance(InstanceProbe.class);
            boolean classInjected = byClass.isInjected();
            instanceManager.destroyInstance(byClass);
            boolean classContext = Thread.currentThread().getContextClassLoader() == markerLoader;

            InstanceProbe byName = (InstanceProbe) instanceManager.newInstance(InstanceProbe.class.getName());
            boolean stringInjected = byName.isInjected();
            instanceManager.destroyInstance(byName);
            boolean stringContext = Thread.currentThread().getContextClassLoader() == markerLoader;

            InstanceProbe byLoader = (InstanceProbe) instanceManager.newInstance(InstanceProbe.class.getName(),
                    InstanceProbeServlet.class.getClassLoader());
            boolean stringLoaderInjected = byLoader.isInjected();
            instanceManager.destroyInstance(byLoader);
            boolean stringLoaderContext = Thread.currentThread().getContextClassLoader() == markerLoader;

            InstanceProbe byObject = new InstanceProbe();
            instanceManager.newInstance(byObject);
            boolean objectInjected = byObject.isInjected();
            instanceManager.destroyInstance(byObject);
            boolean objectContext = Thread.currentThread().getContextClassLoader() == markerLoader;

            boolean contextRestored = classContext && stringContext && stringLoaderContext && objectContext;
            response.setContentType("text/plain");
            response.getWriter().println("class=" + injected(classInjected) + ",string=" + injected(stringInjected)
                    + ",stringLoader=" + injected(stringLoaderInjected) + ",object=" + injected(objectInjected)
                    + ",context=" + (contextRestored ? "restored" : "changed"));
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static String injected(boolean injected) {
        return injected ? "injected" : "not-injected";
    }
}
