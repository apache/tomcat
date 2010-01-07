package org.apache.jasper.compiler;

import java.io.File;
import java.io.IOException;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestGenerator extends TomcatBaseTest {
    
    public void testBug45015a() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45015a.jsp");
        
        String result = res.toString();
        // Beware of the differences between escaping in JSP attributes and
        // in Java Strings
        assertTrue(result.indexOf("00-hello 'world'") > 0);
        assertTrue(result.indexOf("01-hello 'world") > 0);
        assertTrue(result.indexOf("02-hello world'") > 0);
        assertTrue(result.indexOf("03-hello world'") > 0);
        assertTrue(result.indexOf("04-hello world\"") > 0);
        assertTrue(result.indexOf("05-hello \"world\"") > 0);
        assertTrue(result.indexOf("06-hello \"world") > 0);
        assertTrue(result.indexOf("07-hello world\"") > 0);
        assertTrue(result.indexOf("08-hello world'") > 0);
        assertTrue(result.indexOf("09-hello world\"") > 0);
    }

    public void testBug45015b() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        Exception e = null;
        try {
            getUrl("http://localhost:" + getPort() + "/test/bug45015b.jsp");
        } catch (IOException ioe) {
            e = ioe;
        }

        // Failure is expected
        assertNotNull(e);
    }
}
