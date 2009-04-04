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
package org.apache.tomcat.servlets.jsp;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.addons.UserTemplateClassMapper;
import org.apache.tomcat.integration.ObjectManager;

/**
 * Support for <servlet><jsp-file> config. 
 * 
 * A servlet can be configured with a jsp-file instead of a class. This can
 * be translated into a regular servlet, with JspFileTemplateServlet class and
 * the jsp-file as init parameter.  
 * 
 * This servlet is not jsp specific - you can put any templating file in the 
 * jsp-file config ( if you can ignore the jsp in the name of the attribute ).
 * The file will be converted to a servlet by a custom addon ( jasper in most
 * cases ) 
 * 
 * @author Costin Manolache
 */
public class JspFileTemplateServlet extends HttpServlet {
    
    String jspFile; 
    Servlet realJspServlet;

    UserTemplateClassMapper mapper;

    /** 
     * If called from a <jsp-file> servlet, compile the servlet and init it.
     */
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        // Support <servlet><jsp-file>
        String jspFileParam = config.getInitParameter("jsp-file");
        // TODO: use extension to find the right UserTemplateClassMapper.
        ObjectManager om = 
            (ObjectManager) config.getServletContext().getAttribute(ObjectManager.ATTRIBUTE);
        mapper = 
            (UserTemplateClassMapper) om.get(
                    UserTemplateClassMapper.class);
        if (mapper == null) {
            mapper = new SimpleTemplateClassMapper();
        }
        
        if (jspFile == null && jspFileParam != null) {
            jspFile = jspFileParam;
        }
        // exception will be thrown if not set properly
        realJspServlet = mapper.loadProxy(jspFile, getServletContext(), 
                config);
        realJspServlet.init(config);
    }

    public void setJspFile(String jspFile) {
        this.jspFile = jspFile;
    }
    
    protected void service(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException 
    {
        // TODO: support reload
        
        // realJspServlet will be set - init will fail otherwise.
        realJspServlet.service(req, res);
    }
} 
