package org.apache.coyote.http11;

import java.io.File;

import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestAbstractHttp11Processor extends TomcatBaseTest {

    public void testWithTEVoid() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        
        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: void" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }

    public void testWithTEBuffered() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        
        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: buffered" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    public void testWithTEIdentity() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        
        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: identity" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse200());
        assertTrue(client.getResponseBody().contains("test - data"));
    }


    public void testWithTESavedRequest() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        
        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: savedrequest" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    public void testWithTEUnsupported() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        
        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: unsupported" + SimpleHttpClient.CRLF +
            "Content-Length: 9" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "test=data";

        Client client = new Client();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse501());
    }


    private static final class Client extends SimpleHttpClient {
        @Override
        public boolean isResponseBodyOK() {
            return getResponseBody().contains("test - data");
        }
    }
}
