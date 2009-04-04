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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.naming.NamingException;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.InstanceManager;
import org.apache.jasper.EmbeddedServletOptions;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspC;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.servlet.JspServletWrapper;

/** 
 * The actual compiler. Maps and compile a jsp-file to a class.
 */
public class JasperCompilerTemplateClassMapper 
        extends SimpleTemplateClassMapper {
    
    public void init(ServletConfig config) {
        this.config = config;
        ServletContext context = config.getServletContext();
        context.setAttribute(InstanceManager.class.getName(), 
                new InstanceManager() {

                    public void destroyInstance(Object arg0)
                            throws IllegalAccessException,
                            InvocationTargetException {
                    }

                    public Object newInstance(String arg0)
                            throws IllegalAccessException,
                            InvocationTargetException, NamingException,
                            InstantiationException, ClassNotFoundException {
                        return newInstance(arg0, 
                                this.getClass().getClassLoader());
                    }

                    public void newInstance(Object o)
                            throws IllegalAccessException,
                            InvocationTargetException, NamingException {
                    }

                    public Object newInstance(String className,
                                              ClassLoader classLoader)
                            throws IllegalAccessException,
                            InvocationTargetException, NamingException,
                            InstantiationException, ClassNotFoundException {
                        Class clazz = classLoader.loadClass(className);
                        return clazz.newInstance();
                    }
            
        });
        //      Initialize the JSP Runtime Context
        options = new EmbeddedServletOptions(config, context);
        
        rctxt = new JspRuntimeContext(context, options);
        String basePath = context.getRealPath("/");
        File f = new File(basePath + "/WEB-INF/classes");
        f.mkdirs();
        //fileS.initParams.put("scratchdir",  f.getAbsolutePath());
        // if load-on-startup: allow other servlets to find us

        
    }
    
    private Options options;
    private JspRuntimeContext rctxt;
    private ServletConfig config;

    public boolean needsReload(String jspFile, Servlet s) {
        JspServletWrapper wrapper =
            (JspServletWrapper) rctxt.getWrapper(jspFile);
        // TODO: extract outdate info, compilation date, etc
        return false;
    }
    
    protected Servlet compileAndInitPage(ServletContext ctx, 
                                         String jspUri, 
                                         ServletConfig cfg) 
    throws ServletException {
        try {
            if (config == null) {
                init(cfg);
            }
            JspServletWrapper wrapper =
                (JspServletWrapper) rctxt.getWrapper(jspUri);
            if (wrapper == null) {
                synchronized(this) {
                    wrapper = (JspServletWrapper) rctxt.getWrapper(jspUri);
                    if (wrapper == null) {
                        // Check if the requested JSP page exists, to avoid
                        // creating unnecessary directories and files.
                        if (null == ctx.getResource(jspUri)) {
                            return null;
                        }
                        //boolean isErrorPage = exception != null;
                        wrapper = new JspServletWrapper(cfg, options, jspUri,
                                false, rctxt);
                        rctxt.addWrapper(jspUri,wrapper);
                    }
                }
            }

            wrapper.getJspEngineContext().compile();
            return wrapper.getServlet();
        } catch (IOException ex) {
            throw new ServletException(ex);
        }
    }

    /**
     *  
     * Do the compilation - without JspServletWrapper
     * 
     * Options: 
     *  - jasper.jar in classpath, we do Class.forName for main()
     *  - TODO: exec jasper.sh ( or any other script set in params ) 
     *  - TODO: redirect to a different servlet
     * 
     * Not used right - see previous method for a safer approach
     * 
     * @param ctx
     * @param jspPath
     */
   public void compileJspDirect(ServletContext ctx, String jspPath) {
        //ServletContextImpl ctx = (ServletContextImpl)sctx;
        // Params to pass to jspc:
        // classpath 
        // webapp base dir
        String baseDir = ctx.getRealPath("/");
        // jsp path ( rel. base dir )

        JspC jspc = new JspC();
        jspc.setUriroot(baseDir);
        jspc.setTrimSpaces(false);
        jspc.setPoolingEnabled(true);
        jspc.setErrorOnUseBeanInvalidClassAttribute(false);
        jspc.setClassDebugInfo(true);
        jspc.setCaching(true);
        jspc.setSmapDumped(true);

        try {
            jspc.execute();
        } catch (JasperException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}