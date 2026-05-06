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
package org.apache.catalina.valves;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.tomcat.util.buf.MessageBytes;


/**
 * Simple Valve that responds to cloud orchestrators health checks.
 */
public class HealthCheckValve extends ValveBase {

    private static final String UP = "{\n" + "  \"status\": \"UP\",\n" + "  \"checks\": []\n" + "}";

    private static final String DOWN = "{\n" + "  \"status\": \"DOWN\",\n" + "  \"checks\": []\n" + "}";

    private String path = "/health";

    /**
     * Will be set to true if the valve is associated with a context.
     */
    protected boolean context = false;

    /**
     * Check if all child containers are available.
     */
    protected boolean checkContainersAvailable = true;

    /**
     * Construct a new HealthCheckValve.
     */
    public HealthCheckValve() {
        super(true);
    }

    /**
     * Get the health check path.
     *
     * @return the health check path
     */
    public final String getPath() {
        return path;
    }

    /**
     * Set the health check path.
     *
     * @param path the health check path
     */
    public final void setPath(String path) {
        this.path = path;
    }

    /**
     * Get whether to check if all child containers are available.
     *
     * @return {@code true} if child container availability is checked
     */
    public boolean getCheckContainersAvailable() {
        return this.checkContainersAvailable;
    }

    /**
     * Set whether to check if all child containers are available.
     *
     * @param checkContainersAvailable the new value
     */
    public void setCheckContainersAvailable(boolean checkContainersAvailable) {
        this.checkContainersAvailable = checkContainersAvailable;
    }

    @Override
    protected void startInternal() throws LifecycleException {
        super.startInternal();
        context = (getContainer() instanceof Context);
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        MessageBytes urlMB = context ? request.getRequestPathMB() : request.getDecodedRequestURIMB();
        if (urlMB.equals(path)) {
            response.setContentType("application/json");
            if (!checkContainersAvailable || isAvailable(getContainer())) {
                response.getOutputStream().print(UP);
            } else {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.getOutputStream().print(DOWN);
            }
        } else {
            getNext().invoke(request, response);
        }
    }

    /**
     * Recursively check if the given container and all its children are available.
     *
     * @param container the container to check
     *
     * @return {@code true} if the container and all its children are available
     */
    protected boolean isAvailable(Container container) {
        for (Container child : container.findChildren()) {
            if (!isAvailable(child)) {
                return false;
            }
        }
        return container.getState().isAvailable();
    }

}
