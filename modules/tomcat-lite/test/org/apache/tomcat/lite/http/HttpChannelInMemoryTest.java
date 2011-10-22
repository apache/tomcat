/*
 */
package org.apache.tomcat.lite.http;

import java.io.BufferedReader;
import java.io.IOException;

import junit.framework.TestCase;

import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.HttpChannel.RequestCompleted;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;
import org.apache.tomcat.lite.io.MemoryIOConnector;
import org.apache.tomcat.lite.io.MemoryIOConnector.MemoryIOChannel;

// TODO: rename to Http11ConnectionTest
public class HttpChannelInMemoryTest extends TestCase {
    /**
     *  Connection under test
     */
    Http11Connection conn;

    /**
     * Last http channel created by the connection
     */
    volatile HttpChannel http;

    // Input/output for the connection
    MemoryIOConnector.MemoryIOChannel net = new MemoryIOChannel();

    HttpConnector serverConnector = new HttpConnector(null);

    // Callback results for callback tests
    boolean hasBody = false;
    boolean bodyDone = false;
    boolean bodySentDone = false;
    boolean headersDone = false;
    boolean allDone = false;

    public void setUp() throws IOException {
        // Requests will not be serviced - you must manually generate
        // the response.
        serverConnector.setHttpService(null);

        conn = new Http11Connection(serverConnector) {
            protected HttpChannel checkHttpChannel() throws IOException {
                return http = super.checkHttpChannel();
            }
        }.serverMode();
        conn.setSink(net);
    }


    public void test2Req() throws IOException {
        String req = "GET /index.html?q=b&c=d HTTP/1.1\r\n" +
        "Host:  Foo.com \n" +
        "H2:Bar\r\n" +
        "H3: Foo \r\n" +
        " Bar\r\n" +
        "H4: Foo\n" +
        "    Bar\n" +
        "\r\n" +
        "HEAD /r2? HTTP/1.1\n" +
        "Host: Foo.com\r\n" +
        "H3: Foo \r\n" +
        "       Bar\r\n" +
        "H4: Foo\n" +
        " Bar\n" +
        "\r\n";
        net.getIn().append(req);

        //http = lastServer.get(0);
        assertTrue(http.getRequest().method().equals("GET"));
        assertTrue(http.getRequest().protocol().equals("HTTP/1.1"));
        assertEquals(http.getRequest().getMimeHeaders().size(), 4);
        assertEquals(http.getRequest().getMimeHeaders().getHeader("Host").toString(),
                "Foo.com");
        assertEquals(http.getRequest().getMimeHeaders().getHeader("H2").toString(),
                "Bar");

        http.getOut().append("Response1");
        http.getOut().close();
        http.startSending();
        http.release();

        // now second response must be in.
        // the connector will create a new http channel

        //http = lastServer.get(1);

        assertTrue(http.getRequest().method().equals("HEAD"));
        assertTrue(http.getRequest().protocol().equals("HTTP/1.1"));
        assertTrue(http.getRequest().getMimeHeaders().size() == 3);
        assertTrue(http.getRequest().getMimeHeaders().getHeader("Host")
                .equals("Foo.com"));
    }

    public void testHttp11Close() throws IOException {
        String req = "GET /index.html?q=b&c=d HTTP/1.1\r\n" +
        "Host:  Foo.com\n" +
        "Connection: close\n" +
        "\n";
        net.getIn().append(req);

        assertTrue(http.getRequest().method().equals("GET"));
        assertTrue(http.getRequest().protocol().equals("HTTP/1.1"));

        http.getOut().append("Response1");
        http.getOut().close();
        http.startSending();
        http.release();

        assertTrue(net.out.indexOf("connection:close") > 0);
        assertFalse(net.isOpen());
    }

    public void testHttp10Close() throws IOException {
        String req = "GET /index.html?q=b&c=d HTTP/1.0\r\n" +
        "Host:  Foo.com \n" +
        "\r\n";
        net.getIn().append(req);

        assertTrue(http.getRequest().method().equals("GET"));
        assertTrue(http.getRequest().protocol().equals("HTTP/1.0"));

        http.getOut().append("Response1");
        http.getOut().close();
        http.startSending();

        assertTrue(net.out.indexOf("connection:close") > 0);
        assertFalse(net.isOpen());
    }

    public void testHttp10KA() throws IOException {
        String req = "GET /index.html?q=b&c=d HTTP/1.0\r\n" +
        "Connection: Keep-Alive\n" +
        "Host:  Foo.com \n" +
        "\r\n";
        net.getIn().append(req);

        assertTrue(http.getRequest().method().equals("GET"));
        assertTrue(http.getRequest().protocol().equals("HTTP/1.0"));

        http.getOut().append("Hi");
        http.getOut().close();
        http.startSending();

        // after request
        assertEquals(conn.activeHttp, null);

        assertTrue(net.out.indexOf("connection:keep-alive") > 0);
        assertTrue(net.isOpen());
        // inserted since we can calculate the response
        assertEquals(http.getResponse().getHeader("Content-Length"),
                   "2");
    }

    public void testHttp10KANoCL() throws IOException {
        String req = "GET /index.html?q=b&c=d HTTP/1.0\r\n" +
        "Connection: Keep-Alive\n" +
        "Host:  Foo.com \n" +
        "\r\n";
        net.getIn().append(req);

        assertTrue(http.getRequest().method().equals("GET"));
        assertTrue(http.getRequest().protocol().equals("HTTP/1.0"));

        http.getOut().append("Hi");
        http.startSending();

        http.getOut().append("After");
        http.getOut().close();
        http.startSending();

        // after request
        assertEquals(conn.activeHttp, null);

        assertFalse(net.out.indexOf("connection:keep-alive") > 0);
        assertFalse(net.isOpen());
        // inserted since we can calculate the response
        assertEquals(http.getResponse().getHeader("Content-Length"),
                null);
        assertEquals(http.getResponse().getHeader("Transfer-Encoding"),
                null);
    }

    public void testMultiLineHead() throws IOException {
        net.getIn().append("GET / HTTP/1.0\n" +
                "Cookie: 1234\n" +
                "  456 \n" +
                "Connection:   Close\n\n");
        net.getIn().close();

        MultiMap headers = http.getRequest().getMimeHeaders();
        CBuffer cookie = headers.getHeader("Cookie");
        CBuffer conn = headers.getHeader("Connection");
        assertEquals(conn.toString(), "close");
        assertEquals(cookie.toString(), "1234 456");

        assertEquals(http.conn.headRecvBuf.toString(),
                "GET / HTTP/1.0\n" +
                "Cookie: 1234 456   \n" + // \n -> trailing space
                "Connection:   Close\n\n");
    }

    public void testCloseSocket() throws IOException {
        net.getIn().append("GET / HTTP/1.1\n"
                + "Host: localhost\n"
                + "\n");
        assertTrue(((Http11Connection)http.conn).keepAlive());

        net.getIn().close();
        assertFalse(((Http11Connection)http.conn).keepAlive());
    }

    public void test2ReqByte2Byte() throws IOException {
        String req = "GET /index.html?q=b&c=d HTTP/1.1\r\n" +
        "Host:  Foo.com \n" +
        "H2:Bar\r\n" +
        "H3: Foo \r\n" +
        " Bar\r\n" +
        "H4: Foo\n" +
        "    Bar\n" +
        "\r\n" +
        "HEAD /r2? HTTP/1.1\n" +
        "Host: Foo1.com\n" +
        "H3: Foo \r\n" +
        "       Bar\r\n" +
        "\r\n";
        for (int i = 0; i < req.length(); i++) {
            net.getIn().append(req.charAt(i));
        }

        assertTrue(http.getRequest().method().equals("GET"));
        assertTrue(http.getRequest().protocol().equals("HTTP/1.1"));
        assertTrue(http.getRequest().getMimeHeaders().size() == 4);
        assertTrue(http.getRequest().getMimeHeaders().getHeader("Host")
                .equals("Foo.com"));

        // send a response
        http.sendBody.append("Response1");
        http.getOut().close();

        http.startSending(); // This will trigger a pipelined request

        http.release(); // now second response must be in

        assertTrue(http.getRequest().method().equals("HEAD"));
        assertTrue(http.getRequest().protocol().equals("HTTP/1.1"));
        assertTrue(http.getRequest().getMimeHeaders().size() == 2);
        assertTrue(http.getRequest().getMimeHeaders().getHeader("Host")
                .equals("Foo1.com"));

        // send a response - service method will be called
        http.sendBody.append("Response2");
        http.getOut().close();
        http.release(); // now second response must be in


    }

    public void testEndWithoutFlushCallbacks() throws IOException {

        net.getIn().append(POST);

        net.getIn().close();
        http.setCompletedCallback(new RequestCompleted() {
            public void handle(HttpChannel data, Object extra)
            throws IOException {
                allDone = true;
            }
        });

        http.sendBody.queue("Hi");
        http.getOut().close();
        http.startSending(); // will call handleEndSend

        assertTrue(allDone);

    }

    public void testCallbacks() throws IOException {
        // already accepted - will change
        serverConnector.setHttpService(new HttpService() {
            public void service(HttpRequest httpReq, HttpResponse httpRes)
                    throws IOException {

                headersDone = true;
                HttpChannel http = httpReq.getHttpChannel();

                http.setCompletedCallback(new RequestCompleted() {
                    public void handle(HttpChannel data, Object extra)
                    throws IOException {
                        allDone = true;
                    }
                });
                http.setDataReceivedCallback(new IOConnector.DataReceivedCallback() {
                    @Override
                    public void handleReceived(IOChannel ch) throws IOException {
                        if (ch.getIn().isAppendClosed()) {
                            bodyDone = true;
                        }
                    }
                });
                http.setDataFlushedCallback(new IOConnector.DataFlushedCallback() {
                    @Override
                    public void handleFlushed(IOChannel ch) throws IOException {
                        if (ch.getOut().isAppendClosed()) {
                            bodySentDone = true;
                        }
                    }
                });
            }
        });

        // Inject the request
        net.getIn().append("POST / HTTP/1.0\n" +
                "Connection: Close\n" +
                "Content-Length: 4\n\n" +
                "1");
        assertTrue(headersDone);
        net.getIn().append("234");

        net.getIn().close();
        assertTrue(bodyDone);


        http.sendBody.queue("Hi");
        http.getOut().close();
        http.startSending();
        assertTrue(bodySentDone);

        assertTrue(allDone);

    }

    public static String POST = "POST / HTTP/1.0\n" +
        "Connection: Close\n" +
        "Content-Length: 4\n\n" +
        "1234";

    public void testClose() throws IOException {
        net.getIn().append(POST);
        net.getIn().close();

        IOBuffer receiveBody = http.receiveBody;
        IOBuffer appData = receiveBody;
        BBuffer res = BBuffer.allocate(1000);
        appData.readAll(res);

        assertEquals(res.toString(), "1234");
        assertFalse(((Http11Connection)http.conn).keepAlive());

        http.sendBody.queue(res);
        http.getOut().close();
        http.startSending();

        assertTrue(net.getOut().isAppendClosed());
        assertTrue(net.out.toString().indexOf("\n1234") > 0);

    }

    public void testReadLine() throws Exception {
        net.getIn().append("POST / HTTP/1.0\n" +
        		"Content-Length: 28\n\n" +
                "Line 1\n" +
                "Line 2\r\n" +
                "Line 3\r" +
                "Line 4");
        net.getIn().close();

        BufferedReader r = http.getRequest().getReader();
        assertEquals("Line 1", r.readLine());
        assertEquals("Line 2", r.readLine());
        assertEquals("Line 3", r.readLine());
        assertEquals("Line 4", r.readLine());
        assertEquals(null, r.readLine());

    }
}
