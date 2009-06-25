/*
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
