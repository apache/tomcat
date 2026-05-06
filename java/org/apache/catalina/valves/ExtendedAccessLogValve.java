/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.valves;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.URLEncoder;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * An implementation of the W3c Extended Log File Format. See
 * <a href="http://www.w3.org/TR/WD-logfile.html">WD-logfile-960323</a> for more information about the format. The
 * following fields are supported:
 * <ul>
 * <li><code>c-dns</code>: Client hostname (or ip address if <code>enableLookups</code> for the connector is false)</li>
 * <li><code>c-ip</code>: Client ip address</li>
 * <li><code>bytes</code>: bytes served</li>
 * <li><code>cs-method</code>: request method</li>
 * <li><code>cs-uri</code>: The full uri requested</li>
 * <li><code>cs-uri-query</code>: The query string</li>
 * <li><code>cs-uri-stem</code>: The uri without query string</li>
 * <li><code>date</code>: The date in yyyy-mm-dd format for GMT</li>
 * <li><code>s-dns</code>: The server dns entry</li>
 * <li><code>s-ip</code>: The server ip address</li>
 * <li><code>cs(xxx)</code>: The value of header xxx from client to server</li>
 * <li><code>sc(xxx)</code>: The value of header xxx from server to client</li>
 * <li><code>sc-status</code>: The status code</li>
 * <li><code>time</code>: Time the request was served</li>
 * <li><code>time-taken</code>: Time (in seconds) taken to serve the request</li>
 * <li><code>x-threadname</code>: Current request thread name (can compare later with stacktraces)</li>
 * <li><code>x-A(xxx)</code>: Pull xxx attribute from the servlet context</li>
 * <li><code>x-C(xxx)</code>: Pull the cookie(s) of the name xxx</li>
 * <li><code>x-O(xxx)</code>: Pull the all response header values xxx</li>
 * <li><code>x-R(xxx)</code>: Pull xxx attribute from the servlet request</li>
 * <li><code>x-S(xxx)</code>: Pull xxx attribute from the session</li>
 * <li><code>x-P(...)</code>: Call request.getParameter(...) and URLencode it. Helpful to capture certain POST
 * parameters.</li>
 * <li>For any of the x-H(...) the following method will be called from the HttpServletRequest object</li>
 * <li><code>x-H(authType)</code>: getAuthType</li>
 * <li><code>x-H(characterEncoding)</code>: getCharacterEncoding</li>
 * <li><code>x-H(connectionId)</code>: getConnectionId</li>
 * <li><code>x-H(contentLength)</code>: getContentLength</li>
 * <li><code>x-H(locale)</code>: getLocale</li>
 * <li><code>x-H(protocol)</code>: getProtocol</li>
 * <li><code>x-H(remoteUser)</code>: getRemoteUser</li>
 * <li><code>x-H(requestedSessionId)</code>: getRequestedSessionId</li>
 * <li><code>x-H(requestedSessionIdFromCookie)</code>: isRequestedSessionIdFromCookie</li>
 * <li><code>x-H(requestedSessionIdValid)</code>: isRequestedSessionIdValid</li>
 * <li><code>x-H(scheme)</code>: getScheme</li>
 * <li><code>x-H(secure)</code>: isSecure</li>
 * </ul>
 */
public class ExtendedAccessLogValve extends AccessLogValve {

    /**
     * Creates a new ExtendedAccessLogValve instance.
     */
    public ExtendedAccessLogValve() {
    }

    private static final Log log = LogFactory.getLog(ExtendedAccessLogValve.class);

    // -------------------------------------------------------- Private Methods

    /**
     * Calls toString() on the object, wraps the result with double quotes (") and writes the result to the buffer. Any
     * double quotes appearing in the value are escaped using two double quotes (""). If the value is null or if
     * toString() fails, '-' will be written to the buffer.
     *
     * @param value - The value to wrap
     * @param buf   the buffer to write to
     */
    static void wrap(Object value, CharArrayWriter buf) {
        String svalue;
        if (value == null || "-".equals(value)) {
            buf.append('-');
            return;
        }

        try {
            svalue = value.toString();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            /* Log error */
            buf.append('-');
            return;
        }

        buf.append('\"');
        if (!svalue.isEmpty()) {
            // Does the value contain a " ? If so must encode it
            escapeAndAppend(svalue, buf, true);
        }
        buf.append('\"');
    }

    @Override
    protected synchronized void open() {
        super.open();
        if (currentLogFile.length() == 0) {
            writer.println("#Fields: " + pattern);
            writer.println("#Version: 2.0");
            writer.println("#Software: " + ServerInfo.getServerInfo());
        }
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Log element that outputs the date in yyyy-MM-dd format for GMT.
     */
    protected static class DateElement implements AccessLogElement {
        /**
         * Creates a new DateElement instance.
         */
        public DateElement() {
        }

        // Milliseconds in 24 hours
        private static final long INTERVAL = (1000 * 60 * 60 * 24);

        private static final ThreadLocal<ElementTimestampStruct> currentDate =
                ThreadLocal.withInitial(() -> new ElementTimestampStruct("yyyy-MM-dd"));

        @Override
        public void addElement(CharArrayWriter buf, Request request, Response response, long time) {
            ElementTimestampStruct eds = currentDate.get();
            long millis = eds.currentTimestamp.getTime();
            long epochMilli = request.getCoyoteRequest().getStartInstant().toEpochMilli();
            if (epochMilli > (millis + INTERVAL - 1) || epochMilli < millis) {
                eds.currentTimestamp.setTime(epochMilli - (epochMilli % INTERVAL));
                eds.currentTimestampString = eds.currentTimestampFormat.format(eds.currentTimestamp);
            }
            buf.append(eds.currentTimestampString);
        }
    }

    /**
     * Log element that outputs the time in HH:mm:ss format.
     */
    protected static class TimeElement implements AccessLogElement {
        /**
         * Creates a new TimeElement instance.
         */
        public TimeElement() {
        }

        // Milliseconds in a second
        private static final long INTERVAL = 1000;

        private static final ThreadLocal<ElementTimestampStruct> currentTime =
                ThreadLocal.withInitial(() -> new ElementTimestampStruct("HH:mm:ss"));

        @Override
        public void addElement(CharArrayWriter buf, Request request, Response response, long time) {
            ElementTimestampStruct eds = currentTime.get();
            long millis = eds.currentTimestamp.getTime();
            long epochMilli = request.getCoyoteRequest().getStartInstant().toEpochMilli();
            if (epochMilli > (millis + INTERVAL - 1) || epochMilli < millis) {
                eds.currentTimestamp.setTime(epochMilli - (epochMilli % INTERVAL));
                eds.currentTimestampString = eds.currentTimestampFormat.format(eds.currentTimestamp);
            }
            buf.append(eds.currentTimestampString);
        }
    }

    /**
     * Log element that outputs a specific request header value.
     */
    protected static class RequestHeaderElement implements AccessLogElement {
        private final String header;

        /**
         * Creates a new RequestHeaderElement for the specified header.
         *
         * @param header the name of the request header
         */
        public RequestHeaderElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Request request, Response response, long time) {
            wrap(request.getHeader(header), buf);
        }
    }

    /**
     * Log element that outputs a specific response header value.
     */
    protected static class ResponseHeaderElement implements AccessLogElement {
        private final String header;

        /**
         * Creates a new ResponseHeaderElement for the specified header.
         *
         * @param header the name of the response header
         */
        public ResponseHeaderElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Request request, Response response, long time) {
            wrap(response.getHeader(header), buf);
        }
    }

    /**
     * Log element that outputs a servlet context attribute value.
     */
    protected static class ServletContextElement implements AccessLogElement {
        private final String attribute;

        /**
         * Creates a new ServletContextElement for the specified attribute.
         *
         * @param attribute the name of the servlet context attribute
         */
        public ServletContextElement(String attribute) {
            this.attribute = attribute;
        }

        @Override
        public void addElement(CharArrayWriter buf, Request request, Response response, long time) {
            wrap(request.getContext().getServletContext().getAttribute(attribute), buf);
        }
    }

    /**
     * Log element that outputs the value(s) of a named cookie.
     */
    protected static class CookieElement implements AccessLogElement {
        private final String name;

        /**
         * Creates a new CookieElement for the specified cookie name.
         *
         * @param name the name of the cookie
         */
        public CookieElement(String name) {
            this.name = name;
        }

        @Override
        public void addElement(CharArrayWriter buf, Request request, Response response, long time) {
            StringBuilder value = new StringBuilder();
            boolean first = true;
            Cookie[] c = request.getCookies();
            for (int i = 0; c != null && i < c.length; i++) {
                if (name.equals(c[i].getName())) {
                    if (first) {
                        first = false;
                    } else {
                        value.append(',');
                    }
                    value.append(c[i].getValue());
                }
            }
            if (value.isEmpty()) {
                buf.append('-');
            } else {
                wrap(value, buf);
            }
        }
    }

    /**
     * write a specific response header - x-O(xxx)
     */
    protected static class ResponseAllHeaderElement implements AccessLogElement {
        private final String header;

        /**
         * Creates a new ResponseAllHeaderElement for the specified header.
         *
         * @param header the name of the response header
         */
        public ResponseAllHeaderElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Request request, Response response, long time) {
            if (null != response) {
                Iterator<String> iter = response.getHeaders(header).iterator();
                if (iter.hasNext()) {
                    StringBuilder buffer = new StringBuilder();
                    boolean first = true;
                    while (iter.hasNext()) {
                        if (first) {
                            first = false;
                        } else {
                            buffer.append(',');
                        }
                        buffer.append(iter.next());
                    }
                    wrap(buffer, buf);
                } else {
                    buf.append('-');
                }
                return;
            }
            buf.append('-');
        }
    }

    /**
     * Log element that outputs a servlet request attribute value.
     */
    protected static class RequestAttributeElement implements AccessLogElement {
        private final String attribute;

        /**
         * Creates a new RequestAttributeElement for the specified attribute.
         *
         * @param attribute the name of the request attribute
         */
        public RequestAttributeElement(String attribute) {
            this.attribute = attribute;
        }

        @Override
        public void addElement(CharArrayWriter buf, Request request, Response response, long time) {
            wrap(request.getAttribute(attribute), buf);
        }
    }

    /**
     * Log element that outputs a session attribute value.
     */
    protected static class SessionAttributeElement implements AccessLogElement {
        private final String attribute;

        /**
         * Creates a new SessionAttributeElement for the specified attribute.
         *
         * @param attribute the name of the session attribute
         */
        public SessionAttributeElement(String attribute) {
            this.attribute = attribute;
        }

        @Override
        public void addElement(CharArrayWriter buf, Request request, Response response, long time) {
            HttpSession session;
            if (request != null) {
                session = request.getSession(false);
                if (session != null) {
                    wrap(session.getAttribute(attribute), buf);
                }
            }
        }
    }

    /**
     * Log element that outputs a URL-encoded request parameter value.
     */
    protected static class RequestParameterElement implements AccessLogElement {
        private final String parameter;

        /**
         * Creates a new RequestParameterElement for the specified parameter.
         *
         * @param parameter the name of the request parameter
         */
        public RequestParameterElement(String parameter) {
            this.parameter = parameter;
        }

        /**
         * urlEncode the given string. If null or empty, return null.
         */
        private String urlEncode(String value) {
            if (null == value || value.isEmpty()) {
                return null;
            }
            return URLEncoder.QUERY.encode(value, StandardCharsets.UTF_8);
        }

        @Override
        public void addElement(CharArrayWriter buf, Request request, Response response, long time) {
            String parameterValue;
            try {
                parameterValue = request.getParameter(parameter);
            } catch (IllegalStateException ise) {
                parameterValue = null;
            }
            wrap(urlEncode(parameterValue), buf);
        }
    }

    /**
     * Tokenizer for parsing the log pattern string.
     */
    protected static class PatternTokenizer {
        private final StringReader sr;
        private StringBuilder buf = new StringBuilder();
        private boolean ended = false;
        private boolean subToken;
        private boolean parameter;

        /**
         * Creates a new tokenizer for the given pattern string.
         *
         * @param str the pattern string to tokenize
         */
        public PatternTokenizer(String str) {
            sr = new StringReader(str);
        }

        /**
         * Returns whether the last token was followed by a sub-token indicator.
         *
         * @return {@code true} if the last token had a sub-token
         */
        public boolean hasSubToken() {
            return subToken;
        }

        /**
         * Returns whether the last token was followed by a parameter indicator.
         *
         * @return {@code true} if the last token had a parameter
         */
        public boolean hasParameter() {
            return parameter;
        }

        /**
         * Returns the next token from the pattern string.
         *
         * @return the next token, or {@code null} if no more tokens are available
         * @throws IOException if an I/O error occurs
         */
        public String getToken() throws IOException {
            if (ended) {
                return null;
            }

            String result;
            subToken = false;
            parameter = false;

            int c = sr.read();
            while (c != -1) {
                switch (c) {
                    case ' ':
                        result = buf.toString();
                        buf.setLength(0);
                        buf.append((char) c);
                        return result;
                    case '-':
                        result = buf.toString();
                        buf.setLength(0);
                        subToken = true;
                        return result;
                    case '(':
                        result = buf.toString();
                        buf.setLength(0);
                        parameter = true;
                        return result;
                    case ')':
                        throw new IOException(sm.getString("patternTokenizer.unexpectedParenthesis"));
                    default:
                        buf.append((char) c);
                }
                c = sr.read();
            }
            ended = true;
            if (!buf.isEmpty()) {
                return buf.toString();
            } else {
                return null;
            }
        }

        /**
         * Returns the parameter value from the most recent parameter token.
         *
         * @return the parameter value, or {@code null} if no parameter is available
         * @throws IOException if an I/O error occurs
         */
        public String getParameter() throws IOException {
            String result;
            if (!parameter) {
                return null;
            }
            parameter = false;
            int c = sr.read();
            while (c != -1) {
                if (c == ')') {
                    result = buf.toString();
                    buf = new StringBuilder();
                    return result;
                }
                buf.append((char) c);
                c = sr.read();
            }
            return null;
        }

        /**
         * Returns any whitespace following the current position.
         *
         * @return the whitespace characters, or an empty string if none
         * @throws IOException if an I/O error occurs
         */
        public String getWhiteSpaces() throws IOException {
            if (isEnded()) {
                return "";
            }
            StringBuilder whiteSpaces = new StringBuilder();
            if (!buf.isEmpty()) {
                whiteSpaces.append(buf);
                buf = new StringBuilder();
            }
            int c = sr.read();
            while (Character.isWhitespace((char) c)) {
                whiteSpaces.append((char) c);
                c = sr.read();
            }
            if (c == -1) {
                ended = true;
            } else {
                buf.append((char) c);
            }
            return whiteSpaces.toString();
        }

        /**
         * Returns whether the tokenizer has reached the end of the pattern string.
         *
         * @return {@code true} if the end has been reached
         */
        public boolean isEnded() {
            return ended;
        }

        /**
         * Returns the remaining unparsed portion of the pattern string.
         *
         * @return the remaining characters in the pattern
         * @throws IOException if an I/O error occurs
         */
        public String getRemains() throws IOException {
            StringBuilder remains = new StringBuilder();
            for (int c = sr.read(); c != -1; c = sr.read()) {
                remains.append((char) c);
            }
            return remains.toString();
        }

    }

    @Override
    protected AccessLogElement[] createLogElements() {
        if (log.isTraceEnabled()) {
            log.trace("decodePattern, pattern =" + pattern);
        }
        List<AccessLogElement> list = new ArrayList<>();

        PatternTokenizer tokenizer = new PatternTokenizer(pattern);
        try {

            // Ignore leading whitespace.
            tokenizer.getWhiteSpaces();

            if (tokenizer.isEnded()) {
                log.info(sm.getString("extendedAccessLogValve.emptyPattern"));
                return null;
            }

            String token = tokenizer.getToken();
            while (token != null) {
                if (log.isTraceEnabled()) {
                    log.trace("token = " + token);
                }
                AccessLogElement element = getLogElement(token, tokenizer);
                if (element == null) {
                    break;
                }
                list.add(element);
                String whiteSpaces = tokenizer.getWhiteSpaces();
                if (!whiteSpaces.isEmpty()) {
                    list.add(new StringElement(whiteSpaces));
                }
                if (tokenizer.isEnded()) {
                    break;
                }
                token = tokenizer.getToken();
            }
            if (log.isTraceEnabled()) {
                log.trace("finished decoding with element size of: " + list.size());
            }
            return list.toArray(new AccessLogElement[0]);
        } catch (IOException ioe) {
            log.error(sm.getString("extendedAccessLogValve.patternParseError", pattern), ioe);
            return null;
        }
    }

    /**
     * Returns the appropriate log element for the given token.
     *
     * @param token     the pattern token
     * @param tokenizer the pattern tokenizer
     * @return the log element, or {@code null} if the token is unrecognized
     * @throws IOException if an I/O error occurs while reading the pattern
     */
    protected AccessLogElement getLogElement(String token, PatternTokenizer tokenizer) throws IOException {
        switch (token) {
            case "date" -> {
                return new DateElement();
            }
            case "time" -> {
                if (tokenizer.hasSubToken()) {
                    String nextToken = tokenizer.getToken();
                    if ("taken".equals(nextToken)) {
                        if (tokenizer.hasSubToken()) {
                            nextToken = tokenizer.getToken();
                            return switch (nextToken) {
                                case "ns" -> new ElapsedTimeElement(ElapsedTimeElement.Style.NANOSECONDS);
                                case "us" -> new ElapsedTimeElement(ElapsedTimeElement.Style.MICROSECONDS);
                                case "ms" -> new ElapsedTimeElement(ElapsedTimeElement.Style.MILLISECONDS);
                                case "fracsec" -> new ElapsedTimeElement(ElapsedTimeElement.Style.SECONDS_FRACTIONAL);
                                case null, default -> new ElapsedTimeElement(ElapsedTimeElement.Style.SECONDS);
                            };
                        } else {
                            return new ElapsedTimeElement(ElapsedTimeElement.Style.SECONDS);
                        }
                    }
                } else {
                    return new TimeElement();
                }
            }
            case "bytes" -> {
                return new ByteSentElement(true);
            }
            case "cached" -> {
                /* I don't know how to evaluate this! */
                return new StringElement("-");
                /* I don't know how to evaluate this! */
            }
            case "c" -> {
                String nextToken = tokenizer.getToken();
                if ("ip".equals(nextToken)) {
                    return new RemoteAddrElement();
                } else if ("dns".equals(nextToken)) {
                    return new HostElement();
                }
            }
            case "s" -> {
                String nextToken = tokenizer.getToken();
                if ("ip".equals(nextToken)) {
                    return new LocalAddrElement(getIpv6Canonical());
                } else if ("dns".equals(nextToken)) {
                    return (buf, req, res, l) -> {
                        String value;
                        try {
                            value = InetAddress.getLocalHost().getHostName();
                        } catch (Throwable t) {
                            ExceptionUtils.handleThrowable(t);
                            value = "localhost";
                        }
                        buf.append(value);
                    };
                }
            }
            case "cs" -> {
                return getClientToServerElement(tokenizer);
            }
            case "sc" -> {
                return getServerToClientElement(tokenizer);
            }
            case "sr", "rs" -> {
                return getProxyElement(tokenizer);
            }
            case "x" -> {
                return getXParameterElement(tokenizer);
            }
            case null, default -> {
            }
        }
        log.error(sm.getString("extendedAccessLogValve.decodeError", token));
        return null;
    }

    /**
     * Returns the appropriate log element for a client-to-server token.
     *
     * @param tokenizer the pattern tokenizer
     * @return the log element, or {@code null} if unrecognized
     * @throws IOException if an I/O error occurs while reading the pattern
     */
    protected AccessLogElement getClientToServerElement(PatternTokenizer tokenizer) throws IOException {
        if (tokenizer.hasSubToken()) {
            String token = tokenizer.getToken();
            if ("method".equals(token)) {
                return new MethodElement();
            } else if ("uri".equals(token)) {
                if (tokenizer.hasSubToken()) {
                    token = tokenizer.getToken();
                    if ("stem".equals(token)) {
                        return new RequestURIElement();
                    } else if ("query".equals(token)) {
                        return (buf, request, res, l) -> {
                            String query = null;
                            if (request != null) {
                                query = request.getQueryString();
                            }
                            appendQueryString(query, buf, false, true, true);
                        };
                    }
                } else {
                    return (buf, request, res, l) -> {
                        if (request != null) {
                            escapeAndAppend(request.getRequestURI(), buf);
                            appendQueryString(request.getQueryString(), buf, true, true, false);
                        } else {
                            buf.append('-');
                        }
                    };
                }
            }
        } else if (tokenizer.hasParameter()) {
            String parameter = tokenizer.getParameter();
            if (parameter == null) {
                log.error(sm.getString("extendedAccessLogValve.noClosing"));
                return null;
            }
            return new RequestHeaderElement(parameter);
        }
        log.error(sm.getString("extendedAccessLogValve.decodeError", tokenizer.getRemains()));
        return null;
    }

    /**
     * Returns the appropriate log element for a server-to-client token.
     *
     * @param tokenizer the pattern tokenizer
     * @return the log element, or {@code null} if unrecognized
     * @throws IOException if an I/O error occurs while reading the pattern
     */
    protected AccessLogElement getServerToClientElement(PatternTokenizer tokenizer) throws IOException {
        if (tokenizer.hasSubToken()) {
            String token = tokenizer.getToken();
            if ("status".equals(token)) {
                return new HttpStatusCodeElement();
            } else if ("comment".equals(token)) {
                return new StringElement("?");
            }
        } else if (tokenizer.hasParameter()) {
            String parameter = tokenizer.getParameter();
            if (parameter == null) {
                log.error(sm.getString("extendedAccessLogValve.noClosing"));
                return null;
            }
            return new ResponseHeaderElement(parameter);
        }
        log.error(sm.getString("extendedAccessLogValve.decodeError", tokenizer.getRemains()));
        return null;
    }

    /**
     * Returns the appropriate log element for a proxy token.
     *
     * @param tokenizer the pattern tokenizer
     * @return the log element, or {@code null} if unrecognized
     * @throws IOException if an I/O error occurs while reading the pattern
     */
    protected AccessLogElement getProxyElement(PatternTokenizer tokenizer) throws IOException {
        if (tokenizer.hasSubToken()) {
            tokenizer.getToken();
            return new StringElement("-");
        } else if (tokenizer.hasParameter()) {
            tokenizer.getParameter();
            return new StringElement("-");
        }
        log.error(sm.getString("extendedAccessLogValve.decodeError", tokenizer.getRemains()));
        return null;
    }

    /**
     * Returns the appropriate log element for an x-parameter token.
     *
     * @param tokenizer the pattern tokenizer
     * @return the log element, or {@code null} if unrecognized
     * @throws IOException if an I/O error occurs while reading the pattern
     */
    protected AccessLogElement getXParameterElement(PatternTokenizer tokenizer) throws IOException {
        if (!tokenizer.hasSubToken()) {
            log.error(sm.getString("extendedAccessLogValve.badXParam"));
            return null;
        }
        String token = tokenizer.getToken();
        if ("threadname".equals(token)) {
            return new ThreadNameElement();
        }

        if (!tokenizer.hasParameter()) {
            log.error(sm.getString("extendedAccessLogValve.badXParam"));
            return null;
        }
        String parameter = tokenizer.getParameter();
        if (parameter == null) {
            log.error(sm.getString("extendedAccessLogValve.noClosing"));
            return null;
        }
        switch (token) {
            case "A" -> {
                return new ServletContextElement(parameter);
            }
            case "C" -> {
                return new CookieElement(parameter);
            }
            case "R" -> {
                return new RequestAttributeElement(parameter);
            }
            case "S" -> {
                return new SessionAttributeElement(parameter);
            }
            case "H" -> {
                return getServletRequestElement(parameter);
            }
            case "P" -> {
                return new RequestParameterElement(parameter);
            }
            case "O" -> {
                return new ResponseAllHeaderElement(parameter);
            }
            case null, default -> {
                log.error(sm.getString("extendedAccessLogValve.badXParamValue", token));
                return null;
            }
        }
    }

    /**
     * Returns the appropriate log element for a servlet request method parameter.
     *
     * @param parameter the parameter name mapping to a request method
     * @return the log element, or {@code null} if the parameter is unrecognized
     */
    protected AccessLogElement getServletRequestElement(String parameter) {
        switch (parameter) {
            case "authType" -> {
                return (buf, request, res, l) -> wrap(request.getAuthType(), buf);
            }
            case "remoteUser" -> {
                return (buf, request, res, l) -> wrap(request.getRemoteUser(), buf);
            }
            case "requestedSessionId" -> {
                return (buf, request, res, l) -> wrap(request.getRequestedSessionId(), buf);
            }
            case "requestedSessionIdFromCookie" -> {
                return (buf, request, res, l) -> wrap(String.valueOf(request.isRequestedSessionIdFromCookie()), buf);
            }
            case "requestedSessionIdValid" -> {
                return (buf, request, res, l) -> wrap(String.valueOf(request.isRequestedSessionIdValid()), buf);
            }
            case "contentLength" -> {
                return (buf, request, res, l) -> wrap(String.valueOf(request.getContentLengthLong()), buf);
            }
            case "connectionId" -> {
                return (buf, request, res, l) -> wrap(request.getServletConnection().getConnectionId(), buf);
            }
            case "characterEncoding" -> {
                return (buf, request, res, l) -> wrap(request.getCharacterEncoding(), buf);
            }
            case "locale" -> {
                return (buf, request, res, l) -> wrap(request.getLocale(), buf);
            }
            case "protocol" -> {
                return (buf, request, res, l) -> wrap(request.getProtocol(), buf);
            }
            case "scheme" -> {
                return (buf, request, res, l) -> buf.append(request.getScheme());
            }
            case "secure" -> {
                return (buf, request, res, l) -> wrap(Boolean.valueOf(request.isSecure()), buf);
            }
            case null, default -> {
                log.error(sm.getString("extendedAccessLogValve.badXParamValue", parameter));
                return null;
            }
        }
    }

    private static class ElementTimestampStruct {
        private final Date currentTimestamp = new Date(0);
        private final SimpleDateFormat currentTimestampFormat;
        private String currentTimestampString;

        ElementTimestampStruct(String format) {
            currentTimestampFormat = new SimpleDateFormat(format, Locale.US);
            currentTimestampFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
    }
}
