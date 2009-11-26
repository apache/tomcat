/*
 */
package org.apache.tomcat.lite.servlet;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.servlets.jsp.BaseJspLoader;

public class JspLoader extends BaseJspLoader {

    public ClassLoader getClassLoader(ServletContext ctx) {
        return ((ServletContextImpl) ctx).getClassLoader();
    }

    public String getClassPath(ServletContext ctx) {
        return ((ServletContextImpl) ctx).getClassPath();
    }
    
    protected void compileAndInitPage(ServletContext ctx, 
            String jspUri, 
            ServletConfig cfg,
            String classPath) 
                throws ServletException, IOException {
        
        ServletContextImpl ctxI = (ServletContextImpl)ctx;
        HttpChannel server = ctxI.getEngine().getLocalConnector().getServer();
        
        HttpRequest req = server.getRequest();

        req.addParameter("uriroot", ctx.getRealPath("/"));
        req.addParameter("jspFiles", jspUri.substring(1));
        req.addParameter("classPath", classPath);
        req.addParameter("pkg", getPackage(ctx, jspUri));

        // TODO: init params to specify 
        // TODO: remote request
        RequestDispatcher disp = ctx.getNamedDispatcher("jspc");

        ServletRequestImpl sreq = 
            TomcatLite.getFacade(req);
        sreq.setContext((ServletContextImpl) ctx);
        disp.forward(sreq, sreq.getResponse());
    }
}
