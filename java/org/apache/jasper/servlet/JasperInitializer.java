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
package org.apache.jasper.servlet;

import java.io.IOException;
import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.jsp.JspFactory;

import org.apache.jasper.Constants;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.TldCache;
import org.apache.jasper.runtime.JspFactoryImpl;
import org.apache.jasper.security.SecurityClassLoad;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.xml.sax.SAXException;

/**
 * Initializer for the Jasper JSP Engine.
 */
public class JasperInitializer implements ServletContainerInitializer {

    private static final String MSG = "org.apache.jasper.servlet.JasperInitializer";
    private static final Log log = LogFactory.getLog(JasperInitializer.class);

    /**
     * Preload classes required at runtime by a JSP servlet so that
     * we don't get a defineClassInPackage security exception.
     */
    static {
        JspFactoryImpl factory = new JspFactoryImpl();
        SecurityClassLoad.securityClassLoad(factory.getClass().getClassLoader());
        if( System.getSecurityManager() != null ) {
            String basePackage = "org.apache.jasper.";
            try {
                factory.getClass().getClassLoader().loadClass( basePackage +
                        "runtime.JspFactoryImpl$PrivilegedGetPageContext");
                factory.getClass().getClassLoader().loadClass( basePackage +
                        "runtime.JspFactoryImpl$PrivilegedReleasePageContext");
                factory.getClass().getClassLoader().loadClass( basePackage +
                        "runtime.JspRuntimeLibrary");
                factory.getClass().getClassLoader().loadClass( basePackage +
                        "runtime.ServletResponseWrapperInclude");
                factory.getClass().getClassLoader().loadClass( basePackage +
                        "servlet.JspServletWrapper");
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }

        if (JspFactory.getDefaultFactory() == null) {
            JspFactory.setDefaultFactory(factory);
        }
    }

    @Override
    public void onStartup(Set<Class<?>> types, ServletContext context) throws ServletException {
        if (log.isDebugEnabled()) {
            log.debug(Localizer.getMessage(MSG + ".onStartup", context.getServletContextName()));
        }

        // Setup a simple default Instance Manager
        if (context.getAttribute(InstanceManager.class.getName())==null) {
            context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
        }

        boolean validate = Boolean.parseBoolean(
                context.getInitParameter(Constants.XML_VALIDATION_TLD_INIT_PARAM));
        String blockExternalString = context.getInitParameter(
                Constants.XML_BLOCK_EXTERNAL_INIT_PARAM);
        boolean blockExternal;
        if (blockExternalString == null) {
            blockExternal = true;
        } else {
            blockExternal = Boolean.parseBoolean(blockExternalString);
        }

        // scan the application for TLDs
        TldScanner scanner = newTldScanner(context, true, validate, blockExternal);
        try {
            scanner.scan();
        } catch (IOException | SAXException e) {
            throw new ServletException(e);
        }

        // add any listeners defined in TLDs
        for (String listener : scanner.getListeners()) {
            context.addListener(listener);
        }

        context.setAttribute(TldCache.SERVLET_CONTEXT_ATTRIBUTE_NAME,
                new TldCache(context, scanner.getUriTldResourcePathMap(),
                        scanner.getTldResourcePathTaglibXmlMap()));
    }

    protected TldScanner newTldScanner(ServletContext context, boolean namespaceAware,
            boolean validate, boolean blockExternal) {
        return new TldScanner(context, namespaceAware, validate, blockExternal);
    }
}
