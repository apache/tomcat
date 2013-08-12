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
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.TldLocationsCache;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;
import org.xml.sax.SAXException;

/**
 * Initializer for the Jasper JSP Engine.
 */
public class JasperInitializer implements ServletContainerInitializer {
    /**
     * Name of ServletContext initParam that determines if descriptor XML
     * should be validated.
     */
    public static final String VALIDATE = "org.apache.jasper.validateDescriptors";
    private static final String MSG = "org.apache.jasper.servlet.JasperInitializer";
    private static final Log LOG = LogFactory.getLog(JasperInitializer.class);

    @Override
    public void onStartup(Set<Class<?>> types, ServletContext context) throws ServletException {
        if (LOG.isDebugEnabled()) {
            LOG.debug(Localizer.getMessage(MSG + ".onStartup", context.getServletContextName()));
        }

        boolean validate = Boolean.valueOf(context.getInitParameter(VALIDATE));

        // scan the application for TLDs
        TldScanner scanner = new TldScanner(context, true, validate);
        try {
            scanner.scan();
        } catch (IOException | SAXException e) {
            throw new ServletException(e);
        }

        // add any listeners defined in TLDs
        for (String listener : scanner.getListeners()) {
            context.addListener(listener);
        }

        Map<String, TldResourcePath> taglibMap = scanner.getTaglibMap();
        context.setAttribute(TldLocationsCache.KEY, new TldLocationsCache(taglibMap));
    }
}
