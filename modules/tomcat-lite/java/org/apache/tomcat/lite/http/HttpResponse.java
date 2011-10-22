/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.util.HashMap;

import org.apache.tomcat.lite.io.BBucket;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.CBuffer;

public class HttpResponse extends HttpMessage {

    /*
     * Server status codes; see RFC 2068.
     */

    /**
     * Status code (100) indicating the client can continue.
     */

    public static final int SC_CONTINUE = 100;


    /**
     * Status code (101) indicating the server is switching protocols
     * according to Upgrade header.
     */

    public static final int SC_SWITCHING_PROTOCOLS = 101;

    /**
     * Status code (200) indicating the request succeeded normally.
     */

    public static final int SC_OK = 200;

    /**
     * Status code (201) indicating the request succeeded and created
     * a new resource on the server.
     */

    public static final int SC_CREATED = 201;

    /**
     * Status code (202) indicating that a request was accepted for
     * processing, but was not completed.
     */

    public static final int SC_ACCEPTED = 202;

    /**
     * Status code (203) indicating that the meta information presented
     * by the client did not originate from the server.
     */

    public static final int SC_NON_AUTHORITATIVE_INFORMATION = 203;

    /**
     * Status code (204) indicating that the request succeeded but that
     * there was no new information to return.
     */

    public static final int SC_NO_CONTENT = 204;

    /**
     * Status code (205) indicating that the agent <em>SHOULD</em> reset
     * the document view which caused the request to be sent.
     */

    public static final int SC_RESET_CONTENT = 205;

    /**
     * Status code (206) indicating that the server has fulfilled
     * the partial GET request for the resource.
     */

    public static final int SC_PARTIAL_CONTENT = 206;

    /**
     * Used by Webdav.
     */
    public static final int SC_MULTI_STATUS = 207;
    // This one collides with HTTP 1.1
    // "207 Partial Update OK"

    /**
     * Status code (300) indicating that the requested resource
     * corresponds to any one of a set of representations, each with
     * its own specific location.
     */

    public static final int SC_MULTIPLE_CHOICES = 300;

    /**
     * Status code (301) indicating that the resource has permanently
     * moved to a new location, and that future references should use a
     * new URI with their requests.
     */

    public static final int SC_MOVED_PERMANENTLY = 301;

    /**
     * Status code (302) indicating that the resource has temporarily
     * moved to another location, but that future references should
     * still use the original URI to access the resource.
     *
     * This definition is being retained for backwards compatibility.
     * SC_FOUND is now the preferred definition.
     */

    public static final int SC_MOVED_TEMPORARILY = 302;

    /**
    * Status code (302) indicating that the resource reside
    * temporarily under a different URI. Since the redirection might
    * be altered on occasion, the client should continue to use the
    * Request-URI for future requests.(HTTP/1.1) To represent the
    * status code (302), it is recommended to use this variable.
    */

    public static final int SC_FOUND = 302;

    /**
     * Status code (303) indicating that the response to the request
     * can be found under a different URI.
     */

    public static final int SC_SEE_OTHER = 303;

    /**
     * Status code (304) indicating that a conditional GET operation
     * found that the resource was available and not modified.
     */

    public static final int SC_NOT_MODIFIED = 304;

    /**
     * Status code (305) indicating that the requested resource
     * <em>MUST</em> be accessed through the proxy given by the
     * <code><em>Location</em></code> field.
     */

    public static final int SC_USE_PROXY = 305;

     /**
     * Status code (307) indicating that the requested resource
     * resides temporarily under a different URI. The temporary URI
     * <em>SHOULD</em> be given by the <code><em>Location</em></code>
     * field in the response.
     */

     public static final int SC_TEMPORARY_REDIRECT = 307;

    /**
     * Status code (400) indicating the request sent by the client was
     * syntactically incorrect.
     */

    public static final int SC_BAD_REQUEST = 400;

    /**
     * Status code (401) indicating that the request requires HTTP
     * authentication.
     */

    public static final int SC_UNAUTHORIZED = 401;

    /**
     * Status code (402) reserved for future use.
     */

    public static final int SC_PAYMENT_REQUIRED = 402;

    /**
     * Status code (403) indicating the server understood the request
     * but refused to fulfill it.
     */

    public static final int SC_FORBIDDEN = 403;

    /**
     * Status code (404) indicating that the requested resource is not
     * available.
     */

    public static final int SC_NOT_FOUND = 404;

    /**
     * Status code (405) indicating that the method specified in the
     * <code><em>Request-Line</em></code> is not allowed for the resource
     * identified by the <code><em>Request-URI</em></code>.
     */

    public static final int SC_METHOD_NOT_ALLOWED = 405;

    /**
     * Status code (406) indicating that the resource identified by the
     * request is only capable of generating response entities which have
     * content characteristics not acceptable according to the accept
     * headers sent in the request.
     */

    public static final int SC_NOT_ACCEPTABLE = 406;

    /**
     * Status code (407) indicating that the client <em>MUST</em> first
     * authenticate itself with the proxy.
     */

    public static final int SC_PROXY_AUTHENTICATION_REQUIRED = 407;

    /**
     * Status code (408) indicating that the client did not produce a
     * request within the time that the server was prepared to wait.
     */

    public static final int SC_REQUEST_TIMEOUT = 408;

    /**
     * Status code (409) indicating that the request could not be
     * completed due to a conflict with the current state of the
     * resource.
     */

    public static final int SC_CONFLICT = 409;

    /**
     * Status code (410) indicating that the resource is no longer
     * available at the server and no forwarding address is known.
     * This condition <em>SHOULD</em> be considered permanent.
     */

    public static final int SC_GONE = 410;

    /**
     * Status code (411) indicating that the request cannot be handled
     * without a defined <code><em>Content-Length</em></code>.
     */

    public static final int SC_LENGTH_REQUIRED = 411;

    /**
     * Status code (412) indicating that the precondition given in one
     * or more of the request-header fields evaluated to false when it
     * was tested on the server.
     */

    public static final int SC_PRECONDITION_FAILED = 412;

    /**
     * Status code (413) indicating that the server is refusing to process
     * the request because the request entity is larger than the server is
     * willing or able to process.
     */

    public static final int SC_REQUEST_ENTITY_TOO_LARGE = 413;

    /**
     * Status code (414) indicating that the server is refusing to service
     * the request because the <code><em>Request-URI</em></code> is longer
     * than the server is willing to interpret.
     */

    public static final int SC_REQUEST_URI_TOO_LONG = 414;

    /**
     * Status code (415) indicating that the server is refusing to service
     * the request because the entity of the request is in a format not
     * supported by the requested resource for the requested method.
     */

    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;

    /**
     * Status code (416) indicating that the server cannot serve the
     * requested byte range.
     */

    public static final int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;

    /**
     * Status code (417) indicating that the server could not meet the
     * expectation given in the Expect request header.
     */

    public static final int SC_EXPECTATION_FAILED = 417;

    /**
     * Status code (423) indicating the destination resource of a
     * method is locked, and either the request did not contain a
     * valid Lock-Info header, or the Lock-Info header identifies
     * a lock held by another principal.
     */
    public static final int SC_LOCKED = 423;

    /**
     * Status code (500) indicating an error inside the HTTP server
     * which prevented it from fulfilling the request.
     */

    public static final int SC_INTERNAL_SERVER_ERROR = 500;

    /**
     * Status code (501) indicating the HTTP server does not support
     * the functionality needed to fulfill the request.
     */

    public static final int SC_NOT_IMPLEMENTED = 501;

    /**
     * Status code (502) indicating that the HTTP server received an
     * invalid response from a server it consulted when acting as a
     * proxy or gateway.
     */

    public static final int SC_BAD_GATEWAY = 502;

    /**
     * Status code (503) indicating that the HTTP server is
     * temporarily overloaded, and unable to handle the request.
     */

    public static final int SC_SERVICE_UNAVAILABLE = 503;

    /**
     * Status code (504) indicating that the server did not receive
     * a timely response from the upstream server while acting as
     * a gateway or proxy.
     */

    public static final int SC_GATEWAY_TIMEOUT = 504;

    /**
     * Status code (505) indicating that the server does not support
     * or refuses to support the HTTP protocol version that was used
     * in the request message.
     */

    public static final int SC_HTTP_VERSION_NOT_SUPPORTED = 505;

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

    public void sendError(int status) {
        this.status = status;
    }

    public void sendError(int status, String msg) {
        message.set(msg);
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
    static BBucket getMessage( int status ) {
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

    public static String getStatusText(int code) {
        return getMessage(code).toString();
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
        addStatus(SC_LOCKED, "Locked");


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
