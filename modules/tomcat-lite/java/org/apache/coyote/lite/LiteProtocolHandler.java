package org.apache.coyote.lite;



import java.io.IOException;
import java.util.Iterator;

import org.apache.coyote.Adapter;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.SocketConnector;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;

/**
 * Work in progress - use the refactored http as a coyote connector.
 * Just basic requests work right now - need to implement all the 
 * methods of coyote.
 * 
 * 
 * @author Costin Manolache
 */
public class LiteProtocolHandler implements ProtocolHandler {

    Adapter adapter;
    
    @Override
    public void destroy() throws Exception {
    }

    @Override
    public Adapter getAdapter() {
        return adapter;
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return null;
    }

    @Override
    public void init() throws Exception {
    }

    @Override
    public void pause() throws Exception {
    }

    @Override
    public void resume() throws Exception {
    }

    @Override
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
        
    }

    int port = 8999;
    
    public void setPort(int port) {
        this.port = port;
    }
    
    @Override
    public void setAttribute(String name, Object value) {
        System.err.println("setAttribute " + name + " " + value);
    }

    @Override
    public void start() throws Exception {
        HttpConnector c = new HttpConnector(new SocketConnector());
        c.setPort(port);
        
//        c.setDebug(true);
//        c.setDebugHttp(true);
        
        c.getDispatcher().setDefaultService(new HttpService() {
            @Override
            public void service(HttpRequest httpReq, HttpResponse httpRes)
                    throws IOException {
                coyoteService(httpReq, httpRes);
            }

        });
        c.start();
    }
    
    private void wrap(MessageBytes dest, CBuffer buffer) {
        dest.setChars(buffer.array(), buffer.position(), 
                buffer.length());
    }

    private void coyoteService(HttpRequest httpReq, final HttpResponse httpRes) {
        Request req = new Request();
        req.setInputBuffer(new InputBuffer() {
            @Override
            public int doRead(ByteChunk chunk, Request request)
                    throws IOException {
                // TODO
                return 0;
            }
        });
        Response res = new Response();
        res.setOutputBuffer(new OutputBuffer() {

            @Override
            public int doWrite(org.apache.tomcat.util.buf.ByteChunk chunk,
                    Response response) throws IOException {
                httpRes.getBody().append(chunk.getBuffer(), chunk.getStart(),
                        chunk.getLength());
                return chunk.getLength();
            }
            
        });
        
        // TODO: turn http request into a coyote request - copy all fields, 
        // add hooks where needed.
        
        wrap(req.decodedURI(), httpReq.decodedURI());
        wrap(req.method(), httpReq.method());
        wrap(req.protocol(), httpReq.protocol());
        wrap(req.requestURI(), httpReq.requestURI());
        // Same for response.
        
        try {
            
            adapter.service(req, res);
            
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    
    
}
