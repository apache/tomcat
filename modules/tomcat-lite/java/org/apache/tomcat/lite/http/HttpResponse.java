/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.util.HashMap;

import org.apache.tomcat.lite.io.BBucket;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.IOBuffer;

public class HttpResponse extends HttpMessage {

    // will not be recycled
    public Object nativeResponse;

    protected CBuffer message = CBuffer.newInstance();
    
    int status = -1;

    HttpResponse(HttpChannel httpCh) {
        super(httpCh);
    }

    public void recycle() {
        super.recycle();
        message.recycle();
        status = -1;
    }
    
    public void setMessage(String s) {
        message.set(filter(s));
    }
    
    public String getMessage() {
        return message.toString();
    }
    
    public CBuffer getMessageBuffer() {
        return message;
    }
    
    byte[] S_200 = new byte[] { '2', '0', '0' };
    
    public void setStatus(int i) {
        status = i;
    }
    
    public int getStatus() {
        if (status >= 0) {
            return status;
        }
        if (getMsgBytes().status().isNull()) {
            status = 200;
        } else {
            try {
                status = getMsgBytes().status().getInt();
            } catch(NumberFormatException ex) {
                status = 500;
                httpCh.log.severe("Invalid status " + getMsgBytes().status());
            }
        }
        return status;
    }

    public HttpRequest getRequest() {
        return getHttpChannel().getRequest();
    }
    
    // Http client mode.
    protected void processReceivedHeaders() throws IOException {
        protocol().set(getMsgBytes().protocol());                
        message.set(getMsgBytes().message());
        processMimeHeaders();
        // TODO: if protocol == 1.0 and we requested 1.1, downgrade getHttpChannel().pro
        try {
            status = getStatus();
        } catch (Throwable t) {
            getHttpChannel().log.warning("Invalid status " + getMsgBytes().status() + " " + getMessage());
        }
    }

    /**
     * All responses to the HEAD request method MUST NOT include a 
     * message-body, even though the presence of entity- header fields might
     *  lead one to believe they do. All 1xx (informational), 204 (no content)
     *  , and 304 (not modified) responses MUST NOT include a message-body. All 
     *  other responses do include a message-body, although it MAY be of zero 
     *  length.
     */
    public boolean hasBody() {
        if (httpCh.getRequest().method().equals("HEAD")) {
            return false;
        }
        if (status >= 100 && status < 200) {
            return false;
        }
        // what about (status == 205) ?
        if ((status == 204) 
                || (status == 304)) {
            return false;
        }
        return true;
    }
    
    /** Get the status string associated with a status code.
     *  No I18N - return the messages defined in the HTTP spec.
     *  ( the user isn't supposed to see them, this is the last
     *  thing to translate)
     *
     *  Common messages are cached.
     *
     */
    BBucket getMessage( int status ) {
        // method from Response.

        // Does HTTP requires/allow international messages or
        // are pre-defined? The user doesn't see them most of the time
        switch( status ) {
        case 200:
            return st_200;
        case 302:
            return st_302;
        case 400:
            return st_400;
        case 404:
            return st_404;
        }
        BBucket bb = stats.get(status);
        if (bb == null) {
            return st_unknown;
        }
        return bb;
    }
    
    
    static BBucket st_unknown = BBuffer.wrapper("No Message");
    static BBucket st_200 = BBuffer.wrapper("OK");
    static BBucket st_302= BBuffer.wrapper("Moved Temporarily");
    static BBucket st_400= BBuffer.wrapper("Bad Request");
    static BBucket st_404= BBuffer.wrapper("Not Found");

    static HashMap<Integer,BBucket> stats = new HashMap<Integer, BBucket>();
    private static void addStatus(int stat, String msg) {
        stats.put(stat, BBuffer.wrapper(msg));
    }
    
    static {
        addStatus(100, "Continue");
        addStatus(101, "Switching Protocols");
        addStatus(200, "OK");
        addStatus(201, "Created");
        addStatus(202, "Accepted");
        addStatus(203, "Non-Authoritative Information");
        addStatus(204, "No Content");
        addStatus(205, "Reset Content");
        addStatus(206, "Partial Content");
        addStatus(207, "Multi-Status");
        addStatus(300, "Multiple Choices");
        addStatus(301, "Moved Permanently");
        addStatus(302, "Moved Temporarily");
        addStatus(303, "See Other");
        addStatus(304, "Not Modified");
        addStatus(305, "Use Proxy");
        addStatus(307, "Temporary Redirect");
        addStatus(400, "Bad Request");
        addStatus(401, "Unauthorized");
        addStatus(402, "Payment Required");
        addStatus(403, "Forbidden");
        addStatus(404, "Not Found");
        addStatus(405, "Method Not Allowed");
        addStatus(406, "Not Acceptable");
        addStatus(407, "Proxy Authentication Required");
        addStatus(408, "Request Timeout");
        addStatus(409, "Conflict");
        addStatus(410, "Gone");
        addStatus(411, "Length Required");
        addStatus(412, "Precondition Failed");
        addStatus(413, "Request Entity Too Large");
        addStatus(414, "Request-URI Too Long");
        addStatus(415, "Unsupported Media Type");
        addStatus(416, "Requested Range Not Satisfiable");
        addStatus(417, "Expectation Failed");
        addStatus(422, "Unprocessable Entity");
        addStatus(423, "Locked");
        addStatus(424, "Failed Dependency");
        addStatus(500, "Internal Server Error");
        addStatus(501, "Not Implemented");
        addStatus(502, "Bad Gateway");
        addStatus(503, "Service Unavailable");
        addStatus(504, "Gateway Timeout");
        addStatus(505, "HTTP Version Not Supported");
        addStatus(507, "Insufficient Storage");
        
    }
    
    /**
     * Filter the specified message string for characters that are sensitive
     * in HTML.  This avoids potential attacks caused by including JavaScript
     * codes in the request URL that is often reported in error messages.
     *
     * @param message The message string to be filtered
     */
    private static String filter(String message) {

        if (message == null)
            return (null);
        if (message.indexOf('<') < 0 &&
                message.indexOf('>') < 0 &&
                message.indexOf('&') < 0 &&
                message.indexOf('"') < 0) {
            return message;
        }
        
        char content[] = new char[message.length()];
        message.getChars(0, message.length(), content, 0);

        StringBuffer result = new StringBuffer(content.length + 50);
        for (int i = 0; i < content.length; i++) {
            switch (content[i]) {
            case '<':
                result.append("&lt;");
                break;
            case '>':
                result.append("&gt;");
                break;
            case '&':
                result.append("&amp;");
                break;
            case '"':
                result.append("&quot;");
                break;
            default:
                result.append(content[i]);
            }
        }
        return (result.toString());
    }
    
}
