package org.apache.tomcat.util.http.mapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestMapperWelcomeFiles extends TomcatBaseTest {

    public void testWelcomeFileNotStrict() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        
        File appDir = new File("test/webapp-3.0");

        StandardContext ctxt = (StandardContext) tomcat.addWebapp(null, "/test",
                appDir.getAbsolutePath());
        Tomcat.addServlet(ctxt, "Ok", new OkServlet());
        ctxt.setReplaceWelcomeFiles(true);
        ctxt.addWelcomeFile("index.jsp");
        ctxt.addWelcomeFile("index.do");
        
        tomcat.start();
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/test/welcome-files", bc, new HashMap<String,List<String>>());
        assertEquals(HttpServletResponse.SC_OK, rc);
        assertTrue(bc.toString().contains("JSP"));
        
        rc = getUrl("http://localhost:" + getPort() +
                "/test/welcome-files/sub", bc,
                new HashMap<String,List<String>>());
        assertEquals(HttpServletResponse.SC_OK, rc);
        assertTrue(bc.toString().contains("Servlet"));
    }
    
    public void testWelcomeFileStrict() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        
        File appDir = new File("test/webapp-3.0");

        StandardContext ctxt = (StandardContext) tomcat.addWebapp(null, "/test",
                appDir.getAbsolutePath());
        Tomcat.addServlet(ctxt, "Ok", new OkServlet());
        ctxt.setReplaceWelcomeFiles(true);
        ctxt.addWelcomeFile("index.jsp");
        ctxt.addWelcomeFile("index.do");
        
        // Simulate STRICT_SERVLET_COMPLIANCE
        ctxt.setResourceOnlyServlets("");
        
        tomcat.start();
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/test/welcome-files", bc, new HashMap<String,List<String>>());
        assertEquals(HttpServletResponse.SC_OK, rc);
        assertTrue(bc.toString().contains("JSP"));
        
        rc = getUrl("http://localhost:" + getPort() +
                "/test/welcome-files/sub", bc,
                new HashMap<String,List<String>>());
        assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
    }

    private static class OkServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().write("OK-Servlet");
        }
    }
}
