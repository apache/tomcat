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
import java.util.HashMap;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/** 
 *
 * @author Costin Manolache
 */
public class WildcardTemplateServlet extends HttpServlet {

    // for the '*.jsp' case - need to keep track of the jsps
    HashMap<String, Servlet> jsps=new HashMap<String, Servlet>();

    BaseJspLoader mapper;
    
    /** 
     * If called from a <jsp-file> servlet, compile the servlet and init it.
     */
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        String mapperCN = config.getInitParameter("mapper");
        if (mapperCN == null) {
            throw new UnavailableException("can't create mapper");
        }
        try {
            Class c = Class.forName(mapperCN);
            mapper = (BaseJspLoader) c.newInstance();
        } catch (Throwable t) {
            throw new UnavailableException("can't create mapper");
        }
    }

    // TODO: use context extensions to register the servlet as if it would be 
    // loaded from web.xml
    
    protected void service(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException 
    {
        // This is a *.jsp mapping.
        String jspPath = null;

        /** 
         * Magic to get the jsp file from the mappings or container
         */
        if (jspPath == null) {
            jspPath = (String)req.getAttribute("org.apache.catalina.jsp_file");
        }
        if (jspPath == null) {
            // RequestDispatcher.include()
            jspPath = (String)req.getAttribute("javax.servlet.include.servlet_path");
            if (jspPath != null) {
                String pathInfo = (String)req.getAttribute("javax.servlet.include.path_info");
                if (pathInfo != null) {
                    jspPath += pathInfo;
                }
            } else {
                jspPath = req.getServletPath();
                String pathInfo = req.getPathInfo();
                if (pathInfo != null) {
                    jspPath += pathInfo;
                }
            }
        }
        
        // now we should have jspUri == the path to the jsp.
        Servlet realJspServlet = jsps.get(jspPath);
        
        // TODO: support reload
        
        if (realJspServlet == null) {
            realJspServlet = mapper.loadProxy(jspPath, 
                    getServletContext(), getServletConfig());
            if (realJspServlet != null) {
                jsps.put(jspPath, realJspServlet);
            } else {
                throw new ServletException(jspPath + " not found");
            }
        }
        
        realJspServlet.service(req,  res);
    }    
} 
