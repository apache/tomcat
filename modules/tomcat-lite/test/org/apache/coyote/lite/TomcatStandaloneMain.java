/*
 */
package org.apache.coyote.lite;

import javax.servlet.ServletException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.lite.TestMain;

/**
 * Startup tomcat + coyote lite connector.
 * No config files used.
 *
 * @author Costin Manolache
 */
public class TomcatStandaloneMain {

    public static Tomcat setUp(int port) {
        try {
            Tomcat tomcat = new Tomcat();

            tomcat.setPort(port);
            String base = TestMain.findDir("/output/build");
            tomcat.setBaseDir(base);
            // Absolute path - tomcat6 and 7 are different,
            // 7 adds webapps.
            tomcat.addWebapp("/", base + "/webapps/ROOT");

            LiteProtocolHandler litePH = setUp(tomcat, port);

            tomcat.start();
            return tomcat;
        } catch (LifecycleException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ServletException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public static LiteProtocolHandler setUp(Tomcat tomcat, int port) {
        Connector connector;
        try {
            connector = new Connector(LiteProtocolHandler.class.getName());
            tomcat.getService().addConnector(connector);
            connector.setPort(port);
            tomcat.setConnector(connector);
            LiteProtocolHandler ph =
                (LiteProtocolHandler) connector.getProtocolHandler();
            return ph;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
