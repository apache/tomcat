package org.apache.jasper.runtime;

import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.junit.Assert;
import org.junit.Test;

public class TestJspContextWrapper extends TomcatBaseTest {

    @Test
    public void testELTagFilePageContext() throws Exception {
        getTomcatInstanceTestWebapp(true, true);

        ByteChunk out = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() + "/test/bug5nnnn/bug58178.jsp", out, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String result = out.toString();

        Assert.assertTrue(result, result.contains("PASS"));
    }

    public void testELTagFileImports() {

    }
}
