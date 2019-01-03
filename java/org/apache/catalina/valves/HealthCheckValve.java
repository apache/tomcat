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

import javax.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.tomcat.util.buf.MessageBytes;


/**
 * Simple Valve that responds to cloud orchestrators health checks.
 */
public class HealthCheckValve extends ValveBase {

    private static final String UP =
            "{\n" +
            "  \"status\": \"UP\",\n" +
            "  \"checks\": []\n" +
            "}";
    private String path = "/health";

    public HealthCheckValve() {
        super(true);
    }

    public final String getPath() {
        return path;
    }

    public final void setPath(String path) {
        this.path = path;
    }

    @Override
    public void invoke(Request request, Response response)
            throws IOException, ServletException {
        MessageBytes requestPathMB = request.getRequestPathMB();
        if (requestPathMB.equals(path)) {
            response.setContentType("application/json");
            response.getOutputStream().print(UP);
        } else {
            getNext().invoke(request, response);
        }
    }
}
