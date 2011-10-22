/*
 */
package org.apache.tomcat.lite.proxy;

import java.io.IOException;

import junit.framework.TestCase;

import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpConnector.HttpConnection;
import org.apache.tomcat.lite.io.MemoryIOConnector;
import org.apache.tomcat.lite.io.MemoryIOConnector.MemoryIOChannel;

public class SmallProxyTest extends TestCase {

    MemoryIOConnector memoryServerConnector =
        new MemoryIOConnector();

    MemoryIOConnector memoryClientConnector =
        new MemoryIOConnector().withServer(memoryServerConnector);


    HttpConnector httpCon = new HttpConnector(memoryServerConnector) {
        @Override
        public HttpChannel get(CharSequence target) throws IOException {
            throw new IOException();
        }
        public HttpChannel getServer() {
            lastServer = new HttpChannel().serverMode(true);
            lastServer.setConnector(this);
            //lastServer.withIOConnector(memoryServerConnector);
            return lastServer;
        }
    };

    HttpConnector httpClient = new HttpConnector(memoryClientConnector) {
        @Override
        public HttpChannel get(CharSequence target) throws IOException {
            lastClient = new HttpChannel();
            lastClient.setConnector(this);
            return lastClient;
        }
        public HttpChannel get(String host, int port) throws IOException {
            lastClient = new HttpChannel();
            lastClient.setConnector(this);
            return lastClient;
        }
        public HttpChannel getServer() {
            throw new RuntimeException();
        }
    };

    HttpChannel lastServer;
    HttpChannel lastClient;

    boolean hasBody = false;
    boolean bodyDone = false;
    boolean bodySentDone = false;
    boolean headersDone = false;
    boolean allDone = false;


    //MemoryIOChannel clientNet = new MemoryIOChannel();

    MemoryIOConnector.MemoryIOChannel net = new MemoryIOChannel();
    HttpChannel http;

    HttpConnection serverConnection;

    public void setUp() throws IOException {
        http = httpCon.getServer();
        serverConnection = httpCon.handleAccepted(net);
    }

    /**
     * More complicated test..
     * @throws IOException
     */
    public void testProxy() throws IOException {
        httpCon.setHttpService(new HttpProxyService()
            .withSelector(memoryClientConnector)
            .withHttpClient(httpClient));

        net.getIn().append("GET http://www.apache.org/ HTTP/1.0\n" +
                "Connection: Close\n\n");
        net.getIn().close();

        // lastClient.rawSendBuffers has the request sent by proxy
        lastClient.getNet().getIn()
            .append("HTTP/1.0 200 OK\n\nHi\n");
        lastClient.getNet().getIn()
            .append("world\n");

        // TODO: check what the proxy sent
        // lastClient.getOut();

        // will also trigger 'release' - both sides are closed.
        lastClient.getNet().getIn().close();

        // wait response...
        // http.sendBody.close();
        String res = net.out.toString();
        assertTrue(res.indexOf("Hi\nworld\n") > 0);
        assertTrue(res.indexOf("HTTP/1.0 200 OK") == 0);
        assertTrue(res.indexOf("tomcatproxy") > 0);

    }
}
