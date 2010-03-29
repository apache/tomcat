package org.apache.coyote.http11;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestInternalInputBuffer extends TomcatBaseTest {
    
    /**
     * Test case for https://issues.apache.org/bugzilla/show_bug.cgi?id=48839
     * with BIO
     */
    public void testBug48839BIO() {
        
        Bug48839Client client = new Bug48839Client();
        client.setPort(getPort());
        
        // BIO test
        client.doRequest(false);
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
    }

    
    /**
     * Test case for https://issues.apache.org/bugzilla/show_bug.cgi?id=48839
     * with NIO
     */
    public void testBug48839NIO() {
        
        Bug48839Client client = new Bug48839Client();
        client.setPort(getPort());
        
        // NIO test
        client.doRequest(true);
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
    }

    /**
     * Bug 48839 test client.
     */
    private class Bug48839Client extends SimpleHttpClient {
                
        private Exception doRequest(boolean useNio) {
        
            Tomcat tomcat = getTomcatInstance();
            
            if (useNio) {
                Connector connector = 
                    new Connector("org.apache.coyote.http11.Http11NioProtocol");
                connector.setPort(getPort());
                tomcat.getService().addConnector(connector);
                tomcat.setConnector(connector);
            }
            
            Context root = tomcat.addContext("", TEMP_DIR);
            Tomcat.addServlet(root, "Bug48839", new Bug48839Servlet());
            root.addServletMapping("/test", "Bug48839");

            try {
                tomcat.start();

                // Open connection
                connect();
                
                String[] request = new String[1];
                request[0] =
                    "GET http://localhost:8080/test HTTP/1.1" + CRLF +
                    "X-Bug48839: abcd" + CRLF +
                    "\tefgh" + CRLF +
                    "Connection: close" + CRLF +
                    CRLF;
                
                setRequest(request);
                processRequest(); // blocks until response has been read
                
                // Close the connection
                disconnect();
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        public boolean isResponseBodyOK() {
            if (getResponseBody() == null) {
                return false;
            }
            if (!getResponseBody().contains("abcd\tefgh")) {
                return false;
            }
            return true;
        }
        
    }

    private static class Bug48839Servlet extends HttpServlet {
        
        private static final long serialVersionUID = 1L;

        /**
         * Only interested in the request headers from a GET request
         */
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Just echo the header value back as plain text
            resp.setContentType("text/plain");
            
            PrintWriter out = resp.getWriter();
            
            Enumeration<String> values = req.getHeaders("X-Bug48839");
            while (values.hasMoreElements()) {
                out.println(values.nextElement());
            }
        }
    }
}
