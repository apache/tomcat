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
package org.apache.naming;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.IntrospectionUtils;

public class TesterInjectionServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private String property1 = null;
    public String getProperty1() { return property1; }

    // Not used directly.
    // Here to ensure properties are injected in preference to fields
    private String property2 = null;
    public void setProperty2a(String property2) { this.property2 = property2; }
    public String getProperty2a() { return property2; }

    private String property2a = null;
    public void setProperty2(String property2) { this.property2a = property2; }
    public String getProperty2() { return property2a; }

    private String property3 = null;
    public String getProperty3() { return property3; }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/plain");
        resp.setCharacterEncoding("UTF-8");

        String injectionName = req.getParameter("injectionName");

        PrintWriter pw = resp.getWriter();
        pw.print(IntrospectionUtils.getProperty(this, injectionName));

        // The property should take precedence over the field and this should
        // be null
        if (getProperty2a() != null) {
            pw.println();
            pw.print(getProperty2a());
        }
    }
}
