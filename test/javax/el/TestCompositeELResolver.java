package javax.el;

import java.io.File;

import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestCompositeELResolver extends TomcatBaseTest {

    public void testBug50408() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =  new File("test/webapp-3.0");
        // app dir is relative to server home
        StandardContext ctxt = (StandardContext) tomcat.addWebapp(null,
                "/test", appDir.getAbsolutePath());
        
        // This test needs the JSTL libraries
        File lib = new File("webapps/examples/WEB-INF/lib");
        ctxt.setAliases("/WEB-INF/lib=" + lib.getCanonicalPath());
        
        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug50408.jsp", new ByteChunk(), null);
        
        assertEquals(HttpServletResponse.SC_OK, rc);
    }
}
