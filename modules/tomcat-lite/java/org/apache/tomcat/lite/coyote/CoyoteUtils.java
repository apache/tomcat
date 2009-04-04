/*
 */
package org.apache.tomcat.lite.coyote;

import java.io.IOException;

import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.mapper.MappingData;

public class CoyoteUtils {
    static ByteChunk space = new ByteChunk(1);
    static ByteChunk col = new ByteChunk(1);
    static ByteChunk crlf = new ByteChunk(2);
    static {
        try {
            space.append(' ');
            col.append(':');
            crlf.append('\r');
            crlf.append('\n');
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static String getContextPath(Request request) {
        MappingData md = MapperAdapter.getMappingData(request);
        String ctxPath = (md.contextPath.isNull()) ? "/" : 
            md.contextPath.toString();
        return ctxPath;
    }

    public static String getPathInfo(Request request) {
        MappingData md = MapperAdapter.getMappingData(request);
        String ctxPath = (md.pathInfo.isNull()) ? "/" : 
            md.pathInfo.toString();
        return ctxPath;
    }
    public static String getServletPath(Request request) {
        MappingData md = MapperAdapter.getMappingData(request);
        String ctxPath = (md.wrapperPath.isNull()) ? "/" : 
            md.wrapperPath.toString();
        return ctxPath;
    }
    
    // TODO: collate all notes in a signle file
    static int READER_NOTE = 5;
    
    public static MessageReader getReader(Request req) {
        MessageReader r = (MessageReader) req.getNote(READER_NOTE);
        if (r == null) {
            r = new MessageReader();
            r.setRequest(req);
            req.setNote(READER_NOTE, r);
        }
        return r;
    }
    
    /** 
     * Convert the request to bytes, ready to send.
     */
    public static void serializeRequest(Request req, 
                                        ByteChunk reqBuf) throws IOException {
        req.method().toBytes();
        if (!req.unparsedURI().isNull()) {
            req.unparsedURI().toBytes();
        }
        req.protocol().toBytes();

        reqBuf.append(req.method().getByteChunk());
        reqBuf.append(space);
        if (req.unparsedURI().isNull()) {
            req.requestURI().toBytes();

            reqBuf.append(req.requestURI().getByteChunk());      
        } else {
            reqBuf.append(req.unparsedURI().getByteChunk());
        }
        reqBuf.append(space);
        reqBuf.append(req.protocol().getByteChunk());
        reqBuf.append(crlf);
        // Headers
        MimeHeaders mimeHeaders = req.getMimeHeaders();
        boolean hasHost = false;
        for (int i = 0; i < mimeHeaders.size(); i++) {
            MessageBytes name = mimeHeaders.getName(i);
            name.toBytes();
            reqBuf.append(name.getByteChunk());
            if (name.equalsIgnoreCase("host")) {
                hasHost = true;
            }
            reqBuf.append(col);
            mimeHeaders.getValue(i).toBytes();
            reqBuf.append(mimeHeaders.getValue(i).getByteChunk());
            reqBuf.append(crlf);
        }
        if (!hasHost) {
            reqBuf.append("Host: localhost\r\n".getBytes(), 0, 17);
        }
        reqBuf.append(crlf);
    }

    
}
