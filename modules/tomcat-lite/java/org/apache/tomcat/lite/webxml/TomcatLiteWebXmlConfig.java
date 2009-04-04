/*
 */
package org.apache.tomcat.lite.webxml;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.tomcat.lite.ServletContextImpl;
import org.apache.tomcat.lite.ContextPreinitListener;

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
