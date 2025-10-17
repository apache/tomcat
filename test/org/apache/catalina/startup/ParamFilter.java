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
package org.apache.catalina.startup;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebInitParam;

/**
 * Test Mock to check Filter Annotations.
 */
@WebFilter(value = "/param", filterName = "paramFilter",
        dispatcherTypes = { DispatcherType.ERROR, DispatcherType.ASYNC },
        initParams = { @WebInitParam(name = "message", value = "Servlet says: ") })
public class ParamFilter extends GenericFilter {

    private static final long serialVersionUID = 1L;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        PrintWriter out = res.getWriter();
        out.print(getInitParameter("message"));
        chain.doFilter(req, res);
    }
}
