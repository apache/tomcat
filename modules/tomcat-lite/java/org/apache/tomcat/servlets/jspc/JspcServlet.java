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
package org.apache.tomcat.servlets.jspc;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspC;
import org.apache.tomcat.servlets.jsp.BaseJspLoader;

/** 
 * The actual compiler. Maps and compile a jsp-file to a class.
 */
public class JspcServlet extends HttpServlet implements BaseJspLoader.JspCompiler {
    
    public void doGet(HttpServletRequest req, HttpServletResponse res) 
                throws ServletException {

        // TODO: allow only local calls ?
        
        // relative to context
        String jspFiles = req.getParameter("jspFiles");
        String classPath = req.getParameter("classPath");
        String pkg = req.getParameter("pkg");
        
        compileAndInit(getServletContext(), 
                jspFiles, getServletConfig(), classPath, pkg);
    }

    @Override
    public void compileAndInit(ServletContext ctx, String jspFiles,
            ServletConfig cfg, String classPath, String pkg) {
        
        if (jspFiles.startsWith("/")) {
            jspFiles = jspFiles.substring(1);
        }
        String baseDir = ctx.getRealPath("/");
        
        JspC jspc = new JspC();
        
        jspc.setUriroot(baseDir);
        jspc.setTrimSpaces(false);
        jspc.setPoolingEnabled(true);
        jspc.setErrorOnUseBeanInvalidClassAttribute(false);
        jspc.setClassDebugInfo(true);
        jspc.setCaching(true);
        jspc.setSmapDumped(true);
        jspc.setGenStringAsCharArray(true);
        
        jspc.setJspFiles(jspFiles);
        
        jspc.setVerbose(10);
        
        jspc.setPackage(pkg);

        jspc.setOutputDir(baseDir + "WEB-INF/tmp");
        jspc.setCompile(true);
        //jspc.setCompiler("jdt");
        jspc.setClassPath(classPath);

        try {
            jspc.execute();
        } catch (JasperException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}