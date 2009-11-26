/*
 */
package org.apache.tomcat.lite.servlet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.servlet.ServletContextImpl;
import org.apache.tomcat.lite.servlet.TomcatLite;

public class LiteTestHelper {
    static TomcatLite lite;

    public static ServletContextImpl addContext(TomcatLite lite) throws ServletException {
        ServletContextImpl ctx = 
            (ServletContextImpl) lite.addServletContext(null, null, "/test1");
          

          ctx.add("testException", new HttpServlet() {
              public void doGet(HttpServletRequest req, HttpServletResponse res) 
                throws IOException {
                throw new NullPointerException();
              }
            });
            ctx.addMapping("/testException", "testException");

            
            ctx.add("test", new HttpServlet() {
                public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
                    res.addHeader("Foo", "Bar");
                    res.getWriter().write("Hello world");
                }
            });
            
            ctx.addMapping("/1stTest", "test");
            
            
            return ctx;
    }
    
    public static void startLite() throws IOException, ServletException {
        if (lite == null) {
            lite = new TomcatLite();

            LiteTestHelper.addContext(lite);
            lite.start();

            lite.startConnector();
        }
    }
    
    public static void initServletsAndRun(TomcatLite lite, int port) throws ServletException, IOException {
        addContext(lite);
        lite.init();
        lite.start(); 


        if (port > 0) {
            // This should be added after all local initialization to avoid
            // the server from responding.
            // Alternatively, you can load this early but set it to return
            // 'unavailable' if load balancers depend on this.
            addConnector(lite, port, true);
            
            // At this point we can add contexts and inject requests, if we want to 
            // do it over HTTP need to start the connector as well.
            lite.startConnector(); 
        }
    }
    
    public static void addConnector(TomcatLite lite, 
                                    int port, boolean daemon) { 
        lite.setPort(port);
    }
    
    /**
     *  Get url using URLConnection.
     */
    public static BBuffer getUrl(String path) throws IOException {
    
        BBuffer out = BBuffer.allocate(4096);
        
        URL url = new URL(path);
        URLConnection connection = url.openConnection();
        connection.setReadTimeout(5000);
        connection.connect();
        InputStream is = connection.getInputStream();
        out.readAll(is);
        return out;
    }
    
//    static class ByteChunkOutputBuffer implements OutputBuffer {
//        
//        protected ByteChunk output = null;
//    
//        public ByteChunkOutputBuffer(ByteChunk output) {
//          this.output = output;
//        }
//    
//        public int doWrite(ByteChunk chunk, Response response) 
//            throws IOException {
//          output.append(chunk);
//          return chunk.getLength();
//        }
//    }
    
        
}
