/*
 */
package org.apache.coyote.lite;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;

import org.apache.tomcat.lite.http.HttpClient;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.io.BBuffer;

/**
 * Very simple test.
 */
public class TomcatLiteCoyoteTest extends TestCase {
    static int port = 8884;
    static {
        Logger.getLogger("org.apache.catalina.core.StandardService").setLevel(Level.WARNING);
        Logger.getLogger("org.apache.catalina.core.StandardEngine").setLevel(Level.WARNING);
        Logger.getLogger("org.apache.catalina.startup.ContextConfig").setLevel(Level.WARNING);
    }
    static Tomcat main = TomcatStandaloneMain.setUp(port);

    public void testSimple() throws IOException {
        HttpConnector clientCon = HttpClient.newClient();

        HttpChannel ch = clientCon.get("localhost", port);
        ch.getRequest().setRequestURI("/index.html");
        ch.getRequest().send();

        BBuffer res = ch.readAll(null, 0);

        assertTrue(res.toString(),
                res.toString().indexOf("<title>Apache Tomcat</title>") >= 0);
    }


}
