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

import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/el")
public class ElServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    @SuppressWarnings("removal")
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        BeanManager beanManager = CDI.current().getBeanManager();
        ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(expressionFactory);
        context.addELResolver(beanManager.getELResolver());
        ValueExpression expression = expressionFactory.createValueExpression(context, "#{greeting.message}",
                String.class);
        Object value = expression.getValue(context);
        response.setContentType("text/plain");
        response.getWriter().println(value.toString());
    }
}
