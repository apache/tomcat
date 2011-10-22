/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.MultiMap.Entry;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.Hex;
import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOReader;
import org.apache.tomcat.lite.io.IOWriter;
import org.apache.tomcat.lite.io.UrlEncoding;

public class HttpRequest extends HttpMessage {
    public static final String DEFAULT_CHARACTER_ENCODING="ISO-8859-1";

    protected CBuffer schemeMB;
    protected CBuffer methodMB;
    protected CBuffer remoteAddrMB;
    protected CBuffer remoteHostMB;
    protected int remotePort;

    protected CBuffer localNameMB;
    protected CBuffer localAddrMB;
    protected int localPort = -1;

    // Host: header, or default:80
    protected CBuffer serverNameMB;
    protected int serverPort = -1;


    // ==== Derived fields, computed after request is received ===

    protected CBuffer requestURI;
    protected CBuffer queryMB;

    protected BBuffer decodedUri = BBuffer.allocate();
    protected CBuffer decodedUriMB;

    // Decoded query
    protected MultiMap parameters;

    boolean parametersParsed = false;

    protected IOWriter charEncoder = new IOWriter(null);
    protected IOReader charDecoder = new IOReader(null);
    protected UrlEncoding urlEncoding = new UrlEncoding();

    // Reference to 'real' request object
    // will not be recycled
    public Object nativeRequest;
    public Object wrapperRequest;

    boolean ssl = false;

    boolean async = false;

    CBuffer requestURL = CBuffer.newInstance();

    private Map<String, Object> attributes = new HashMap<String, Object>();

    /**
     * Mapping data.
     */
    protected MappingData mappingData = new MappingData();


    HttpRequest(HttpChannel httpCh) {
        super(httpCh);
        decodedUriMB = CBuffer.newInstance();
        requestURI = CBuffer.newInstance();
        queryMB = CBuffer.newInstance();
        serverNameMB = CBuffer.newInstance();

        parameters = new MultiMap();

        schemeMB =
            CBuffer.newInstance();
        methodMB = CBuffer.newInstance();
        initRemote();
    }

    protected void initRemote() {
        remoteAddrMB = CBuffer.newInstance();
        localNameMB = CBuffer.newInstance();
        remoteHostMB = CBuffer.newInstance();
        localAddrMB = CBuffer.newInstance();
    }

    public void recycle() {
        super.recycle();
        schemeMB.recycle();
        methodMB.set("GET");
        requestURI.recycle();
        requestURL.recycle();
        queryMB.recycle();
        decodedUriMB.recycle();

        parameters.recycle();
        remoteAddrMB.recycle();
        remoteHostMB.recycle();
        parametersParsed = false;
        ssl = false;
        async = false;
        asyncTimeout = -1;
        charEncoder.recycle();

        localPort = -1;
        remotePort = -1;
        localAddrMB.recycle();
        localNameMB.recycle();

        serverPort = -1;
        serverNameMB.recycle();
        decodedUri.recycle();
        decodedQuery.recycle();
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public void setAttribute(String name, Object o) {
        if (o == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, o);
        }
    }
    // getAttributeNames not supported

    public Map<String, Object> attributes() {
        return attributes;
    }


    public CBuffer method() {
        return methodMB;
    }

    public String getMethod() {
        return methodMB.toString();
    }

    public void setMethod(String method) {
        methodMB.set(method);
    }

    public CBuffer scheme() {
        return schemeMB;
    }

    public String getScheme() {
        String scheme = schemeMB.toString();
        if (scheme == null) {
            return "http";
        }
        return scheme;
    }

    public void setScheme(String s) {
        schemeMB.set(s);
    }

    public MappingData getMappingData() {
        return (mappingData);
    }

    /**
     * Return the portion of the request URI used to select the Context
     * of the Request.
     */
    public String getContextPath() {
        return (getMappingData().contextPath.toString());
    }

    public String getPathInfo() {
        CBuffer pathInfo = getMappingData().pathInfo;
        if (pathInfo.length() == 0) {
            return null;
        }
        return (getMappingData().pathInfo.toString());
    }

    /**
     * Return the portion of the request URI used to select the servlet
     * that will process this request.
     */
    public String getServletPath() {
        return (getMappingData().wrapperPath.toString());
    }

    /**
     * Parse query parameters - but not POST body.
     *
     * If you don't call this method, getParameters() will
     * also read the body for POST with x-www-url-encoded
     * mime type.
     */
    public void parseQueryParameters() {
        parseQuery();
    }

    /**
     * Explicitely parse the body, adding the parameters to
     * those from the query ( if already parsed ).
     *
     * By default servlet mode ( both query and body ) is used.
     */
    public void parsePostParameters() {
        parseBody();
    }

    MultiMap getParameters() {
        if (!parametersParsed) {
            parseQuery();
            parseBody();
        }
        return parameters;
    }

    public Enumeration<String> getParameterNames() {
        return getParameters().names();
    }

    /**
     * Expensive, creates a copy on each call.
     * @param name
     * @return
     */
    public String[] getParameterValues(String name) {
        Entry entry = getParameters().getEntry(name);
        if (entry == null) {
            return null;
        }
        String[] values = new String[entry.values.size()];
        for (int j = 0; j < values.length; j++) {
            values[j] = entry.values.get(j).toString();
        }
        return values;
    }

    // Inefficient - we convert from a different representation.
    public Map<String, String[]> getParameterMap() {
        // we could allow 'locking' - I don't think this is
        // a very useful optimization
        Map<String, String[]> map = new HashMap();
        for (int i = 0; i < getParameters().size(); i++) {
            Entry entry = getParameters().getEntry(i);
            if (entry == null) {
                continue;
            }
            if (entry.key == null) {
                continue;
            }
            String name = entry.key.toString();
            String[] values = new String[entry.values.size()];
            for (int j = 0; j < values.length; j++) {
                values[j] = entry.values.get(j).toString();
            }
            map.put(name, values);
        }
        return map;
    }

    public String getParameter(String name) {
        CharSequence value = getParameters().get(name);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    public void setParameter(String name, String value) {
        getParameters().set(name, value);
    }

    public void addParameter(String name, String values) {
        getParameters().add(name, values);
    }

    public CBuffer queryString() {
        return queryMB;
    }

    // TODO
    void serializeParameters(Appendable cc) throws IOException {
        int keys = parameters.size();
        boolean notFirst = false;
        for (int i = 0; i < parameters.size(); i++) {
            Entry entry = parameters.getEntry(i);
            for (int j = 0; j < entry.values.size(); j++) {
                // TODO: Uencode
                if (notFirst) {
                    cc.append('&');
                } else {
                    notFirst = true;
                }
                cc.append(entry.key);
                cc.append("=");
                cc.append(entry.values.get(j).getValue());
            }
        }
    }

    public void setURI(CharSequence encoded) {
        decodedUriMB.recycle();
        decodedUriMB.append(encoded);
        // TODO: generate % encoding ( reverse of decodeRequest )
    }

    public CBuffer decodedURI() {
        return decodedUriMB;
    }

    public CBuffer requestURI() {
        return requestURI;
    }

    public CBuffer requestURL() {
        CBuffer url = requestURL;
        url.recycle();

        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0)
            port = 80; // Work around java.net.URL bug

        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        // Decoded !!
        url.append(getRequestURI());

        return (url);

    }

    /**
     * Not decoded - %xx as in original.
     * @return
     */
    public String getRequestURI() {
        return requestURI.toString();
    }

    public void setRequestURI(String encodedUri) {
        requestURI.set(encodedUri);
    }

    CBuffer getOrAdd(String name) {
        CBuffer header = getMimeHeaders().getHeader(name);
        if (header == null) {
            header = getMimeHeaders().addValue(name);
        }
        return header;
    }

    /**
     * Set the Host header of the request.
     * @param target
     */
    public void setHost(String target) {
        serverNameMB.recycle();
        getOrAdd("Host").set(target);
    }

    // XXX
    public CBuffer serverName() {
        if (serverNameMB.length() == 0) {
            parseHost();
        }
        return serverNameMB;
    }

    public String getServerName() {
        return serverName().toString();
    }

    public void setServerName(String name)  {
        serverName().set(name);
    }

    public int getServerPort() {
        serverName();
        return serverPort;
    }

    public void setServerPort(int serverPort ) {
        this.serverPort=serverPort;
    }

    public CBuffer remoteAddr() {
        if (remoteAddrMB.length() == 0) {
            HttpChannel asyncHttp = getHttpChannel();
            IOChannel iochannel = asyncHttp.getNet().getFirst();
            remoteAddrMB.set((String)
                    iochannel.getAttribute(IOChannel.ATT_REMOTE_ADDRESS));
        }
        return remoteAddrMB;
    }

    public CBuffer remoteHost() {
        if (remoteHostMB.length() == 0) {
            HttpChannel asyncHttp = getHttpChannel();
            IOChannel iochannel = asyncHttp.getNet().getFirst();
            remoteHostMB.set((String)
                    iochannel.getAttribute(IOChannel.ATT_REMOTE_HOSTNAME));
        }
        return remoteHostMB;
    }

    public CBuffer localName() {
        return localNameMB;
    }

    public CBuffer localAddr() {
        return localAddrMB;
    }

    public int getRemotePort(){
        if (remotePort == -1) {
            HttpChannel asyncHttp = getHttpChannel();
            IOChannel iochannel = asyncHttp.getNet().getFirst();
            remotePort = (Integer) iochannel.getAttribute(IOChannel.ATT_REMOTE_PORT);
        }
        return remotePort;
    }

    public void setRemotePort(int port){
        this.remotePort = port;
    }

    public int getLocalPort(){
        if (localPort == -1) {
            HttpChannel asyncHttp = getHttpChannel();
            IOChannel iochannel = asyncHttp.getNet().getFirst();
            localPort = (Integer) iochannel.getAttribute(IOChannel.ATT_LOCAL_PORT);
        }
        return localPort;
    }

    public void setLocalPort(int port){
        this.localPort = port;
    }

    public HttpResponse waitResponse() throws IOException {
        return waitResponse(httpCh.ioTimeout);
    }

    public void send(HttpService headersCallback, long timeout) throws IOException {
        if (headersCallback != null) {
            httpCh.setHttpService(headersCallback);
        }

        httpCh.send();
    }

    public void send(HttpService headersCallback) throws IOException {
        send(headersCallback, httpCh.ioTimeout);
    }

    public void send() throws IOException {
        send(null, httpCh.ioTimeout);
    }

    public HttpResponse waitResponse(long timeout) throws IOException {
        // TODO: close out if post
        httpCh.send();

        httpCh.headersReceivedLock.waitSignal(timeout);

        return httpCh.getResponse();
    }

    /**
     * Parse host.
     * @param serverNameMB2
     * @throws IOException
     */
    boolean parseHost()  {
        MultiMap.Entry hostHF = getMimeHeaders().getEntry("Host");
        if (hostHF == null) {
            // HTTP/1.0
            // Default is what the socket tells us. Overriden if a host is
            // found/parsed
            return true;
        }

        BBuffer valueBC = hostHF.valueB;
        if (valueBC == null) {
            valueBC = BBuffer.allocate();
            hostHF.getValue().toAscii(valueBC);
        }
        byte[] valueB = valueBC.array();
        int valueL = valueBC.getLength();
        int valueS = valueBC.getStart();

        int colonPos = valueBC.indexOf(':', 0);

        serverNameMB.recycle();

        boolean ipv6 = (valueB[valueS] == '[');
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            char b = (char) valueB[i + valueS];
            if (b == ':') {
                if (!ipv6 || bracketClosed) {
                    colonPos = i;
                    break;
                }
            }
            serverNameMB.append(b);
            if (b == ']') {
                bracketClosed = true;
            }
        }

        if (colonPos < 0) {
            if (!ssl) {
                setServerPort(80);
            } else {
                setServerPort(443);
            }
        } else {
            int port = 0;
            int mult = 1;
            for (int i = valueL - 1; i > colonPos; i--) {
                int charValue = Hex.DEC[(int) valueB[i + valueS]];
                if (charValue == -1) {
                    // we don't return 400 - could do it
                    return false;
                }
                port = port + (charValue * mult);
                mult = 10 * mult;
            }
            setServerPort(port);

        }
        return true;
    }

    // TODO: this is from coyote - MUST be rewritten !!!
    // - cleaner
    // - chunked encoding for body
    // - buffer should be in a pool, etc.
    /**
     * Post data buffer.
     */
    public final static int CACHED_POST_LEN = 8192;

    public  byte[] postData = null;

    private long asyncTimeout = -1;

    /**
     * Parse request parameters.
     */
    protected void parseQuery() {

        parametersParsed = true;

        // getCharacterEncoding() may have been overridden to search for
        // hidden form field containing request encoding
        String enc = getEncoding();

//        boolean useBodyEncodingForURI = connector.getUseBodyEncodingForURI();
//        if (enc != null) {
//            parameters.setEncoding(enc);
////            if (useBodyEncodingForURI) {
////                parameters.setQueryStringEncoding(enc);
////            }
//        } else {
//            parameters.setEncoding(DEFAULT_CHARACTER_ENCODING);
////            if (useBodyEncodingForURI) {
////                parameters.setQueryStringEncoding
////                    (DEFAULT_CHARACTER_ENCODING);
////            }
//        }

        handleQueryParameters();
    }

    // Copy - will be modified by decoding
    BBuffer decodedQuery = BBuffer.allocate(1024);

    CBuffer tmpNameC = CBuffer.newInstance();
    BBuffer tmpName = BBuffer.wrapper();
    BBuffer tmpValue = BBuffer.wrapper();

    CBuffer tmpNameCB = CBuffer.newInstance();
    CBuffer tmpValueCB = CBuffer.newInstance();

    /**
     * Process the query string into parameters
     */
    public void handleQueryParameters() {
        if( queryMB.length() == 0) {
            return;
        }

        decodedQuery.recycle();
        decodedQuery.append(getMsgBytes().query());
        // TODO: option 'useBodyEncodingForUri' - versus UTF or ASCII
        String queryStringEncoding = getEncoding();
        processParameters( decodedQuery, queryStringEncoding );
    }

    public void processParameters( BBuffer bc, String encoding ) {
        if( bc.isNull())
            return;
        if (bc.remaining() ==0) {
            return;
        }
        processParameters( bc.array(), bc.getOffset(),
                           bc.getLength(), encoding);
    }

    public void processParameters( byte bytes[], int start, int len,
            String enc ) {
        int end=start+len;
        int pos=start;

        do {
            boolean noEq=false;
            int valStart=-1;
            int valEnd=-1;

            int nameStart=pos;
            int nameEnd=BBuffer.indexOf(bytes, nameStart, end, '=' );
            // Workaround for a&b&c encoding
            int nameEnd2=BBuffer.indexOf(bytes, nameStart, end, '&' );
            if( (nameEnd2!=-1 ) &&
                    ( nameEnd==-1 || nameEnd > nameEnd2) ) {
                nameEnd=nameEnd2;
                noEq=true;
                valStart=nameEnd;
                valEnd=nameEnd;
            }
            if( nameEnd== -1 )
                nameEnd=end;

            if( ! noEq ) {
                valStart= (nameEnd < end) ? nameEnd+1 : end;
                valEnd=BBuffer.indexOf(bytes, valStart, end, '&');
                if( valEnd== -1 ) valEnd = (valStart < end) ? end : valStart;
            }

            pos=valEnd+1;

            if( nameEnd<=nameStart ) {
                // No name eg ...&=xx&... will trigger this
                continue;
            }

            // TODO: use CBuffer, recycle
            tmpName.setBytes( bytes, nameStart, nameEnd-nameStart );
            tmpValue.setBytes( bytes, valStart, valEnd-valStart );

            try {
                parameters.add(urlDecode(tmpName, enc),
                        urlDecode(tmpValue, enc));
            } catch (IOException e) {
                // ignored
            }
        } while( pos<end );
    }

//    public void processParameters(char bytes[], int start, int len,
//            String enc ) {
//        int end=start+len;
//        int pos=start;
//
//        do {
//            boolean noEq=false;
//            int valStart=-1;
//            int valEnd=-1;
//
//            int nameStart=pos;
//            int nameEnd=CBuffer.indexOf(bytes, nameStart, end, '=' );
//            // Workaround for a&b&c encoding
//            int nameEnd2=CBuffer.indexOf(bytes, nameStart, end, '&' );
//            if( (nameEnd2!=-1 ) &&
//                    ( nameEnd==-1 || nameEnd > nameEnd2) ) {
//                nameEnd=nameEnd2;
//                noEq=true;
//                valStart=nameEnd;
//                valEnd=nameEnd;
//            }
//            if( nameEnd== -1 )
//                nameEnd=end;
//
//            if( ! noEq ) {
//                valStart= (nameEnd < end) ? nameEnd+1 : end;
//                valEnd=CBuffer.indexOf(bytes, valStart, end, '&');
//                if( valEnd== -1 ) valEnd = (valStart < end) ? end : valStart;
//            }
//
//            pos=valEnd+1;
//
//            if( nameEnd<=nameStart ) {
//                // No name eg ...&=xx&... will trigger this
//                continue;
//            }
//
//            // TODO: use CBuffer, recycle
//            tmpNameCB.recycle();
//            tmpValueCB.recycle();
//
//            tmpNameCB.wrap( bytes, nameStart, nameEnd );
//            tmpValueCB.wrap( bytes, valStart, valEnd );
//
//            //CharChunk name = new CharChunk();
//            //CharChunk value = new CharChunk();
//            // TODO:
//            try {
//                parameters.add(urlDecode(tmpName, enc),
//                        urlDecode(tmpValue, enc));
//            } catch (IOException e) {
//                // ignored
//            }
//        } while( pos<end );
//    }

    private String urlDecode(BBuffer bc, String enc)
            throws IOException {
        // Replace %xx
        urlDecoder.urlDecode(bc, true);

        String result = null;
        if (enc != null) {
            result = bc.toString(enc);
        } else {
            // Ascii

            CBuffer cc = tmpNameC;
            cc.recycle();
            int length = bc.getLength();
            byte[] bbuf = bc.array();
            int start = bc.getStart();
            cc.appendAscii(bbuf, start, length);
            result = cc.toString();
            cc.recycle();
        }
        return result;
    }

    private void processParameters( byte bytes[], int start, int len ) {
        processParameters(bytes, start, len, getEncoding());
    }

    protected void parseBody() {

        parametersParsed = true;
        String enc = getCharacterEncoding();

//      if (usingInputStream || usingReader)
//      return;
        if (!getMethod().equalsIgnoreCase("POST"))
            return;

        String contentType = getContentType();
        if (contentType == null)
            contentType = "";
        int semicolon = contentType.indexOf(';');
        if (semicolon >= 0) {
            contentType = contentType.substring(0, semicolon).trim();
        } else {
            contentType = contentType.trim();
        }
        if (!("application/x-www-form-urlencoded".equals(contentType)))
            return;

        int len = getContentLength();

        if (len > 0) {
            try {
                byte[] formData = null;
                if (len < CACHED_POST_LEN) {
                    if (postData == null)
                        postData = new byte[CACHED_POST_LEN];
                    formData = postData;
                } else {
                    formData = new byte[len];
                }
                int actualLen = readPostBody(formData, len);
                if (actualLen == len) {
                    processParameters(formData, 0, len);
                }
            } catch (Throwable t) {
                ; // Ignore
            }
        }

    }

    /**
     * Read post body in an array.
     */
    protected int readPostBody(byte body[], int len)
        throws IOException {

        int offset = 0;
        do {
            int inputLen = getBodyInputStream().read(body, offset, len - offset);
            if (inputLen <= 0) {
                return offset;
            }
            offset += inputLen;
        } while ((len - offset) > 0);
        return len;

    }

    // Async support - a subset of servlet spec, the fancy stuff is in the
    // facade.

    public boolean isAsyncStarted() {
        return async;
    }

    public void async() {
        this.async = true;
    }

    public void setAsyncTimeout(long timeout) {
        this.asyncTimeout  = timeout;
    }

    /**
     * Server mode, request just received.
     */
    protected void processReceivedHeaders() throws IOException {
        BBuffer url = getMsgBytes().url();
        if (url.remaining() == 0) {
            System.err.println("No input");
        }
        if (url.get(0) == 'h') {
            int firstSlash = url.indexOf('/', 0);
            schemeMB.appendAscii(url.array(),
                    url.getStart(), firstSlash + 2);
            if (!schemeMB.equals("http://") &&
                    !schemeMB.equals("https://")) {
                httpCh.getResponse().setStatus(400);
                httpCh.abort("Error normalizing url " +
                        getMsgBytes().url());
                return;
            }

            int urlStart = url.indexOf('/', firstSlash + 2);
            serverNameMB.recycle();
            serverNameMB.appendAscii(url.array(),
                    url.getStart() + firstSlash + 2, urlStart - firstSlash - 2);

            url.position(url.getStart() + urlStart);
        }
        if (!httpCh.normalize(getMsgBytes().url())) {
            httpCh.getResponse().setStatus(400);
            httpCh.abort("Error normalizing url " +
                    getMsgBytes().url());
            return;
        }

        method().set(getMsgBytes().method());
        requestURI().set(getMsgBytes().url());
        queryString().set(getMsgBytes().query());
        protocol().set(getMsgBytes().protocol());

        processMimeHeaders();

        // URL decode and normalize
        decodedUri.append(getMsgBytes().url());

        getURLDecoder().urlDecode(decodedUri, false);

        // Need to normalize again - %decoding may decode /
        if (!httpCh.normalize(decodedUri)) {
            httpCh.getResponse().setStatus(400);
            httpCh.abort("Invalid decoded uri " + decodedUri);
            return;
        }
        decodedURI().set(decodedUri);

        // default response protocol
        httpCh.getResponse().protocol().set(getMsgBytes().protocol());
    }


    public boolean hasBody() {
        return chunked || contentLength >= 0;
    }

    /**
     * Convert (if necessary) and return the absolute URL that represents the
     * resource referenced by this possibly relative URL.  If this URL is
     * already absolute, return it unchanged.
     *
     * @param location URL to be (possibly) converted and then returned
     *
     * @exception IllegalArgumentException if a MalformedURLException is
     *  thrown when converting the relative URL to an absolute one
     */
    public void toAbsolute(String location, CBuffer cb) {

        cb.recycle();
        if (location == null)
            return;

        boolean leadingSlash = location.startsWith("/");
        if (leadingSlash || !hasScheme(location)) {

            String scheme = getScheme();
            String name = serverName().toString();
            int port = getServerPort();

            cb.append(scheme);
            cb.append("://", 0, 3);
            cb.append(name);
            if ((scheme.equals("http") && port != 80)
                    || (scheme.equals("https") && port != 443)) {
                cb.append(':');
                String portS = port + "";
                cb.append(portS);
            }
            if (!leadingSlash) {
                String relativePath = decodedURI().toString();
                int pos = relativePath.lastIndexOf('/');
                relativePath = relativePath.substring(0, pos);

                //String encodedURI = null;
                urlEncoding.urlEncode(relativePath,  cb, charEncoder);
                //encodedURI = urlEncoder.encodeURL(relativePath);
                //redirectURLCC.append(encodedURI, 0, encodedURI.length());
                cb.append('/');
            }

            cb.append(location);
        } else {
            cb.append(location);
        }

    }

    /**
     * Determine if a URI string has a <code>scheme</code> component.
     */
    public static boolean hasScheme(String uri) {
        int len = uri.length();
        for(int i=0; i < len ; i++) {
            char c = uri.charAt(i);
            if(c == ':') {
                return i > 0;
            } else if(!isSchemeChar(c)) {
                return false;
            }
        }
        return false;
    }

    /**
     * Determine if the character is allowed in the scheme of a URI.
     * See RFC 2396, Section 3.1
     */
    private static boolean isSchemeChar(char c) {
        return Character.isLetterOrDigit(c) ||
            c == '+' || c == '-' || c == '.';
    }

    public IOWriter getCharEncoder() {
        return charEncoder;
    }

    public IOReader getCharDecoder() {
        return charDecoder;
    }

    public UrlEncoding getUrlEncoding() {
        return urlEncoding;
    }

    public BBuffer toBytes(CBuffer cb, BBuffer bb) {
        if (bb == null) {
            bb = BBuffer.allocate(cb.length());
        }
        getCharEncoder().encodeAll(cb, bb, "UTF-8");
        return bb;
    }

    public String toString() {
        IOBuffer out = new IOBuffer();
        try {
            Http11Connection.serialize(this, out);
            return out.readAll(null).toString();
        } catch (IOException e) {
            return "Invalid request";
        }
    }

    public boolean isSecure() {
        return ssl;
    }

    public HttpRequest setSecure(boolean ssl) {
        this.ssl = ssl;
        return this;
    }
}
