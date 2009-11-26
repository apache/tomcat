/*
 */
package org.apache.tomcat.lite.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.BufferedIOReader;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOInputStream;
import org.apache.tomcat.lite.io.IOOutputStream;
import org.apache.tomcat.lite.io.IOReader;
import org.apache.tomcat.lite.io.IOWriter;
import org.apache.tomcat.lite.io.UrlEncoding;


/**
 * Basic Http request or response message.
 * 
 * Because the HttpChannel can be used for both client and
 * server, and to make proxy and other code simpler - the request 
 * and response are represented by the same class.
 * 
 * @author Costin Manolache
 */
public abstract class HttpMessage {

    public static enum State {
        HEAD,
        BODY_DATA,
        DONE
    }

    /**
     * Raw, off-the-wire message.
     */
    public static class HttpMessageBytes {
        BBuffer head1 = BBuffer.wrapper();
        BBuffer head2 = BBuffer.wrapper();
        BBuffer proto = BBuffer.wrapper();

        BBuffer query = BBuffer.wrapper();
        
        List<BBuffer> headerNames = new ArrayList<BBuffer>();
        List<BBuffer> headerValues  = new ArrayList<BBuffer>();
        
        int headerCount;
        
        public BBuffer status() {
            return head1;
        }

        public BBuffer method() {
            return head1;
        }

        public BBuffer url() {
            return head2;
        }

        public BBuffer query() {
            return query;
        }

        public BBuffer protocol() {
            return proto;
        }

        public BBuffer message() {
            return head2;
        }
        
        public int addHeader() {
            headerNames.add(BBuffer.wrapper());
            headerValues.add(BBuffer.wrapper());
            return headerCount++;
        }
        
        public BBuffer getHeaderName(int i) {
            if (i >= headerNames.size()) {
                return null;
            }
            return headerNames.get(i);
        }

        public BBuffer getHeaderValue(int i) {
            if (i >= headerValues.size()) {
                return null;
            }
            return headerValues.get(i);
        }

        public void recycle() {
            head1.recycle();
            head2.recycle();
            proto.recycle();
            query.recycle();
            headerCount = 0;
            for (int i = 0; i < headerCount; i++) {
                headerNames.get(i).recycle();
                headerValues.get(i).recycle();
            }
        }
    }
    
    private HttpMessageBytes msgBytes = new HttpMessageBytes();
    
    protected HttpMessage.State state = HttpMessage.State.HEAD;
    
    protected HttpChannel httpCh; 
    
    protected MultiMap headers = new MultiMap().insensitive();

    protected CBuffer protoMB;
    
    // Cookies 
    protected boolean cookiesParsed = false;
    
    // TODO: cookies parsed when headers are added !
    protected ArrayList<ServerCookie> cookies;
    protected ArrayList<ServerCookie> cookiesCache;

    protected UrlEncoding urlDecoder = new UrlEncoding();
    protected String charEncoding;

    IOReader reader;
    BufferedIOReader bufferedReader;
    HttpWriter writer;
    IOWriter conv;
    
    IOOutputStream out;
    private IOInputStream in; 
        
    boolean commited;
    
    protected IOBuffer body;

    long contentLength = -2;
    boolean chunked;
    
    BBuffer clBuffer = BBuffer.allocate(64);
    
    public HttpMessage(HttpChannel httpCh) {
        this.httpCh = httpCh;
        
        out = new IOOutputStream(httpCh.getOut(), this);
        conv = new IOWriter(httpCh);
        writer = new HttpWriter(this, out, conv);

        in = new IOInputStream(httpCh, httpCh.getIOTimeout());
        
        reader = new IOReader(httpCh.getIn());
        bufferedReader = new BufferedIOReader(reader);
        
        cookies = new ArrayList<ServerCookie>();    
        cookiesCache = new ArrayList<ServerCookie>();    
        protoMB = CBuffer.newInstance();        
    }
    
    public void addHeader(String name, String value) {
        getMimeHeaders().addValue(name).set(value);
    }

    public void setHeader(String name, String value) {
        getMimeHeaders().setValue(name).set(value);
    }

    public void setMimeHeaders(MultiMap resHeaders) {
        this.headers = resHeaders;
    }

    public String getHeader(String name) {
        CBuffer cb = headers.getHeader(name);
        return (cb == null) ? null : cb.toString();
    }

    public MultiMap getMimeHeaders() {
        return headers;
    }
    
    public Collection<String> getHeaderNames() {

        MultiMap headers = getMimeHeaders();
        int n = headers.size();
        ArrayList<String> result = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            result.add(headers.getName(i).toString());
        }
        return result;
    }
    
    public boolean containsHeader(String name) {
        return headers.getHeader(name) != null;
    }

    public void setContentLength(long len) {
        contentLength = len;
        clBuffer.setLong(len);
        setCLHeader();
    }
    
    public void setContentLength(int len) {
        contentLength = len;
        clBuffer.setLong(len);
        setCLHeader();
    } 
    
    private void setCLHeader() {
        MultiMap.Entry clB = headers.setEntry("content-length");
        clB.valueB = clBuffer; 
    }

    public long getContentLengthLong() {
        if (contentLength == -2) {
            CBuffer clB = headers.getHeader("content-length");
            contentLength = (clB == null) ? 
                    -1 : clB.getLong();
        }
        return contentLength;
    }
    
    public int getContentLength() {
        long length = getContentLengthLong();

        if (length < Integer.MAX_VALUE) {
            return (int) length;
        }
        return -1;
    }
    
    public String getContentType() {
        CBuffer contentTypeMB = headers.getHeader("content-type");
        if (contentTypeMB == null) {
            return null;
        }
        return contentTypeMB.toString();
    }
    
    public void setContentType(String contentType) {
        CBuffer clB = getMimeHeaders().getHeader("content-type");
        if (clB == null) {
            setHeader("Content-Type", contentType);
        } else {
            clB.set(contentType);
        }
    }

    /**
     * Get the character encoding used for this request.
     * Need a field because it can be overriden. Used to construct the 
     * Reader.
     */
    public String getCharacterEncoding() {
        if (charEncoding != null)
            return charEncoding;

        charEncoding = ContentType.getCharsetFromContentType(getContentType());
        return charEncoding;
    }
    
    private static final String DEFAULT_ENCODING = "ISO-8859-1";
    
    public String getEncoding() {
        String charEncoding = getCharacterEncoding();
        if (charEncoding == null) {
            return DEFAULT_ENCODING; 
        } else {
            return charEncoding;
        }
    }

    public void setCharacterEncoding(String enc) 
            throws UnsupportedEncodingException {
        this.charEncoding = enc;
    }
    
    

    public void recycle() {
        commited = false;
        headers.recycle();
        protoMB.set("HTTP/1.1");
        for (int i = 0; i < cookies.size(); i++) {
            cookies.get(i).recycle();
        }
        cookies.clear();
        charEncoding = null;
        bufferedReader.recycle();
        
        writer.recycle();
        conv.recycle();
        
        contentLength = -2;
        chunked = false;
        clBuffer.recycle();
        state = State.HEAD;
        cookiesParsed = false;
        getMsgBytes().recycle();
        
    }
    
    
    public String getProtocol() {
        return protoMB.toString();
    }
    
    public void setProtocol(String proto) {
        protoMB.set(proto);
    }
    
    public CBuffer protocol() {
        return protoMB;
    }
    
    public ServerCookie getCookie(String name) {
        for (ServerCookie sc: getServerCookies()) {
            if (sc.getName().equalsIgnoreCase(name)) {
                return sc;
            }
        }
        return null;
    }
    
    public List<ServerCookie> getServerCookies() {
        if (!cookiesParsed) {
            cookiesParsed = true;
            ServerCookie.processCookies(cookies, cookiesCache, getMsgBytes());
        }
        return cookies;
    }
    
    public UrlEncoding getURLDecoder() {
        return urlDecoder;
    }
    
    public boolean isCommitted() {
        return commited;
    }

    public void setCommitted(boolean b) {
        commited = b;
    }

    // Not used in coyote connector ( hack )
    
    public void sendHead() throws IOException {
    }
    
    public HttpChannel getHttpChannel() {
        return httpCh;
    }
    
    public IOBuffer getBody() {
        return body;
    }
    
    void setBody(IOBuffer body) {
        this.body = body;
    }
    
    public void flush() throws IOException {
        httpCh.startSending();
    }
    
    // not servlet input stream 
    public IOInputStream getBodyInputStream() {
        return in;
    }
    
    public IOOutputStream getBodyOutputStream() {
        return out;
    }

    public IOReader getBodyReader() throws IOException {
        reader.setEncoding(getCharacterEncoding());
        return reader;
    }
    
    /** 
     * Returns a buffered reader. 
     */
    public BufferedReader getReader() throws IOException {
        reader.setEncoding(getCharacterEncoding());
        return bufferedReader;
    }
    
    public HttpWriter getBodyWriter() {
        conv.setEncoding(getCharacterEncoding());
        return writer;
    }
    
    //
    public abstract void serialize(IOBuffer out) throws IOException;
    
    
    public void serializeHeaders(IOBuffer rawSendBuffers2) throws IOException {
        MultiMap mimeHeaders = getMimeHeaders();
        
        for (int i = 0; i < mimeHeaders.size(); i++) {
            CBuffer name = mimeHeaders.getName(i);
            CBuffer value = mimeHeaders.getValue(i);
            if (name.length() == 0 || value.length() == 0) {
                continue;
            }
            rawSendBuffers2.append(name);
            rawSendBuffers2.append(HttpChannel.COLON);
            rawSendBuffers2.append(value);
            rawSendBuffers2.append(BBuffer.CRLF_BYTES);
        }
        rawSendBuffers2.append(BBuffer.CRLF_BYTES);
    }
    
    protected void processMimeHeaders() {
        for (int idx = 0; idx < getMsgBytes().headerCount; idx++) {
            BBuffer nameBuf = getMsgBytes().getHeaderName(idx);
            BBuffer valBuf = getMsgBytes().getHeaderValue(idx);
            
            MultiMap.Entry header = headers.addEntry(nameBuf);
            header.valueB = valBuf;
        }
    }

    
    protected abstract void processReceivedHeaders() throws IOException;
    
    public abstract boolean hasBody();

    public HttpMessageBytes getMsgBytes() {
        // TODO: serialize if not set
        return msgBytes;
    }
    
}
