/*
 */
package org.apache.tomcat.servlets.jspc;

import java.io.File;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldLocationsCache;
import org.apache.tomcat.servlets.jsp.BaseJspLoader;

public class JasperRuntime extends HttpServlet implements BaseJspLoader.JspRuntime {

    // TODO: add DefaultAnnotationProcessor
    // TODO: implement the options
    private JspRuntimeContext jspRuntimeContext;

    public void init(ServletConfig cfg) throws ServletException {
        super.init(cfg);
        ServletContext ctx = cfg.getServletContext();
        init(ctx);
    }
    
    public void doGet(HttpServletRequest req, HttpServletResponse res) {
        
    }

    @Override
    public void init(ServletContext ctx) {
        jspRuntimeContext = new JspRuntimeContext(ctx, new Options() {
            @Override
            public boolean genStringAsCharArray() {
                return false;
            }

            @Override
            public Map getCache() {
                return null;
            }

            @Override
            public int getCheckInterval() {
                return 0;
            }

            @Override
            public boolean getClassDebugInfo() {
                return false;
            }

            @Override
            public String getClassPath() {
                return null;
            }

            @Override
            public String getCompiler() {
                return null;
            }

            @Override
            public String getCompilerClassName() {
                return null;
            }

            @Override
            public String getCompilerSourceVM() {
                return null;
            }

            @Override
            public String getCompilerTargetVM() {
                return null;
            }

            @Override
            public boolean getDevelopment() {
                return false;
            }

            @Override
            public boolean getDisplaySourceFragment() {
                return false;
            }

            @Override
            public boolean getErrorOnUseBeanInvalidClassAttribute() {
                return false;
            }

            @Override
            public boolean getFork() {
                return false;
            }

            @Override
            public String getIeClassId() {
                return null;
            }

            @Override
            public String getJavaEncoding() {
                return null;
            }

            @Override
            public JspConfig getJspConfig() {
                return null;
            }

            @Override
            public boolean getKeepGenerated() {
                return false;
            }

            @Override
            public boolean getMappedFile() {
                return false;
            }

            @Override
            public int getModificationTestInterval() {
                return 0;
            }

            @Override
            public File getScratchDir() {
                return null;
            }

            public boolean getSendErrorToClient() {
                return false;
            }

            @Override
            public TagPluginManager getTagPluginManager() {
                return null;
            }

            @Override
            public TldLocationsCache getTldLocationsCache() {
                return null;
            }

            @Override
            public boolean getTrimSpaces() {
                return false;
            }

            @Override
            public boolean isCaching() {
                return false;
            }

            @Override
            public boolean isPoolingEnabled() {
                return false;
            }

            @Override
            public boolean isSmapDumped() {
                return false;
            }

            @Override
            public boolean isSmapSuppressed() {
                return false;
            }

            @Override
            public boolean isXpoweredBy() {
                return false;
            }
        });
        
        ctx.setAttribute("jasper.jspRuntimeContext", jspRuntimeContext);
    }
}
