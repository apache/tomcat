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
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Globals;
import org.apache.catalina.Session;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.TLSUtil;
import org.apache.coyote.ActionCode;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.IPv6Utils;


/**
 * <p>
 * Abstract implementation of the <b>Valve</b> interface that generates a web server access log with the detailed line
 * contents matching a configurable pattern. The syntax of the available patterns is similar to that supported by the
 * <a href="https://httpd.apache.org/">Apache HTTP Server</a> <code>mod_log_config</code> module.
 * </p>
 * <p>
 * Patterns for the logged message may include constant text or any of the following replacement strings, for which the
 * corresponding information from the specified Response is substituted:
 * </p>
 * <ul>
 * <li><b><code>%a</code></b> - Remote IP address
 * <li><b><code>%A</code></b> - Local IP address
 * <li><b><code>%b</code></b> - Bytes sent, excluding HTTP headers, or '-' if no bytes were sent
 * <li><b><code>%B</code></b> - Bytes sent, excluding HTTP headers
 * <li><b><code>%h</code></b> - Remote host name (or IP address if <code>enableLookups</code> for the connector is
 * false)
 * <li><b><code>%H</code></b> - Request protocol
 * <li><b><code>%l</code></b> - Remote logical username from identd (always returns '-')
 * <li><b><code>%m</code></b> - Request method
 * <li><b><code>%p</code></b> - Local port
 * <li><b><code>%q</code></b> - Query string (prepended with a '?' if it exists, otherwise an empty string
 * <li><b><code>%r</code></b> - First line of the request
 * <li><b><code>%s</code></b> - HTTP status code of the response
 * <li><b><code>%S</code></b> - User session ID
 * <li><b><code>%t</code></b> - Date and time, in Common Log Format format
 * <li><b><code>%u</code></b> - Remote user that was authenticated
 * <li><b><code>%U</code></b> - Requested URL path
 * <li><b><code>%v</code></b> - Local server name
 * <li><b><code>%D</code></b> - Time taken to process the request, in microseconds
 * <li><b><code>%T</code></b> - Time taken to process the request, in seconds
 * <li><b><code>%F</code></b> - Time taken to commit the response, in milliseconds
 * <li><b><code>%I</code></b> - current Request thread name (can compare later with stacktraces)
 * <li><b><code>%X</code></b> - Connection status when response is completed:
 * <ul>
 * <li><code>X</code> = Connection aborted before the response completed.</li>
 * <li><code>+</code> = Connection may be kept alive after the response is sent.</li>
 * <li><code>-</code> = Connection will be closed after the response is sent.</li>
 * </ul>
 * </ul>
 * <p>
 * In addition, the caller can specify one of the following aliases for commonly utilized patterns:
 * </p>
 * <ul>
 * <li><b>common</b> - <code>%h %l %u %t "%r" %s %b</code>
 * <li><b>combined</b> - <code>%h %l %u %t "%r" %s %b "%{Referer}i" "%{User-Agent}i"</code>
 * </ul>
 * <p>
 * There is also support to write information from the cookie, incoming header, the Session or something else in the
 * ServletRequest.<br>
 * It is modeled after the <a href="https://httpd.apache.org/">Apache HTTP Server</a> log configuration syntax:
 * </p>
 * <ul>
 * <li><code>%{xxx}i</code> for incoming headers
 * <li><code>%{xxx}o</code> for outgoing response headers
 * <li><code>%{xxx}c</code> for a specific cookie
 * <li><code>%{xxx}r</code> xxx is an attribute in the ServletRequest
 * <li><code>%{xxx}s</code> xxx is an attribute in the HttpSession
 * <li><code>%{xxx}t</code> xxx is an enhanced SimpleDateFormat pattern (see Configuration Reference document for
 * details on supported time patterns)
 * <li><code>%{xxx}T</code> xxx is the unit for the time taken to process the request (see Configuration Reference
 * document for details on supported units)
 * </ul>
 * <p>
 * Conditional logging is also supported. This can be done with the <code>conditionUnless</code> and
 * <code>conditionIf</code> properties. If the value returned from ServletRequest.getAttribute(conditionUnless) yields a
 * non-null value, the logging will be skipped. If the value returned from ServletRequest.getAttribute(conditionIf)
 * yields the null value, the logging will be skipped. The <code>condition</code> attribute is synonym for
 * <code>conditionUnless</code> and is provided for backwards compatibility.
 * </p>
 * <p>
 * For extended attributes coming from a getAttribute() call, it is you responsibility to ensure there are no newline or
 * control characters.
 * </p>
 *
 * @author Craig R. McClanahan
 * @author Jason Brittain
 * @author Remy Maucherat
 * @author Takayuki Kaneko
 * @author Peter Rossbach
 */
public abstract class AbstractAccessLogValve extends ValveBase implements AccessLog {

    private static final Log log = LogFactory.getLog(AbstractAccessLogValve.class);

    /**
     * The list of our time format types.
     */
    private enum FormatType {
        CLF,
        SEC,
        MSEC,
        MSEC_FRAC,
        SDF
    }

    /**
     * The list of our port types.
     */
    private enum PortType {
        LOCAL,
        REMOTE
    }

    /**
     * The list of our ip address types.
     */
    private enum RemoteAddressType {
        REMOTE,
        PEER
    }

    // ------------------------------------------------------ Constructor
    public AbstractAccessLogValve() {
        super(true);
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * enabled this component
     */
    protected boolean enabled = true;

    /**
     * Use IPv6 canonical representation format as defined by RFC 5952.
     */
    private boolean ipv6Canonical = false;

    /**
     * The pattern used to format our access log lines.
     */
    protected String pattern = null;

    /**
     * The size of our global date format cache
     */
    private static final int globalCacheSize = 300;

    /**
     * The size of our thread local date format cache
     */
    private static final int localCacheSize = 60;

    /**
     * <p>
     * Cache structure for formatted timestamps based on seconds.
     * </p>
     * <p>
     * The cache consists of entries for a consecutive range of seconds. The length of the range is configurable. It is
     * implemented based on a cyclic buffer. New entries shift the range.
     * </p>
     * <p>
     * There is one cache for the CLF format (the access log standard format) and a HashMap of caches for additional
     * formats used by SimpleDateFormat.
     * </p>
     * <p>
     * Although the cache supports specifying a locale when retrieving a formatted timestamp, each format will always
     * use the locale given when the format was first used. New locales can only be used for new formats. The CLF format
     * will always be formatted using the locale <code>en_US</code>.
     * </p>
     * <p>
     * The cache is not threadsafe. It can be used without synchronization via thread local instances, or with
     * synchronization as a global cache.
     * </p>
     * <p>
     * The cache can be created with a parent cache to build a cache hierarchy. Access to the parent cache is
     * threadsafe.
     * </p>
     * <p>
     * This class uses a small thread local first level cache and a bigger synchronized global second level cache.
     * </p>
     */
    protected static class DateFormatCache {

        protected class Cache {

            /* CLF log format */
            private static final String cLFFormat = "dd/MMM/yyyy:HH:mm:ss Z";

            /* Second used to retrieve CLF format in most recent invocation */
            private long previousSeconds = Long.MIN_VALUE;
            /* Value of CLF format retrieved in most recent invocation */
            private String previousFormat = "";

            /* First second contained in cache */
            private long first = Long.MIN_VALUE;
            /* Last second contained in cache */
            private long last = Long.MIN_VALUE;
            /* Index of "first" in the cyclic cache */
            private int offset = 0;
            /* Helper object to be able to call SimpleDateFormat.format(). */
            private final Date currentDate = new Date();

            protected final String cache[];
            private SimpleDateFormat formatter;
            private boolean isCLF = false;

            private Cache parent = null;

            private Cache(Cache parent) {
                this(null, parent);
            }

            private Cache(String format, Cache parent) {
                this(format, null, parent);
            }

            private Cache(String format, Locale loc, Cache parent) {
                cache = new String[cacheSize];
                for (int i = 0; i < cacheSize; i++) {
                    cache[i] = null;
                }
                if (loc == null) {
                    loc = cacheDefaultLocale;
                }
                if (format == null) {
                    isCLF = true;
                    format = cLFFormat;
                    formatter = new SimpleDateFormat(format, Locale.US);
                } else {
                    formatter = new SimpleDateFormat(format, loc);
                }
                formatter.setTimeZone(TimeZone.getDefault());
                this.parent = parent;
            }

            private String getFormatInternal(long time) {

                long seconds = time / 1000;

                /*
                 * First step: if we have seen this timestamp during the previous call, and we need CLF, return the
                 * previous value.
                 */
                if (seconds == previousSeconds) {
                    return previousFormat;
                }

                /* Second step: Try to locate in cache */
                previousSeconds = seconds;
                int index = (offset + (int) (seconds - first)) % cacheSize;
                if (index < 0) {
                    index += cacheSize;
                }
                if (seconds >= first && seconds <= last) {
                    if (cache[index] != null) {
                        /* Found, so remember for next call and return. */
                        previousFormat = cache[index];
                        return previousFormat;
                    }

                    /* Third step: not found in cache, adjust cache and add item */
                } else if (seconds >= last + cacheSize || seconds <= first - cacheSize) {
                    first = seconds;
                    last = first + cacheSize - 1;
                    index = 0;
                    offset = 0;
                    for (int i = 1; i < cacheSize; i++) {
                        cache[i] = null;
                    }
                } else if (seconds > last) {
                    for (int i = 1; i < seconds - last; i++) {
                        cache[(index + cacheSize - i) % cacheSize] = null;
                    }
                    first = seconds - (cacheSize - 1);
                    last = seconds;
                    offset = (index + 1) % cacheSize;
                } else if (seconds < first) {
                    for (int i = 1; i < first - seconds; i++) {
                        cache[(index + i) % cacheSize] = null;
                    }
                    first = seconds;
                    last = seconds + (cacheSize - 1);
                    offset = index;
                }

                /*
                 * Last step: format new timestamp either using parent cache or locally.
                 */
                if (parent != null) {
                    synchronized (parent) {
                        previousFormat = parent.getFormatInternal(time);
                    }
                } else {
                    currentDate.setTime(time);
                    previousFormat = formatter.format(currentDate);
                    if (isCLF) {
                        StringBuilder current = new StringBuilder(32);
                        current.append('[');
                        current.append(previousFormat);
                        current.append(']');
                        previousFormat = current.toString();
                    }
                }
                cache[index] = previousFormat;
                return previousFormat;
            }
        }

        /* Number of cached entries */
        private int cacheSize = 0;

        private final Locale cacheDefaultLocale;
        private final DateFormatCache parent;
        protected final Cache cLFCache;
        private final Map<String,Cache> formatCache = new HashMap<>();

        protected DateFormatCache(int size, Locale loc, DateFormatCache parent) {
            cacheSize = size;
            cacheDefaultLocale = loc;
            this.parent = parent;
            Cache parentCache = null;
            if (parent != null) {
                synchronized (parent) {
                    parentCache = parent.getCache(null, null);
                }
            }
            cLFCache = new Cache(parentCache);
        }

        private Cache getCache(String format, Locale loc) {
            Cache cache;
            if (format == null) {
                cache = cLFCache;
            } else {
                cache = formatCache.get(format);
                if (cache == null) {
                    Cache parentCache = null;
                    if (parent != null) {
                        synchronized (parent) {
                            parentCache = parent.getCache(format, loc);
                        }
                    }
                    cache = new Cache(format, loc, parentCache);
                    formatCache.put(format, cache);
                }
            }
            return cache;
        }

        public String getFormat(long time) {
            return cLFCache.getFormatInternal(time);
        }

        public String getFormat(String format, Locale loc, long time) {
            return getCache(format, loc).getFormatInternal(time);
        }
    }

    /**
     * Global date format cache.
     */
    private static final DateFormatCache globalDateCache =
            new DateFormatCache(globalCacheSize, Locale.getDefault(), null);

    /**
     * Thread local date format cache.
     */
    private static final ThreadLocal<DateFormatCache> localDateCache =
            ThreadLocal.withInitial(() -> new DateFormatCache(localCacheSize, Locale.getDefault(), globalDateCache));


    /**
     * The system time when we last updated the Date that this valve uses for log lines.
     */
    private static final ThreadLocal<Date> localDate = ThreadLocal.withInitial(Date::new);

    /**
     * Are we doing conditional logging. default null. It is the value of <code>conditionUnless</code> property.
     */
    protected String condition = null;

    /**
     * Are we doing conditional logging. default null. It is the value of <code>conditionIf</code> property.
     */
    protected String conditionIf = null;

    /**
     * Name of locale used to format timestamps in log entries and in log file name suffix.
     */
    protected String localeName = Locale.getDefault().toString();


    /**
     * Locale used to format timestamps in log entries and in log file name suffix.
     */
    protected Locale locale = Locale.getDefault();

    /**
     * Array of AccessLogElement, they will be used to make log message.
     */
    protected AccessLogElement[] logElements = null;

    /**
     * Array of elements where the value needs to be cached at the start of the request.
     */
    protected CachedElement[] cachedElements = null;

    /**
     * Should this valve use request attributes for IP address, hostname, protocol and port used for the request.
     * Default is <code>false</code>.
     *
     * @see #setRequestAttributesEnabled(boolean)
     */
    protected boolean requestAttributesEnabled = false;

    /**
     * Buffer pool used for log message generation. Pool used to reduce garbage generation.
     */
    private SynchronizedStack<CharArrayWriter> charArrayWriters = new SynchronizedStack<>();

    /**
     * Log message buffers are usually recycled and re-used. To prevent excessive memory usage, if a buffer grows beyond
     * this size it will be discarded. The default is 256 characters. This should be set to larger than the typical
     * access log message size.
     */
    private int maxLogMessageBufferSize = 256;

    /**
     * Does the configured log pattern include a known TLS attribute?
     */
    private boolean tlsAttributeRequired = false;


    // ------------------------------------------------------------- Properties

    public int getMaxLogMessageBufferSize() {
        return maxLogMessageBufferSize;
    }


    public void setMaxLogMessageBufferSize(int maxLogMessageBufferSize) {
        this.maxLogMessageBufferSize = maxLogMessageBufferSize;
    }


    public boolean getIpv6Canonical() {
        return ipv6Canonical;
    }


    public void setIpv6Canonical(boolean ipv6Canonical) {
        this.ipv6Canonical = ipv6Canonical;
    }


    /**
     * {@inheritDoc} Default is <code>false</code>.
     */
    @Override
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
        this.requestAttributesEnabled = requestAttributesEnabled;
    }


    @Override
    public boolean getRequestAttributesEnabled() {
        return requestAttributesEnabled;
    }

    /**
     * @return the enabled flag.
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * @param enabled The enabled to set.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the format pattern.
     */
    public String getPattern() {
        return this.pattern;
    }


    /**
     * Set the format pattern, first translating any recognized alias.
     *
     * @param pattern The new pattern
     */
    public void setPattern(String pattern) {
        if (pattern == null) {
            this.pattern = "";
        } else if (pattern.equals(Constants.AccessLog.COMMON_ALIAS)) {
            this.pattern = Constants.AccessLog.COMMON_PATTERN;
        } else if (pattern.equals(Constants.AccessLog.COMBINED_ALIAS)) {
            this.pattern = Constants.AccessLog.COMBINED_PATTERN;
        } else {
            this.pattern = pattern;
        }
        logElements = createLogElements();
        if (logElements != null) {
            cachedElements = createCachedElements(logElements);
        }
    }

    /**
     * Return whether the attribute name to look for when performing conditional logging. If null, every request is
     * logged.
     *
     * @return the attribute name
     */
    public String getCondition() {
        return condition;
    }


    /**
     * Set the ServletRequest.attribute to look for to perform conditional logging. Set to null to log everything.
     *
     * @param condition Set to null to log everything
     */
    public void setCondition(String condition) {
        this.condition = condition;
    }


    /**
     * Return whether the attribute name to look for when performing conditional logging. If null, every request is
     * logged.
     *
     * @return the attribute name
     */
    public String getConditionUnless() {
        return getCondition();
    }


    /**
     * Set the ServletRequest.attribute to look for to perform conditional logging. Set to null to log everything.
     *
     * @param condition Set to null to log everything
     */
    public void setConditionUnless(String condition) {
        setCondition(condition);
    }

    /**
     * Return whether the attribute name to look for when performing conditional logging. If null, every request is
     * logged.
     *
     * @return the attribute name
     */
    public String getConditionIf() {
        return conditionIf;
    }


    /**
     * Set the ServletRequest.attribute to look for to perform conditional logging. Set to null to log everything.
     *
     * @param condition Set to null to log everything
     */
    public void setConditionIf(String condition) {
        this.conditionIf = condition;
    }

    /**
     * Return the locale used to format timestamps in log entries and in log file name suffix.
     *
     * @return the locale
     */
    public String getLocale() {
        return localeName;
    }


    /**
     * Set the locale used to format timestamps in log entries and in log file name suffix. Changing the locale is only
     * supported as long as the AccessLogValve has not logged anything. Changing the locale later can lead to
     * inconsistent formatting.
     *
     * @param localeName The locale to use.
     */
    public void setLocale(String localeName) {
        this.localeName = localeName;
        locale = findLocale(localeName, locale);
    }

    // --------------------------------------------------------- Public Methods

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        if (tlsAttributeRequired) {
            // The log pattern uses TLS attributes. Ensure these are populated
            // before the request is processed because with NIO2 it is possible
            // for the connection to be closed (and the TLS info lost) before
            // the access log requests the TLS info. Requesting it now causes it
            // to be cached in the request.
            request.getAttribute(Globals.CERTIFICATES_ATTR);
        }
        if (cachedElements != null) {
            for (CachedElement element : cachedElements) {
                element.cache(request);
            }
        }
        getNext().invoke(request, response);
    }


    @Override
    public void log(Request request, Response response, long time) {
        if (!getState().isAvailable() || !getEnabled() || logElements == null ||
                condition != null && null != request.getRequest().getAttribute(condition) ||
                conditionIf != null && null == request.getRequest().getAttribute(conditionIf)) {
            return;
        }

        // Date for access log should be the beginning of the request
        Date date = getDate(request.getCoyoteRequest().getStartTime());

        CharArrayWriter result = charArrayWriters.pop();
        if (result == null) {
            result = new CharArrayWriter(128);
        }

        for (AccessLogElement logElement : logElements) {
            logElement.addElement(result, date, request, response, time);
        }

        log(result);

        if (result.size() <= maxLogMessageBufferSize) {
            result.reset();
            charArrayWriters.push(result);
        }
    }

    // -------------------------------------------------------- Protected Methods

    /**
     * Log the specified message.
     *
     * @param message Message to be logged. This object will be recycled by the calling method.
     */
    protected abstract void log(CharArrayWriter message);

    // -------------------------------------------------------- Private Methods

    /**
     * This method returns a Date object that is accurate to within one second. If a thread calls this method to get a
     * Date and it's been less than 1 second since a new Date was created, this method simply gives out the same Date
     * again so that the system doesn't spend time creating Date objects unnecessarily.
     *
     * @param systime The time
     *
     * @return the date object
     */
    private static Date getDate(long systime) {
        Date date = localDate.get();
        date.setTime(systime);
        return date;
    }


    /**
     * Find a locale by name.
     *
     * @param name     The locale name
     * @param fallback Fallback locale if the name is not found
     *
     * @return the locale object
     */
    protected static Locale findLocale(String name, Locale fallback) {
        if (name == null || name.isEmpty()) {
            return Locale.getDefault();
        } else {
            for (Locale l : Locale.getAvailableLocales()) {
                if (name.equals(l.toString())) {
                    return l;
                }
            }
        }
        log.error(sm.getString("accessLogValve.invalidLocale", name));
        return fallback;
    }


    /**
     * AccessLogElement writes the partial message into the buffer.
     */
    protected interface AccessLogElement {
        void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time);
    }

    /**
     * Marks an AccessLogElement as needing to be have the value cached at the start of the request rather than just
     * recorded at the end as the source data for the element may not be available at the end of the request. This
     * typically occurs for remote network information, such as ports, IP addresses etc. when the connection is closed
     * unexpectedly. These elements take advantage of these values being cached elsewhere on first request and do not
     * cache the value in the element since the elements are state-less.
     */
    protected interface CachedElement {
        void cache(Request request);
    }

    /**
     * write thread name - %I
     */
    protected static class ThreadNameElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            RequestInfo info = request.getCoyoteRequest().getRequestProcessor();
            if (info != null) {
                buf.append(info.getWorkerThreadName());
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * write local IP address - %A
     */
    protected static class LocalAddrElement implements AccessLogElement {

        private final String localAddrValue;

        public LocalAddrElement(boolean ipv6Canonical) {
            String init;
            try {
                init = InetAddress.getLocalHost().getHostAddress();
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                init = "127.0.0.1";
            }

            if (ipv6Canonical) {
                localAddrValue = IPv6Utils.canonize(init);
            } else {
                localAddrValue = init;
            }
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            buf.append(localAddrValue);
        }
    }

    /**
     * write remote IP address - %a
     */
    protected class RemoteAddrElement implements AccessLogElement, CachedElement {
        /**
         * Type of address to log
         */
        private static final String remoteAddress = "remote";
        private static final String peerAddress = "peer";

        private final RemoteAddressType remoteAddressType;

        public RemoteAddrElement() {
            remoteAddressType = RemoteAddressType.REMOTE;
        }

        public RemoteAddrElement(String type) {
            switch (type) {
                case remoteAddress:
                    remoteAddressType = RemoteAddressType.REMOTE;
                    break;
                case peerAddress:
                    remoteAddressType = RemoteAddressType.PEER;
                    break;
                default:
                    log.error(sm.getString("accessLogValve.invalidRemoteAddressType", type));
                    remoteAddressType = RemoteAddressType.REMOTE;
                    break;
            }
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            String value = null;
            if (remoteAddressType == RemoteAddressType.PEER) {
                value = request.getPeerAddr();
            } else {
                if (requestAttributesEnabled) {
                    Object addr = request.getAttribute(REMOTE_ADDR_ATTRIBUTE);
                    if (addr == null) {
                        value = request.getRemoteAddr();
                    } else {
                        value = addr.toString();
                    }
                } else {
                    value = request.getRemoteAddr();
                }
            }

            if (ipv6Canonical) {
                value = IPv6Utils.canonize(value);
            }
            buf.append(value);
        }

        @Override
        public void cache(Request request) {
            if (!requestAttributesEnabled) {
                if (remoteAddressType == RemoteAddressType.PEER) {
                    request.getPeerAddr();
                } else {
                    request.getRemoteAddr();
                }
            }
        }
    }

    /**
     * write remote host name - %h
     */
    protected class HostElement implements AccessLogElement, CachedElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            String value = null;
            if (requestAttributesEnabled) {
                Object host = request.getAttribute(REMOTE_HOST_ATTRIBUTE);
                if (host != null) {
                    value = host.toString();
                }
            }
            if (value == null || value.length() == 0) {
                value = request.getRemoteHost();
            }
            if (value == null || value.length() == 0) {
                value = "-";
            }

            if (ipv6Canonical) {
                value = IPv6Utils.canonize(value);
            }
            buf.append(value);
        }

        @Override
        public void cache(Request request) {
            if (!requestAttributesEnabled) {
                request.getRemoteHost();
            }
        }
    }

    /**
     * write remote logical username from identd (always returns '-') - %l
     */
    protected static class LogicalUserNameElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            buf.append('-');
        }
    }

    /**
     * write request protocol - %H
     */
    protected class ProtocolElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (requestAttributesEnabled) {
                Object proto = request.getAttribute(PROTOCOL_ATTRIBUTE);
                if (proto == null) {
                    buf.append(request.getProtocol());
                } else {
                    buf.append(proto.toString());
                }
            } else {
                buf.append(request.getProtocol());
            }
        }
    }

    /**
     * write remote user that was authenticated (if any), else '-' - %u
     */
    protected static class UserElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (request != null) {
                String value = request.getRemoteUser();
                if (value != null) {
                    escapeAndAppend(value, buf);
                } else {
                    buf.append('-');
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * write date and time, in configurable format (default CLF) - %t or %{format}t
     */
    protected class DateAndTimeElement implements AccessLogElement {

        /**
         * Format prefix specifying request start time
         */
        private static final String requestStartPrefix = "begin";

        /**
         * Format prefix specifying response end time
         */
        private static final String responseEndPrefix = "end";

        /**
         * Separator between optional prefix and rest of format
         */
        private static final String prefixSeparator = ":";

        /**
         * Special format for seconds since epoch
         */
        private static final String secFormat = "sec";

        /**
         * Special format for milliseconds since epoch
         */
        private static final String msecFormat = "msec";

        /**
         * Special format for millisecond part of timestamp
         */
        private static final String msecFractionFormat = "msec_frac";

        /**
         * The patterns we use to replace "S" and "SSS" millisecond formatting of SimpleDateFormat by our own handling
         */
        private static final String msecPattern = "{#}";
        private static final String tripleMsecPattern = msecPattern + msecPattern + msecPattern;

        /* Our format description string, null if CLF */
        private final String format;
        /* Does the format string contain characters we need to escape */
        private final boolean needsEscaping;
        /* Whether to use begin of request or end of response as the timestamp */
        private final boolean usesBegin;
        /* The format type */
        private final FormatType type;
        /* Whether we need to postprocess by adding milliseconds */
        private boolean usesMsecs = false;

        protected DateAndTimeElement() {
            this(null);
        }

        /**
         * Replace the millisecond formatting character 'S' by some dummy characters in order to make the resulting
         * formatted time stamps cacheable. We replace the dummy chars later with the actual milliseconds because that's
         * relatively cheap.
         */
        private String tidyFormat(String format) {
            boolean escape = false;
            StringBuilder result = new StringBuilder();
            int len = format.length();
            char x;
            for (int i = 0; i < len; i++) {
                x = format.charAt(i);
                if (escape || x != 'S') {
                    result.append(x);
                } else {
                    result.append(msecPattern);
                    usesMsecs = true;
                }
                if (x == '\'') {
                    escape = !escape;
                }
            }
            return result.toString();
        }

        protected DateAndTimeElement(String sdf) {
            String format = sdf;
            boolean needsEscaping = false;
            if (sdf != null) {
                CharArrayWriter writer = new CharArrayWriter();
                escapeAndAppend(sdf, writer);
                String escaped = writer.toString();
                if (!escaped.equals(sdf)) {
                    needsEscaping = true;
                }
            }
            this.needsEscaping = needsEscaping;
            boolean usesBegin = false;
            FormatType type = FormatType.CLF;

            if (format != null) {
                if (format.equals(requestStartPrefix)) {
                    usesBegin = true;
                    format = "";
                } else if (format.startsWith(requestStartPrefix + prefixSeparator)) {
                    usesBegin = true;
                    format = format.substring(6);
                } else if (format.equals(responseEndPrefix)) {
                    usesBegin = false;
                    format = "";
                } else if (format.startsWith(responseEndPrefix + prefixSeparator)) {
                    usesBegin = false;
                    format = format.substring(4);
                }
                if (format.length() == 0) {
                    type = FormatType.CLF;
                } else if (format.equals(secFormat)) {
                    type = FormatType.SEC;
                } else if (format.equals(msecFormat)) {
                    type = FormatType.MSEC;
                } else if (format.equals(msecFractionFormat)) {
                    type = FormatType.MSEC_FRAC;
                } else {
                    type = FormatType.SDF;
                    format = tidyFormat(format);
                }
            }
            this.format = format;
            this.usesBegin = usesBegin;
            this.type = type;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            long timestamp = date.getTime();
            long frac;
            if (!usesBegin) {
                timestamp += TimeUnit.NANOSECONDS.toMillis(time);
            }
            switch (type) {
                case CLF:
                    buf.append(localDateCache.get().getFormat(timestamp));
                    break;
                case SEC:
                    buf.append(Long.toString(timestamp / 1000));
                    break;
                case MSEC:
                    buf.append(Long.toString(timestamp));
                    break;
                case MSEC_FRAC:
                    frac = timestamp % 1000;
                    if (frac < 100) {
                        if (frac < 10) {
                            buf.append('0');
                            buf.append('0');
                        } else {
                            buf.append('0');
                        }
                    }
                    buf.append(Long.toString(frac));
                    break;
                case SDF:
                    String temp = localDateCache.get().getFormat(format, locale, timestamp);
                    if (usesMsecs) {
                        frac = timestamp % 1000;
                        StringBuilder tripleMsec = new StringBuilder(4);
                        if (frac < 100) {
                            if (frac < 10) {
                                tripleMsec.append('0');
                                tripleMsec.append('0');
                            } else {
                                tripleMsec.append('0');
                            }
                        }
                        tripleMsec.append(frac);
                        temp = temp.replace(tripleMsecPattern, tripleMsec);
                        temp = temp.replace(msecPattern, Long.toString(frac));
                    }
                    if (needsEscaping) {
                        escapeAndAppend(temp, buf);
                    } else {
                        buf.append(temp);
                    }
                    break;
            }
        }
    }

    /**
     * write first line of the request (method and request URI) - %r
     */
    protected static class RequestElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (request != null) {
                String method = request.getMethod();
                if (method == null) {
                    // No method means no request line
                    buf.append('-');
                } else {
                    buf.append(request.getMethod());
                    buf.append(' ');
                    buf.append(request.getRequestURI());
                    if (request.getQueryString() != null) {
                        buf.append('?');
                        buf.append(request.getQueryString());
                    }
                    buf.append(' ');
                    buf.append(request.getProtocol());
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * write HTTP status code of the response - %s
     */
    protected static class HttpStatusCodeElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (response != null) {
                // This approach is used to reduce GC from toString conversion
                int status = response.getStatus();
                if (100 <= status && status < 1000) {
                    buf.append((char) ('0' + (status / 100))).append((char) ('0' + ((status / 10) % 10)))
                            .append((char) ('0' + (status % 10)));
                } else {
                    buf.append(Integer.toString(status));
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * write local or remote port for request connection - %p and %{xxx}p
     */
    protected class PortElement implements AccessLogElement, CachedElement {

        /**
         * Type of port to log
         */
        private static final String localPort = "local";
        private static final String remotePort = "remote";

        private final PortType portType;

        public PortElement() {
            portType = PortType.LOCAL;
        }

        public PortElement(String type) {
            switch (type) {
                case remotePort:
                    portType = PortType.REMOTE;
                    break;
                case localPort:
                    portType = PortType.LOCAL;
                    break;
                default:
                    log.error(sm.getString("accessLogValve.invalidPortType", type));
                    portType = PortType.LOCAL;
                    break;
            }
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (requestAttributesEnabled && portType == PortType.LOCAL) {
                Object port = request.getAttribute(SERVER_PORT_ATTRIBUTE);
                if (port == null) {
                    buf.append(Integer.toString(request.getServerPort()));
                } else {
                    buf.append(port.toString());
                }
            } else {
                if (portType == PortType.LOCAL) {
                    buf.append(Integer.toString(request.getServerPort()));
                } else {
                    buf.append(Integer.toString(request.getRemotePort()));
                }
            }
        }

        @Override
        public void cache(Request request) {
            if (portType == PortType.REMOTE) {
                request.getRemotePort();
            }
        }
    }

    /**
     * write bytes sent, excluding HTTP headers - %b, %B
     */
    protected static class ByteSentElement implements AccessLogElement {
        private final boolean conversion;

        /**
         * @param conversion <code>true</code> to write '-' instead of 0 - %b.
         */
        public ByteSentElement(boolean conversion) {
            this.conversion = conversion;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            // Don't need to flush since trigger for log message is after the
            // response has been committed
            long length = response.getBytesWritten(false);
            if (length <= 0) {
                // Protect against nulls and unexpected types as these values
                // may be set by untrusted applications
                Object start = request.getAttribute(Globals.SENDFILE_FILE_START_ATTR);
                if (start instanceof Long) {
                    Object end = request.getAttribute(Globals.SENDFILE_FILE_END_ATTR);
                    if (end instanceof Long) {
                        length = ((Long) end).longValue() - ((Long) start).longValue();
                    }
                }
            }
            if (length <= 0 && conversion) {
                buf.append('-');
            } else {
                buf.append(Long.toString(length));
            }
        }
    }

    /**
     * write request method (GET, POST, etc.) - %m
     */
    protected static class MethodElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (request != null) {
                buf.append(request.getMethod());
            }
        }
    }

    /**
     * write time taken to process the request - %D, %T
     */
    protected static class ElapsedTimeElement implements AccessLogElement {
        enum Style {
            SECONDS {
                @Override
                public void append(CharArrayWriter buf, long time) {
                    buf.append(Long.toString(TimeUnit.NANOSECONDS.toSeconds(time)));
                }
            },
            SECONDS_FRACTIONAL {
                @Override
                public void append(CharArrayWriter buf, long time) {
                    time = time / 1000000; // Convert to millis
                    buf.append(Long.toString(time / 1000));
                    buf.append('.');
                    int remains = (int) (time % 1000);
                    buf.append(Long.toString(remains / 100));
                    remains = remains % 100;
                    buf.append(Long.toString(remains / 10));
                    buf.append(Long.toString(remains % 10));
                }
            },
            MILLISECONDS {
                @Override
                public void append(CharArrayWriter buf, long time) {
                    buf.append(Long.toString(TimeUnit.NANOSECONDS.toMillis(time)));
                }
            },
            MICROSECONDS {
                @Override
                public void append(CharArrayWriter buf, long time) {
                    buf.append(Long.toString(TimeUnit.NANOSECONDS.toMicros(time)));
                }
            },
            NANOSECONDS {
                @Override
                public void append(CharArrayWriter buf, long time) {
                    buf.append(Long.toString(time));
                }
            };

            /**
             * Append the time to the buffer in the appropriate format.
             *
             * @param buf  The buffer to append to.
             * @param time The time to log in nanoseconds.
             */
            public abstract void append(CharArrayWriter buf, long time);
        }

        private final Style style;

        /**
         * Creates a new ElapsedTimeElement that will log the time in the specified style.
         *
         * @param style The elapsed-time style to use.
         */
        public ElapsedTimeElement(Style style) {
            this.style = style;
        }

        /**
         * @param micros <code>true</code>, write time in microseconds - %D
         * @param millis <code>true</code>, write time in milliseconds, if both arguments are <code>false</code>, write
         *                   time in seconds - %T
         */
        public ElapsedTimeElement(boolean micros, boolean millis) {
            this(micros ? Style.MICROSECONDS : millis ? Style.MILLISECONDS : Style.SECONDS);
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            style.append(buf, time);
        }
    }

    /**
     * write time until first byte is written (commit time) in millis - %F
     */
    protected static class FirstByteTimeElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            long commitTime = response.getCoyoteResponse().getCommitTimeNanos();
            if (commitTime == -1) {
                buf.append('-');
            } else {
                long delta = commitTime - request.getCoyoteRequest().getStartTimeNanos();
                buf.append(Long.toString(TimeUnit.NANOSECONDS.toMillis(delta)));
            }
        }
    }

    /**
     * write Query string (prepended with a '?' if it exists) - %q
     */
    protected static class QueryElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            String query = null;
            if (request != null) {
                query = request.getQueryString();
            }
            if (query != null) {
                buf.append('?');
                buf.append(query);
            }
        }
    }

    /**
     * write user session ID - %S
     */
    protected static class SessionIdElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (request == null) {
                buf.append('-');
            } else {
                Session session = request.getSessionInternal(false);
                if (session == null) {
                    buf.append('-');
                } else {
                    buf.append(session.getIdInternal());
                }
            }
        }
    }

    /**
     * write requested URL path - %U
     */
    protected static class RequestURIElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (request != null) {
                buf.append(request.getRequestURI());
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * write local server name - %v
     */
    protected class LocalServerNameElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            String value = null;
            if (requestAttributesEnabled) {
                Object serverName = request.getAttribute(SERVER_NAME_ATTRIBUTE);
                if (serverName != null) {
                    value = serverName.toString();
                }
            }
            if (value == null || value.length() == 0) {
                value = request.getServerName();
            }
            if (value == null || value.length() == 0) {
                value = "-";
            }

            if (ipv6Canonical) {
                value = IPv6Utils.canonize(value);
            }
            buf.append(value);
        }
    }

    /**
     * write any string
     */
    protected static class StringElement implements AccessLogElement {
        private final String str;

        public StringElement(String str) {
            this.str = str;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            buf.append(str);
        }
    }

    /**
     * write incoming headers - %{xxx}i
     */
    protected static class HeaderElement implements AccessLogElement {
        private final String header;

        public HeaderElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            Enumeration<String> iter = request.getHeaders(header);
            if (iter.hasMoreElements()) {
                escapeAndAppend(iter.nextElement(), buf);
                while (iter.hasMoreElements()) {
                    buf.append(',');
                    escapeAndAppend(iter.nextElement(), buf);
                }
                return;
            }
            buf.append('-');
        }
    }

    /**
     * write a specific cookie - %{xxx}c
     */
    protected static class CookieElement implements AccessLogElement {
        private final String cookieNameToLog;

        public CookieElement(String cookieNameToLog) {
            this.cookieNameToLog = cookieNameToLog;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            StringBuilder value = null;
            boolean first = true;
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookieNameToLog.equals(cookie.getName())) {
                        if (value == null) {
                            value = new StringBuilder();
                        }
                        if (first) {
                            first = false;
                        } else {
                            value.append(',');
                        }
                        value.append(cookie.getValue());
                    }
                }
            }
            if (value == null) {
                buf.append('-');
            } else {
                escapeAndAppend(value.toString(), buf);
            }
        }
    }

    /**
     * write a specific response header - %{xxx}o
     */
    protected static class ResponseHeaderElement implements AccessLogElement {
        private final String header;

        public ResponseHeaderElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (null != response) {
                Iterator<String> iter = response.getHeaders(header).iterator();
                if (iter.hasNext()) {
                    escapeAndAppend(iter.next(), buf);
                    while (iter.hasNext()) {
                        buf.append(',');
                        escapeAndAppend(iter.next(), buf);
                    }
                    return;
                }
            }
            buf.append('-');
        }
    }

    /**
     * write an attribute in the ServletRequest - %{xxx}r
     */
    protected static class RequestAttributeElement implements AccessLogElement {
        private final String attribute;

        public RequestAttributeElement(String attribute) {
            this.attribute = attribute;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            Object value = null;
            if (request != null) {
                value = request.getAttribute(attribute);
            } else {
                value = "??";
            }
            if (value != null) {
                if (value instanceof String) {
                    escapeAndAppend((String) value, buf);
                } else {
                    escapeAndAppend(value.toString(), buf);
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * write an attribute in the HttpSession - %{xxx}s
     */
    protected static class SessionAttributeElement implements AccessLogElement {
        private final String attribute;

        public SessionAttributeElement(String attribute) {
            this.attribute = attribute;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            Object value = null;
            if (null != request) {
                HttpSession sess = request.getSession(false);
                if (null != sess) {
                    value = sess.getAttribute(attribute);
                }
            } else {
                value = "??";
            }
            if (value != null) {
                if (value instanceof String) {
                    escapeAndAppend((String) value, buf);
                } else {
                    escapeAndAppend(value.toString(), buf);
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * Write connection status when response is completed - %X
     */
    protected static class ConnectionStatusElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            if (response != null && request != null) {
                boolean statusFound = false;

                // Check whether connection IO is in "not allowed" state
                AtomicBoolean isIoAllowed = new AtomicBoolean(false);
                request.getCoyoteRequest().action(ActionCode.IS_IO_ALLOWED, isIoAllowed);
                if (!isIoAllowed.get()) {
                    buf.append('X');
                    statusFound = true;
                } else {
                    // Check for connection aborted cond
                    if (response.isError()) {
                        Throwable ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                        if (ex instanceof ClientAbortException) {
                            buf.append('X');
                            statusFound = true;
                        }
                    }
                }

                // If status is not found yet, cont to check whether connection is keep-alive or close
                if (!statusFound) {
                    String connStatus = response.getHeader(org.apache.coyote.http11.Constants.CONNECTION);
                    if (org.apache.coyote.http11.Constants.CLOSE.equalsIgnoreCase(connStatus)) {
                        buf.append('-');
                    } else {
                        buf.append('+');
                    }
                }
            } else {
                // Unknown connection status
                buf.append('?');
            }
        }
    }

    /**
     * Parse pattern string and create the array of AccessLogElement.
     *
     * @return the log elements array
     */
    protected AccessLogElement[] createLogElements() {
        List<AccessLogElement> list = new ArrayList<>();
        boolean replace = false;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (replace) {
                /*
                 * For code that processes {, the behavior will be ... if I do not encounter a closing } - then I ignore
                 * the {
                 */
                if ('{' == ch) {
                    StringBuilder name = new StringBuilder();
                    int j = i + 1;
                    for (; j < pattern.length() && '}' != pattern.charAt(j); j++) {
                        name.append(pattern.charAt(j));
                    }
                    if (j + 1 < pattern.length()) {
                        /* the +1 was to account for } which we increment now */
                        j++;
                        list.add(createAccessLogElement(name.toString(), pattern.charAt(j)));
                        i = j; /* Since we walked more than one character */
                    } else {
                        // D'oh - end of string - pretend we never did this
                        // and do processing the "old way"
                        list.add(createAccessLogElement(ch));
                    }
                } else {
                    list.add(createAccessLogElement(ch));
                }
                replace = false;
            } else if (ch == '%') {
                replace = true;
                list.add(new StringElement(buf.toString()));
                buf = new StringBuilder();
            } else {
                buf.append(ch);
            }
        }
        if (buf.length() > 0) {
            list.add(new StringElement(buf.toString()));
        }
        return list.toArray(new AccessLogElement[0]);
    }


    private CachedElement[] createCachedElements(AccessLogElement[] elements) {
        List<CachedElement> list = new ArrayList<>();
        for (AccessLogElement element : elements) {
            if (element instanceof CachedElement) {
                list.add((CachedElement) element);
            }
        }
        return list.toArray(new CachedElement[0]);
    }


    /**
     * Create an AccessLogElement implementation which needs an element name.
     *
     * @param name    Header name
     * @param pattern char in the log pattern
     *
     * @return the log element
     */
    protected AccessLogElement createAccessLogElement(String name, char pattern) {
        switch (pattern) {
            case 'i':
                return new HeaderElement(name);
            case 'c':
                return new CookieElement(name);
            case 'o':
                return new ResponseHeaderElement(name);
            case 'a':
                return new RemoteAddrElement(name);
            case 'p':
                return new PortElement(name);
            case 'r':
                if (TLSUtil.isTLSRequestAttribute(name)) {
                    tlsAttributeRequired = true;
                }
                return new RequestAttributeElement(name);
            case 's':
                return new SessionAttributeElement(name);
            case 't':
                return new DateAndTimeElement(name);
            case 'T':
                // ms for milliseconds, us for microseconds, and s for seconds
                if ("ns".equals(name)) {
                    return new ElapsedTimeElement(ElapsedTimeElement.Style.NANOSECONDS);
                } else if ("us".equals(name)) {
                    return new ElapsedTimeElement(ElapsedTimeElement.Style.MICROSECONDS);
                } else if ("ms".equals(name)) {
                    return new ElapsedTimeElement(ElapsedTimeElement.Style.MILLISECONDS);
                } else if ("fracsec".equals(name)) {
                    return new ElapsedTimeElement(ElapsedTimeElement.Style.SECONDS_FRACTIONAL);
                } else {
                    return new ElapsedTimeElement(false, false);
                }
            default:
                return new StringElement("???");
        }
    }

    /**
     * Create an AccessLogElement implementation.
     *
     * @param pattern char in the log pattern
     *
     * @return the log element
     */
    protected AccessLogElement createAccessLogElement(char pattern) {
        switch (pattern) {
            case 'a':
                return new RemoteAddrElement();
            case 'A':
                return new LocalAddrElement(ipv6Canonical);
            case 'b':
                return new ByteSentElement(true);
            case 'B':
                return new ByteSentElement(false);
            case 'D':
                return new ElapsedTimeElement(true, false);
            case 'F':
                return new FirstByteTimeElement();
            case 'h':
                return new HostElement();
            case 'H':
                return new ProtocolElement();
            case 'l':
                return new LogicalUserNameElement();
            case 'm':
                return new MethodElement();
            case 'p':
                return new PortElement();
            case 'q':
                return new QueryElement();
            case 'r':
                return new RequestElement();
            case 's':
                return new HttpStatusCodeElement();
            case 'S':
                return new SessionIdElement();
            case 't':
                return new DateAndTimeElement();
            case 'T':
                return new ElapsedTimeElement(false, false);
            case 'u':
                return new UserElement();
            case 'U':
                return new RequestURIElement();
            case 'v':
                return new LocalServerNameElement();
            case 'I':
                return new ThreadNameElement();
            case 'X':
                return new ConnectionStatusElement();
            default:
                return new StringElement("???" + pattern + "???");
        }
    }


    /*
     * This method is intended to mimic the escaping performed by httpd and mod_log_config. mod_log_config escapes more
     * elements than indicated by the documentation. See:
     * https://github.com/apache/httpd/blob/trunk/modules/loggers/mod_log_config.c
     *
     * The following escaped elements are not supported by Tomcat: - %C cookie value (see %{}c below) - %e environment
     * variable - %f filename - %l remote logname (always logs "-") - %n note - %R handler - %ti trailer request header
     * - %to trailer response header - %V server name per UseCanonicalName setting
     *
     * The following escaped elements are not escaped in Tomcat because values that would require escaping are rejected
     * before they reach the AccessLogValve: - %h remote host - %H request protocol - %m request method - %q query
     * string - %r request line - %U request URI - %v canonical server name
     *
     * The following escaped elements are supported by Tomcat: - %{}i request header - %{}o response header - %u remote
     * user
     *
     * The following additional Tomcat elements are escaped for consistency: - %{}c cookie value - %{}r request
     * attribute - %{}s session attribute
     *
     * giving a total of 6 elements that are escaped in Tomcat.
     *
     * Quoting from the httpd docs: "...non-printable and other special characters in %r, %i and %o are escaped using
     * \xhh sequences, where hh stands for the hexadecimal representation of the raw byte. Exceptions from this rule are
     * " and \, which are escaped by prepending a backslash, and all whitespace characters, which are written in their
     * C-style notation (\n, \t, etc)."
     *
     * Reviewing the httpd code, characters with the high bit set are escaped. The httpd is assuming a single byte
     * encoding which may not be true for Tomcat so Tomcat uses the Java \\uXXXX encoding.
     */
    protected static void escapeAndAppend(String input, CharArrayWriter dest) {
        if (input == null || input.isEmpty()) {
            dest.append('-');
            return;
        }

        int len = input.length();
        // As long as we don't encounter chars that need escaping,
        // we only remember start and length of that string part.
        // "next" is the start of the string part containing these chars,
        // "current - 1" is its end. So writing from "next" with length
        // "current - next" writes that part.
        // We write that part whenever we find a character to escape and the
        // unchanged and unwritten string part is not empty.
        int next = 0;
        char c;
        for (int current = 0; current < len; current++) {
            c = input.charAt(current);
            // Fast path
            if (c >= 32 && c < 127) {
                // special case " and \
                switch (c) {
                    case '\\': // dec 92
                        // Write unchanged string parts
                        if (current > next) {
                            dest.write(input, next, current - next);
                        }
                        next = current + 1;
                        dest.append("\\\\");
                        break;
                    case '\"': // dec 34
                        // Write unchanged string parts
                        if (current > next) {
                            dest.write(input, next, current - next);
                        }
                        next = current + 1;
                        dest.append("\\\"");
                        break;
                    // Don't output individual unchanged chars,
                    // write the sub string only when the first char to encode
                    // is encountered plus at the end.
                    default:
                }
                // Control (1-31), delete (127) or above 127
            } else {
                // Write unchanged string parts
                if (current > next) {
                    dest.write(input, next, current - next);
                }
                next = current + 1;
                switch (c) {
                    // Standard escapes for some control chars
                    case '\f': // dec 12
                        dest.append("\\f");
                        break;
                    case '\n': // dec 10
                        dest.append("\\n");
                        break;
                    case '\r': // dec 13
                        dest.append("\\r");
                        break;
                    case '\t': // dec 09
                        dest.append("\\t");
                        break;
                    // Unicode escape \\uXXXX
                    default:
                        dest.append("\\u");
                        dest.append(HexUtils.toHexString(c));
                }
            }
        }
        // Write remaining unchanged string parts
        if (len > next) {
            dest.write(input, next, len - next);
        }
    }
}
