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
package org.apache.jasper.runtime;

import java.io.IOException;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.HttpJspPage;

import org.apache.jasper.Constants;
import org.apache.jasper.compiler.Localizer;

/**
 * This is the super class of all JSP-generated servlets.
 *
 * @author Anil K. Vijendran
 */
public abstract class HttpJspBase extends HttpServlet implements HttpJspPage {

    private static final long serialVersionUID = 1L;

    protected HttpJspBase() {
    }

    @Override
    public final void init(ServletConfig config)
        throws ServletException
    {
        super.init(config);
        jspInit();
        _jspInit();
    }

    @Override
    public String getServletInfo() {
        return Localizer.getMessage("jsp.engine.info", Constants.SPEC_VERSION);
    }

    @Override
    public final void destroy() {
        jspDestroy();
        _jspDestroy();
    }

    @Override
    public final void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        _jspService(request, response);
    }

    @Override
    public void jspInit() {
    }

    public void _jspInit() {
    }

    @Override
    public void jspDestroy() {
    }

    protected void _jspDestroy() {
    }

    @Override
    public abstract void _jspService(HttpServletRequest request,
                                     HttpServletResponse response)
        throws ServletException, IOException;
}
