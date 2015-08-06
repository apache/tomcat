package org.apache.jasper.runtime;

import java.io.File;

import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.junit.Assert;
import org.junit.Test;

public class TestJspContextWrapper extends TomcatBaseTest {

    @Test
    public void testELTagFilePageContext() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        // app dir is relative to server home
        StandardContext ctxt = (StandardContext) tomcat.addWebapp(
                null, "/test", appDir.getAbsolutePath());

        // This test needs the JSTL libraries
        File lib = new File("webapps/examples/WEB-INF/lib");
        ctxt.setAliases("/WEB-INF/lib=" + lib.getCanonicalPath());

        tomcat.start();
        
        ByteChunk out = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() + "/test/bug5nnnn/bug58178.jsp", out, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String result = out.toString();

        Assert.assertTrue(result, result.contains("PASS"));
    }
}
