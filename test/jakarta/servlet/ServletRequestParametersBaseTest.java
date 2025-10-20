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
package jakarta.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TomcatBaseTest;

public class ServletRequestParametersBaseTest extends TomcatBaseTest {

    protected Map<String,List<String>> parseReportedParameters(SimpleHttpClient client) {
        Map<String,List<String>> parameters = new LinkedHashMap<>();
        if (client.isResponse200()) {
            // Response is written using "\n" so need to split on that.
            String[] lines = client.getResponseBody().split("\n");
            for (String line : lines) {
                // Every line should be name=value
                int equalsPos = line.indexOf('=');
                String name = line.substring(0, equalsPos);
                String value = line.substring(equalsPos + 1);

                List<String> values = parameters.computeIfAbsent(name, k -> new ArrayList<>());
                values.add(value);
            }
        }
        return parameters;
    }


    protected static class ParameterParsingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            resp.setContentType("text/plain");
            resp.setCharacterEncoding(StandardCharsets.UTF_8);
            PrintWriter pw = resp.getWriter();

            Enumeration<String> names = req.getParameterNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                for (String value : req.getParameterValues(name)) {
                    pw.print(name + "=" + value + '\n');
                }
            }
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // Required parameter processing is the same as for GET
            doGet(req, resp);
        }
    }


    protected static class TestParameterClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }
}
