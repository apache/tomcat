package org.apache.catalina.ha.context;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.junit.Assert;
import org.junit.Test;

public class TestReplicatedContext extends TomcatBaseTest {

    @Test
    public void testBug57425() throws LifecycleException, IOException, ServletException {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass(ReplicatedContext.class.getName());
        }

        File root = new File("test/webapp");
        Context context = tomcat.addWebapp(host, "", "", root.getAbsolutePath());

        Tomcat.addServlet(context, "test", new AccessContextServlet());
        context.addServletMapping("/access", "test");

        tomcat.start();

        ByteChunk result = getUrl("http://localhost:" + getPort() + "/access");

        Assert.assertEquals("OK", result.toString());

    }

    private static class AccessContextServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            getServletContext().setAttribute("NULL", null);
            resp.getWriter().print("OK");
        }
    }
}
