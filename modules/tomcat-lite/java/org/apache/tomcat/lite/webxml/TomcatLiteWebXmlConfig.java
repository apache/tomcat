/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.lite.webxml;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.tomcat.lite.ContextPreinitListener;
import org.apache.tomcat.lite.ServletContextImpl;

/**
 * Default configurator - parse web.xml, init the context.
 * 
 * Will be executed first - if set as the default config addon.
 * 
 * Possible extensions: 
 *   - read from a .ser file instead of web.xml
 *   - custom code for manual/extra config
 *   - read from a central repo
 *  
 * @author Costin Manolache
 */
public class TomcatLiteWebXmlConfig implements ContextPreinitListener {

    protected void readWebXml(ServletContextImpl ctx, 
                              String base) throws ServletException {
        // TODO: .ser, reloading, etc
//        if (contextConfig != null && contextConfig.fileName != null) {
//            // TODO: this should move to deploy - if not set, there is no point
//            File f = new File(contextConfig.fileName);
//            if (f.exists()) {
//                if (f.lastModified() > contextConfig.timestamp + 1000) {
//                    log("Reloading web.xml");
//                    contextConfig = null;
//                }
//            } else {
//                log("Old web.xml");
//                contextConfig = null;
//            }
//        }
        if (base != null) {
            WebXml webXml = new WebXml(ctx.getContextConfig());
            webXml.readWebXml(base);
        }
    }

    @Override
    public void preInit(ServletContext ctx) {
        ServletContextImpl servletContext =  
            (ServletContextImpl) ctx;
        
        String base = servletContext.getBasePath();
        if (base == null) {
            return; // nothing we can do
        }
        try {
            readWebXml(servletContext, base);
        } catch (ServletException e) {
            // TODO Auto-generated catch block
            throw new RuntimeException(e);
        }
    }
    
}
