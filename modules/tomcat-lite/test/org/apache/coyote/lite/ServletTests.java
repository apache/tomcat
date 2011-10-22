/*
 */
package org.apache.coyote.lite;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.catalina.LifecycleException;
import org.apache.tomcat.test.watchdog.WatchdogClient;

import junit.framework.Test;

/**
 * Wrapper to run watchdog.
 *
 */
public class ServletTests extends WatchdogClient {


    public ServletTests() {
        super();
        goldenDir = getWatchdogdir() + "/src/clients/org/apache/jcheck/servlet/client/";
        testMatch =
            //"HttpServletResponseWrapperSetStatusMsgTest";
            //"ServletContextAttributeAddedEventTest";
            null;
            // ex: "ServletToJSP";
        file = getWatchdogdir() + "/src/conf/servlet-gtest.xml";
        targetMatch = "gtestservlet-test";

        port = 8883;
        exclude = new String[] {
                "DoInit1Test", // tomcat returns 404 if perm. unavailable
                "HttpServletDoInit1Test",
                "GetMajorVersionTest", // tomcat7
                "GetMinorVersionTest",
                "ServletToJSPErrorPageTest",
                "ServletToJSPError502PageTest",
        };
    }

    public ServletTests(String name) {
       this();
       super.single = name;
       port = 8883;
    }

    protected void beforeSuite() {
        // required for the tests
        System.setProperty("org.apache.coyote.USE_CUSTOM_STATUS_MSG_IN_HEADER",
                "true");

        try {
            initServerWithWatchdog(getWatchdogdir());
        } catch (ServletException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void initServerWithWatchdog(String wdDir) throws ServletException,
            IOException {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);

        File f = new File(wdDir + "/build/webapps");
        tomcat.setBaseDir(f.getAbsolutePath());

        for (String s : new String[] {
                "servlet-compat",
                "servlet-tests",
                "jsp-tests"} ) {
            tomcat.addWebapp("/" + s, f.getCanonicalPath() + "/" + s);
        }

        TomcatStandaloneMain.setUp(tomcat, port);

        try {
            tomcat.start();
        } catch (LifecycleException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.err.println("Init done");
    }

    /**
     * Magic JUnit method
     */
    public static Test suite() {
        return new ServletTests().getSuite();
    }
}
