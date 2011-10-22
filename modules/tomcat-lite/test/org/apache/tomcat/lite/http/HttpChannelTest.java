/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;

import junit.framework.TestCase;

import org.apache.tomcat.lite.io.BBuffer;

public class HttpChannelTest extends TestCase {

    HttpChannel ch = new HttpChannel().serverMode(true);
    Http11Connection con = new Http11Connection(null).serverMode();
    HttpRequest req = ch.getRequest();


    BBuffer head = BBuffer.allocate();
    BBuffer line = BBuffer.wrapper();
    BBuffer name = BBuffer.wrapper();
    BBuffer value = BBuffer.wrapper();

    BBuffer statusB = BBuffer.wrapper();
    BBuffer msgB = BBuffer.wrapper();
    BBuffer methodB = BBuffer.wrapper();
    BBuffer queryB = BBuffer.wrapper("");
    BBuffer requestB = BBuffer.wrapper();
    BBuffer protoB = BBuffer.wrapper();

    BBuffer l7 = BBuffer.wrapper("GET \n");
    BBuffer l8 = BBuffer.wrapper("GET /\n");
    BBuffer l9 = BBuffer.wrapper("GET /a?b\n");
    BBuffer l10 = BBuffer.wrapper("GET /a?b HTTP/1.0\n");
    BBuffer l11 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b");
    BBuffer l12 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b\n");

    BBuffer f1 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b\n\n");
    BBuffer f2 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b\r\n\r\n");
    BBuffer f3 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b\r\r");
    BBuffer f4 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b\r\n\r");

    public void reqTest(String lineS, String method, String req,
            String qry, String proto) throws IOException {
        BBuffer line = BBuffer.wrapper(lineS);
        queryB.recycle();
        protoB.recycle();
        requestB.recycle();
        methodB.recycle();
        con.parseRequestLine(line, methodB, requestB, queryB, protoB);
        assertEquals(proto, protoB.toString());
        assertEquals(req, requestB.toString());
        assertEquals(qry, queryB.toString());
        assertEquals(method, methodB.toString());
    }

    public void testParams() throws IOException {
        MultiMap params = processQry("a=b&c=d");
        assertEquals("b", params.getString("a"));
    }

    private MultiMap processQry(String qry) throws IOException {
        BBuffer head = BBuffer.wrapper("GET /a?" + qry + " HTTP/1.0\n" +
        		"Host: a\n\n");
        con.parseMessage(ch, head);
        MultiMap params = req.getParameters();
        return params;
    }

    public void testParseReq() throws IOException {
        reqTest("GET / HTTP/1.0", "GET", "/", "", "HTTP/1.0");
        reqTest("GET", "GET", "", "", "");
        reqTest("GET   / HTTP/1.0", "GET", "/", "", "HTTP/1.0");
        reqTest("GET /     HTTP/1.0", "GET", "/", "", "HTTP/1.0");
        reqTest("GET /?b HTTP/1.0", "GET", "/", "b", "HTTP/1.0");
        reqTest("GET ?a HTTP/1.0", "GET", "", "a", "HTTP/1.0");
        reqTest("GET a HTTP/1.0", "GET", "a", "", "HTTP/1.0");
        reqTest("GET a? HTTP/1.0", "GET", "a", "", "HTTP/1.0");
    }

    public void headTest(String headS, String expName, String expValue,
            String expLine, String expRest) throws IOException {
        head = BBuffer.wrapper(headS);
        head.readLine(line);
        con.parseHeader(ch, head, line, name, value);

        assertEquals(expName, name.toString());
        assertEquals(expValue, value.toString());

        assertEquals(expLine, line.toString());
        assertEquals(expRest, head.toString());
    }

    public void testParseHeader() throws IOException {
        headTest("a:b\n", "a", "b", "", "");
        headTest("a :b\n", "a", "b", "", "");
        headTest("a : b\n", "a", "b", "", "");
        headTest("a :  b\n", "a", "b", "", "");
        headTest("a :  b c \n", "a", "b c", "", "");
        headTest("a :  b c\n", "a", "b c", "", "");
        headTest("a :  b  c\n", "a", "b c", "", "");
        headTest("a :  b  \n c\n", "a", "b c", "", "");
        headTest("a :  b  \n  c\n", "a", "b c", "", "");
        headTest("a :  b  \n  c\nd:", "a", "b c", "", "d:");

    }

    public void responseTest(String lineS, String proto, String status,
            String msg) throws IOException {
        protoB.recycle();
        statusB.recycle();
        msgB.recycle();
        BBuffer line = BBuffer.wrapper(lineS);
        con.parseResponseLine(line,
                protoB, statusB, msgB);
        assertEquals(proto, protoB.toString());
        assertEquals(status, statusB.toString());
        assertEquals(msg, msgB.toString());
    }

    public void testResponse() throws Exception {
        responseTest("HTTP/1.1 200 OK", "HTTP/1.1", "200", "OK");
        responseTest("HTTP/1.1  200 OK", "HTTP/1.1", "200", "OK");
        responseTest("HTTP/1.1  200", "HTTP/1.1", "200", "");
        responseTest("HTTP/1.1", "HTTP/1.1", "", "");
    }


}
