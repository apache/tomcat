/*
 */
package org.apache.tomcat.lite.http;

import java.io.BufferedReader;
import java.io.IOException;

import org.apache.tomcat.lite.TestMain;

import junit.framework.TestCase;

/**
 * Examples and tests for Tomcat-lite in client mode.
 *
 */
public class ClientTest extends TestCase {

    /**
     * All connectors created this way will share a single
     * IO thread. Each connector will have its keep-alive
     * pool - so it's better to share them.
     *
     * Since I want to test keep-alive works, I use a static one
     */
    static HttpConnector httpCon = HttpClient.newClient();

    /**
     * Start a http server, runs on 8802 - shared by all tests.
     * Will use /echo handler.
     */
    static HttpConnector testServer = TestMain.getTestServer();


    public void testSimpleBlocking() throws IOException {
        HttpRequest req = httpCon.request("http://localhost:8802/echo/test1");
        HttpResponse res = req.waitResponse();

        assertEquals(200, res.getStatus());
        //assertEquals("", res.getHeader(""));

        BufferedReader reader = res.getReader();
        String line1 = reader.readLine();
        assertEquals("REQ HEAD:", line1);
    }

    public void testSimpleCallback() throws IOException {

    }

    public void testGetParams() throws IOException {
    }

    public void testPostParams() throws IOException {
    }

    public void testPostBody() throws IOException {
    }


}
