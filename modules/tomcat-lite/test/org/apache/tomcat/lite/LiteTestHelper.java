/*
 */
package org.apache.tomcat.lite;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.lite.coyote.CoyoteHttp;
import org.apache.tomcat.util.buf.ByteChunk;

public class LiteTestHelper {

    public static void initServletsAndRun(TomcatLite lite, int port) throws ServletException, IOException {
        ServletContextImpl ctx = 
          (ServletContextImpl) lite.addServletContext(null, null, "/test1");
        
        ctx.addServlet("test", new SimpleServlet());
        ctx.addMapping("/1stTest", "test");

        ctx.addServlet("testException", new HttpServlet() {
            public void doGet(HttpServletRequest req, HttpServletResponse res) 
              throws IOException {
              throw new NullPointerException();
            }
          });
          ctx.addMapping("/testException", "testException");
        
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
        CoyoteHttp coyoteAdapter = (CoyoteHttp) lite.getConnector();
        coyoteAdapter.getConnectors().setPort(port);
        coyoteAdapter.getConnectors().setDaemon(daemon);
    }
    
    /**
     *  Get url using URLConnection.
     */
    public static ByteChunk getUrl(String path) throws IOException {
    
        ByteChunk out = new ByteChunk();
        
        URL url = new URL(path);
        URLConnection connection = url.openConnection();
        connection.setReadTimeout(5000);
        connection.connect();
        InputStream is = connection.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        byte[] buf = new byte[2048];
        int rd = 0;
        while((rd = bis.read(buf)) > 0) {
            out.append(buf, 0, rd);
        }
        return out;
    }

    /** 
     * Create a ServletRequestImpl object that can be used with 
     *  TomcatLite.service(request).
     *  
     * All output will be added to the ByteChunk out.
     * 
     * This requires no HTTP connector.
     * 
     * @see TomcatLiteNoConnector
     */
    public static ServletRequestImpl createMessage(TomcatLite lite, 
                                                   String uri,
                                                   final ByteChunk out) {
       ServletRequestImpl req = lite.createMessage();
       req.setRequestURI(uri);
       ServletResponseImpl res = req.getResponse();
       res.getCoyoteResponse().setOutputBuffer(
                   new ByteChunkOutputBuffer(out));
       return req;
     }
    

    static class ByteChunkOutputBuffer implements OutputBuffer {
        
        protected ByteChunk output = null;
    
        public ByteChunkOutputBuffer(ByteChunk output) {
          this.output = output;
        }
    
        public int doWrite(ByteChunk chunk, Response response) 
            throws IOException {
          output.append(chunk);
          return chunk.getLength();
        }
    }
    
        
}
