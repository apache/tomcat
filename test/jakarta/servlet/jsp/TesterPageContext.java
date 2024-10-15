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
package jakarta.servlet.jsp;

import java.io.IOException;
import java.util.Enumeration;

import jakarta.el.ELContext;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;

public class TesterPageContext extends PageContext {

    @Override
    public void initialize(Servlet servlet, ServletRequest request,
            ServletResponse response, String errorPageURL,
            boolean needsSession, int bufferSize, boolean autoFlush)
            throws IOException, IllegalStateException, IllegalArgumentException {
        // NO-OP
    }

    @Override
    public void release() {
        // NO-OP
    }

    @Override
    public HttpSession getSession() {
        // NO-OP
        return null;
    }

    @Override
    public Object getPage() {
        // NO-OP
        return null;
    }

    @Override
    public ServletRequest getRequest() {
        // NO-OP
        return null;
    }

    @Override
    public ServletResponse getResponse() {
        // NO-OP
        return null;
    }

    @Override
    public Exception getException() {
        // NO-OP
        return null;
    }

    @Override
    public ServletConfig getServletConfig() {
        // NO-OP
        return null;
    }

    @Override
    public ServletContext getServletContext() {
        // NO-OP
        return null;
    }

    @Override
    public void forward(String relativeUrlPath) throws ServletException,
            IOException {
        // NO-OP

    }

    @Override
    public void include(String relativeUrlPath) throws ServletException,
            IOException {
        // NO-OP
    }

    @Override
    public void include(String relativeUrlPath, boolean flush)
            throws ServletException, IOException {
        // NO-OP
    }

    @Override
    public void handlePageException(Exception e) throws ServletException,
            IOException {
        // NO-OP
    }

    @Override
    public void handlePageException(Throwable t) throws ServletException,
            IOException {
        // NO-OP
    }

    @Override
    public void setAttribute(String name, Object value) {
        // NO-OP
    }

    @Override
    public void setAttribute(String name, Object value, int scope) {
        // NO-OP
    }

    @Override
    public Object getAttribute(String name) {
        // NO-OP
        return null;
    }

    @Override
    public Object getAttribute(String name, int scope) {
        // NO-OP
        return null;
    }

    @Override
    public Object findAttribute(String name) {
        // NO-OP
        return null;
    }

    @Override
    public void removeAttribute(String name) {
        // NO-OP
    }

    @Override
    public void removeAttribute(String name, int scope) {
        // NO-OP
    }

    @Override
    public int getAttributesScope(String name) {
        // NO-OP
        return 0;
    }

    @Override
    public Enumeration<String> getAttributeNamesInScope(int scope) {
        // NO-OP
        return null;
    }

    @Override
    public JspWriter getOut() {
        // NO-OP
        return null;
    }

    @Override
    public ELContext getELContext() {
        // NO-OP
        return null;
    }
}
