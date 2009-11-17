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

package org.apache.tomcat.integration.simple;

import java.util.Enumeration;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.apache.tomcat.integration.ObjectManager;

public class ServletHelper {

    public static ObjectManager getObjectManager(ServletContext ctx) {
        // May be provided by container or a listener
        ObjectManager om = (ObjectManager) ctx.getAttribute(ObjectManager.ATTRIBUTE);
        if (om == null) {
            // Default
            SimpleObjectManager som = new SimpleObjectManager();
            om = som;
            
            // All context init params are set
            Enumeration namesE = ctx.getInitParameterNames();
            while (namesE.hasMoreElements()) {
                String n = (String) namesE.nextElement();
                String v = ctx.getInitParameter(n);
                som.getProperties().put(n, v);
            }
            
            ctx.setAttribute(ObjectManager.ATTRIBUTE, om);
            // load context settings
        }
        return om;
    }

    public static void initServlet(Servlet s) {
        ServletConfig sc = s.getServletConfig();
        String name = sc.getServletName();
        String ctx = sc.getServletContext().getContextPath();
        
        // Servlets are named:...
        
        ObjectManager om = getObjectManager(sc.getServletContext());
        
        String dn = ctx + ":" + name;
        
        // If SimpleObjectManager ( or maybe other supporting dynamic config ):
        if (om instanceof SimpleObjectManager) {
            SimpleObjectManager som = (SimpleObjectManager) om;
            
            
        }
        
        
        om.bind(dn, s);
        
    }
    
}
