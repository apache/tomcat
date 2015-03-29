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
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;


/**
 * <p>Abstract implementation of the <b>Valve</b> interface that generates a web
 * server access log with the detailed line contents matching a configurable
 * pattern. The syntax of the available patterns is similar to that supported by
 * the <a href="http://httpd.apache.org/">Apache HTTP Server</a>
 * <code>mod_log_config</code> module.</p>
 *
 * <p>Patterns for the logged message may include constant text or any of the
 * following replacement strings, for which the corresponding information
 * from the specified Response is substituted:</p>
 * <ul>
 * <li><b>%a</b> - Remote IP address
 * <li><b>%A</b> - Local IP address
 * <li><b>%b</b> - Bytes sent, excluding HTTP headers, or '-' if no bytes
 *     were sent
 * <li><b>%B</b> - Bytes sent, excluding HTTP headers
 * <li><b>%h</b> - Remote host name (or IP address if
 * <code>enableLookups</code> for the connector is false)
 * <li><b>%H</b> - Request protocol
 * <li><b>%l</b> - Remote logical username from identd (always returns '-')
 * <li><b>%m</b> - Request method
 * <li><b>%p</b> - Local port
 * <li><b>%q</b> - Query string (prepended with a '?' if it exists, otherwise
 *     an empty string
 * <li><b>%r</b> - First line of the request
 * <li><b>%s</b> - HTTP status code of the response
 * <li><b>%S</b> - User session ID
 * <li><b>%t</b> - Date and time, in Common Log Format format
 * <li><b>%t{format}</b> - Date and time, in any format supported by SimpleDateFormat
 * <li><b>%u</b> - Remote user that was authenticated
 * <li><b>%U</b> - Requested URL path
 * <li><b>%v</b> - Local server name
 * <li><b>%D</b> - Time taken to process the request, in millis
 * <li><b>%T</b> - Time taken to process the request, in seconds
 * <li><b>%I</b> - current Request thread name (can compare later with stacktraces)
 * </ul>
 * <p>In addition, the caller can specify one of the following aliases for
 * commonly utilized patterns:</p>
 * <ul>
 * <li><b>common</b> - <code>%h %l %u %t "%r" %s %b</code>
 * <li><b>combined</b> -
 *   <code>%h %l %u %t "%r" %s %b "%{Referer}i" "%{User-Agent}i"</code>
 * </ul>
 *
 * <p>
 * There is also support to write information from the cookie, incoming
 * header, the Session or something else in the ServletRequest.<br>
 * It is modeled after the
 * <a href="http://httpd.apache.org/">Apache HTTP Server</a> log configuration
 * syntax:</p>
 * <ul>
 * <li><code>%{xxx}i</code> for incoming headers
 * <li><code>%{xxx}o</code> for outgoing response headers
 * <li><code>%{xxx}c</code> for a specific cookie
 * <li><code>%{xxx}r</code> xxx is an attribute in the ServletRequest
 * <li><code>%{xxx}s</code> xxx is an attribute in the HttpSession
 * <li><code>%{xxx}t</code> xxx is an enhanced SimpleDateFormat pattern
 * (see Configuration Reference document for details on supported time patterns)
 * </ul>
 *
 * <p>
 * Conditional logging is also supported. This can be done with the
 * <code>conditionUnless</code> and <code>conditionIf</code> properties.
 * If the value returned from ServletRequest.getAttribute(conditionUnless)
 * yields a non-null value, the logging will be skipped.
 * If the value returned from ServletRequest.getAttribute(conditionIf)
 * yields the null value, the logging will be skipped.
 * The <code>condition</code> attribute is synonym for
 * <code>conditionUnless</code> and is provided for backwards compatibility.
 * </p>
 *
 * <p>
 * For extended attributes coming from a getAttribute() call,
 * it is you responsibility to ensure there are no newline or
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
    private static enum FormatType {
        CLF, SEC, MSEC, MSEC_FRAC, SDF
    }

    /**
     * The list of our port types.
     */
    private static enum PortType {
        LOCAL, REMOTE
    }

    //------------------------------------------------------ Constructor
    public AbstractAccessLogValve() {
        super(true);
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * enabled this component
     */
    protected boolean enabled = true;

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
     * <p>Cache structure for formatted timestamps based on seconds.</p>
     *
     * <p>The cache consists of entries for a consecutive range of
     * seconds. The length of the range is configurable. It is
     * implemented based on a cyclic buffer. New entries shift the range.</p>
     *
     * <p>There is one cache for the CLF format (the access log standard
     * format) and a HashMap of caches for additional formats used by
     * SimpleDateFormat.</p>
     *
     * <p>Although the cache supports specifying a locale when retrieving a
     * formatted timestamp, each format will always use the locale given
     * when the format was first used. New locales can only be used for new formats.
     * The CLF format will always be formatted using the locale
     * <code>en_US</code>.</p>
     *
     * <p>The cache is not threadsafe. It can be used without synchronization
     * via thread local instances, or with synchronization as a global cache.</p>
     *
     * <p>The cache can be created with a parent cache to build a cache hierarchy.
     * Access to the parent cache is threadsafe.</p>
     *
     * <p>This class uses a small thread local first level cache and a bigger
     * synchronized global second level cache.</p>
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

                /* First step: if we have seen this timestamp
                   during the previous call, and we need CLF, return the previous value. */
                if (seconds == previousSeconds) {
                    return previousFormat;
                }

                /* Second step: Try to locate in cache */
                previousSeconds = seconds;
                int index = (offset + (int)(seconds - first)) % cacheSize;
                if (index < 0) {
                    index += cacheSize;
                }
                if (seconds >= first && seconds <= last) {
                    if (cache[index] != null) {
                        /* Found, so remember for next call and return.*/
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

                /* Last step: format new timestamp either using
                 * parent cache or locally. */
                if (parent != null) {
                    synchronized(parent) {
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
        private final HashMap<String, Cache> formatCache = new HashMap<>();

        protected DateFormatCache(int size, Locale loc, DateFormatCache parent) {
            cacheSize = size;
            cacheDefaultLocale = loc;
            this.parent = parent;
            Cache parentCache = null;
            if (parent != null) {
                synchronized(parent) {
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
                        synchronized(parent) {
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
            new ThreadLocal<DateFormatCache>() {
        @Override
        protected DateFormatCache initialValue() {
            return new DateFormatCache(localCacheSize, Locale.getDefault(), globalDateCache);
        }
    };


    /**
     * The system time when we last updated the Date that this valve
     * uses for log lines.
     */
    private static final ThreadLocal<Date> localDate =
            new ThreadLocal<Date>() {
        @Override
        protected Date initialValue() {
            return new Date();
        }
    };

    /**
     * Are we doing conditional logging. default null.
     * It is the value of <code>conditionUnless</code> property.
     */
    protected String condition = null;

    /**
     * Are we doing conditional logging. default null.
     * It is the value of <code>conditionIf</code> property.
     */
    protected String conditionIf = null;

    /**
     * Name of locale used to format timestamps in log entries and in
     * log file name suffix.
     */
    protected String localeName = Locale.getDefault().toString();


    /**
     * Locale used to format timestamps in log entries and in
     * log file name suffix.
     */
    protected Locale locale = Locale.getDefault();

    /**
     * Array of AccessLogElement, they will be used to make log message.
     */
    protected AccessLogElement[] logElements = null;

    /**
     * @see #setRequestAttributesEnabled(boolean)
     */
    protected boolean requestAttributesEnabled = false;

    /**
     * Buffer pool used for log message generation. Pool used to reduce garbage
     * generation.
     */
    private SynchronizedStack<CharArrayWriter> charArrayWriters =
            new SynchronizedStack<>();

    /**
     * Log message buffers are usually recycled and re-used. To prevent
     * excessive memory usage, if a buffer grows beyond this size it will be
     * discarded. The default is 256 characters. This should be set to larger
     * than the typical access log message size.
     */
    private int maxLogMessageBufferSize = 256;

    // ------------------------------------------------------------- Properties

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
        this.requestAttributesEnabled = requestAttributesEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getRequestAttributesEnabled() {
        return requestAttributesEnabled;
    }

    /**
     * @return Returns the enabled.
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * @param enabled
     *            The enabled to set.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Return the format pattern.
     */
    public String getPattern() {
        return (this.pattern);
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
    }

    /**
     * Return whether the attribute name to look for when
     * performing conditional logging. If null, every
     * request is logged.
     */
    public String getCondition() {
        return condition;
    }


    /**
     * Set the ServletRequest.attribute to look for to perform
     * conditional logging. Set to null to log everything.
     *
     * @param condition Set to null to log everything
     */
    public void setCondition(String condition) {
        this.condition = condition;
    }


    /**
     * Return whether the attribute name to look for when
     * performing conditional logging. If null, every
     * request is logged.
     */
    public String getConditionUnless() {
        return getCondition();
    }


    /**
     * Set the ServletRequest.attribute to look for to perform
     * conditional logging. Set to null to log everything.
     *
     * @param condition Set to null to log everything
     */
    public void setConditionUnless(String condition) {
        setCondition(condition);
    }

    /**
     * Return whether the attribute name to look for when
     * performing conditional logging. If null, every
     * request is logged.
     */
    public String getConditionIf() {
        return conditionIf;
    }


    /**
     * Set the ServletRequest.attribute to look for to perform
     * conditional logging. Set to null to log everything.
     *
     * @param condition Set to null to log everything
     */
    public void setConditionIf(String condition) {
        this.conditionIf = condition;
    }

    /**
     * Return the locale used to format timestamps in log entries and in
     * log file name suffix.
     */
    public String getLocale() {
        return localeName;
    }


    /**
     * Set the locale used to format timestamps in log entries and in
     * log file name suffix. Changing the locale is only supported
     * as long as the AccessLogValve has not logged anything. Changing
     * the locale later can lead to inconsistent formatting.
     *
     * @param localeName The locale to use.
     */
    public void setLocale(String localeName) {
        this.localeName = localeName;
        locale = findLocale(localeName, locale);
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Log a message summarizing the specified request and response, according
     * to the format specified by the <code>pattern</code> property.
     *
     * @param request Request being processed
     * @param response Response being processed
     *
     * @exception IOException if an input/output error has occurred
     * @exception ServletException if a servlet error has occurred
     */
    @Override
    public void invoke(Request request, Response response) throws IOException,
            ServletException {
        getNext().invoke(request, response);
    }


    @Override
    public void log(Request request, Response response, long time) {
        if (!getState().isAvailable() || !getEnabled() || logElements == null
                || condition != null
                && null != request.getRequest().getAttribute(condition)
                || conditionIf != null
                && null == request.getRequest().getAttribute(conditionIf)) {
            return;
        }

        /**
         * XXX This is a bit silly, but we want to have start and stop time and
         * duration consistent. It would be better to keep start and stop
         * simply in the request and/or response object and remove time
         * (duration) from the interface.
         */
        long start = request.getCoyoteRequest().getStartTime();
        Date date = getDate(start + time);

        CharArrayWriter result = charArrayWriters.pop();
        if (result == null) {
            result = new CharArrayWriter(128);
        }

        for (int i = 0; i < logElements.length; i++) {
            logElements[i].addElement(result, date, request, response, time);
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
     * @param message Message to be logged. This object will be recycled by
     *  the calling method.
     */
    protected abstract void log(CharArrayWriter message);

    // -------------------------------------------------------- Private Methods

    /**
     * This method returns a Date object that is accurate to within one second.
     * If a thread calls this method to get a Date and it's been less than 1
     * second since a new Date was created, this method simply gives out the
     * same Date again so that the system doesn't spend time creating Date
     * objects unnecessarily.
     *
     * @return Date
     */
    private static Date getDate(long systime) {
        Date date = localDate.get();
        date.setTime(systime);
        return date;
    }


    /**
     * Find a locale by name
     */
    protected static Locale findLocale(String name, Locale fallback) {
        if (name == null || name.isEmpty()) {
            return Locale.getDefault();
        } else {
            for (Locale l: Locale.getAvailableLocales()) {
                if (name.equals(l.toString())) {
                    return(l);
                }
            }
        }
        log.error(sm.getString("accessLogValve.invalidLocale", name));
        return fallback;
    }


    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        setState(LifecycleState.STARTING);
    }


    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);
    }

    /**
     * AccessLogElement writes the partial message into the buffer.
     */
    protected interface AccessLogElement {
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time);

    }

    /**
     * write thread name - %I
     */
    protected static class ThreadNameElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            RequestInfo info = request.getCoyoteRequest().getRequestProcessor();
            if(info != null) {
                buf.append(info.getWorkerThreadName());
            } else {
                buf.append("-");
            }
        }
    }

    /**
     * write local IP address - %A
     */
    protected static class LocalAddrElement implements AccessLogElement {

        private static final String LOCAL_ADDR_VALUE;

        static {
            String init;
            try {
                init = InetAddress.getLocalHost().getHostAddress();
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                init = "127.0.0.1";
            }
            LOCAL_ADDR_VALUE = init;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            buf.append(LOCAL_ADDR_VALUE);
        }
    }

    /**
     * write remote IP address - %a
     */
    protected class RemoteAddrElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (requestAttributesEnabled) {
                Object addr = request.getAttribute(REMOTE_ADDR_ATTRIBUTE);
                if (addr == null) {
                    buf.append(request.getRemoteAddr());
                } else {
                    buf.append(addr.toString());
                }
            } else {
                buf.append(request.getRemoteAddr());
            }
        }
    }

    /**
     * write remote host name - %h
     */
    protected class HostElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
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
            buf.append(value);
        }
    }

    /**
     * write remote logical username from identd (always returns '-') - %l
     */
    protected static class LogicalUserNameElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            buf.append('-');
        }
    }

    /**
     * write request protocol - %H
     */
    protected class ProtocolElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
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
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (request != null) {
                String value = request.getRemoteUser();
                if (value != null) {
                    buf.append(value);
                } else {
                    buf.append('-');
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * write date and time, in configurable format (default CLF) - %t or %t{format}
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
         * The patterns we use to replace "S" and "SSS" millisecond
         * formatting of SimpleDateFormat by our own handling
         */
        private static final String msecPattern = "{#}";
        private static final String trippleMsecPattern =
            msecPattern + msecPattern + msecPattern;

        /* Our format description string, null if CLF */
        private final String format;
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
         * Replace the millisecond formatting character 'S' by
         * some dummy characters in order to make the resulting
         * formatted time stamps cacheable. We replace the dummy
         * chars later with the actual milliseconds because that's
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

        protected DateAndTimeElement(String header) {
            String format = header;
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
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            long timestamp = date.getTime();
            long frac;
            if (usesBegin) {
                timestamp -= time;
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
                    StringBuilder trippleMsec = new StringBuilder(4);
                    if (frac < 100) {
                        if (frac < 10) {
                            trippleMsec.append('0');
                            trippleMsec.append('0');
                        } else {
                            trippleMsec.append('0');
                        }
                    }
                    trippleMsec.append(frac);
                    temp = temp.replace(trippleMsecPattern, trippleMsec);
                    temp = temp.replace(msecPattern, Long.toString(frac));
                }
                buf.append(temp);
                break;
            }
        }
    }

    /**
     * write first line of the request (method and request URI) - %r
     */
    protected static class RequestElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
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
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (response != null) {
                // This approach is used to reduce GC from toString conversion
                int status = response.getStatus();
                if (100 <= status && status < 1000) {
                    buf.append((char) ('0' + (status / 100)))
                            .append((char) ('0' + ((status / 10) % 10)))
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
    protected class PortElement implements AccessLogElement {

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
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
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
    }

    /**
     * write bytes sent, excluding HTTP headers - %b, %B
     */
    protected static class ByteSentElement implements AccessLogElement {
        private final boolean conversion;

        /**
         * if conversion is true, write '-' instead of 0 - %b
         */
        public ByteSentElement(boolean conversion) {
            this.conversion = conversion;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            // Don't need to flush since trigger for log message is after the
            // response has been committed
            long length = response.getBytesWritten(false);
            if (length <= 0) {
                // Protect against nulls and unexpected types as these values
                // may be set by untrusted applications
                Object start = request.getAttribute(
                        Globals.SENDFILE_FILE_START_ATTR);
                if (start instanceof Long) {
                    Object end = request.getAttribute(
                            Globals.SENDFILE_FILE_END_ATTR);
                    if (end instanceof Long) {
                        length = ((Long) end).longValue() -
                                ((Long) start).longValue();
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
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (request != null) {
                buf.append(request.getMethod());
            }
        }
    }

    /**
     * write time taken to process the request - %D, %T
     */
    protected static class ElapsedTimeElement implements AccessLogElement {
        private final boolean millis;

        /**
         * if millis is true, write time in millis - %D
         * if millis is false, write time in seconds - %T
         */
        public ElapsedTimeElement(boolean millis) {
            this.millis = millis;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (millis) {
                buf.append(Long.toString(time));
            } else {
                // second
                buf.append(Long.toString(time / 1000));
                buf.append('.');
                int remains = (int) (time % 1000);
                buf.append(Long.toString(remains / 100));
                remains = remains % 100;
                buf.append(Long.toString(remains / 10));
                buf.append(Long.toString(remains % 10));
            }
        }
    }

    /**
     * write time until first byte is written (commit time) in millis - %F
     */
    protected static class FirstByteTimeElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request, Response response, long time) {
            long commitTime = response.getCoyoteResponse().getCommitTime();
            if (commitTime == -1) {
                buf.append('-');
            } else {
                long delta = commitTime - request.getCoyoteRequest().getStartTime();
                buf.append(Long.toString(delta));
            }
        }
    }

    /**
     * write Query string (prepended with a '?' if it exists) - %q
     */
    protected static class QueryElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
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
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
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
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
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
    protected static class LocalServerNameElement implements AccessLogElement {
        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            buf.append(request.getServerName());
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
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
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
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            Enumeration<String> iter = request.getHeaders(header);
            if (iter.hasMoreElements()) {
                buf.append(iter.nextElement());
                while (iter.hasMoreElements()) {
                    buf.append(',').append(iter.nextElement());
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
        private final String header;

        public CookieElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            String value = "-";
            Cookie[] c = request.getCookies();
            if (c != null) {
                for (int i = 0; i < c.length; i++) {
                    if (header.equals(c[i].getName())) {
                        value = c[i].getValue();
                        break;
                    }
                }
            }
            buf.append(value);
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
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            if (null != response) {
                Iterator<String> iter = response.getHeaders(header).iterator();
                if (iter.hasNext()) {
                    buf.append(iter.next());
                    while (iter.hasNext()) {
                        buf.append(',').append(iter.next());
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
        private final String header;

        public RequestAttributeElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            Object value = null;
            if (request != null) {
                value = request.getAttribute(header);
            } else {
                value = "??";
            }
            if (value != null) {
                if (value instanceof String) {
                    buf.append((String) value);
                } else {
                    buf.append(value.toString());
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
        private final String header;

        public SessionAttributeElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(CharArrayWriter buf, Date date, Request request,
                Response response, long time) {
            Object value = null;
            if (null != request) {
                HttpSession sess = request.getSession(false);
                if (null != sess) {
                    value = sess.getAttribute(header);
                }
            } else {
                value = "??";
            }
            if (value != null) {
                if (value instanceof String) {
                    buf.append((String) value);
                } else {
                    buf.append(value.toString());
                }
            } else {
                buf.append('-');
            }
        }
    }


    /**
     * parse pattern string and create the array of AccessLogElement
     */
    protected AccessLogElement[] createLogElements() {
        List<AccessLogElement> list = new ArrayList<>();
        boolean replace = false;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (replace) {
                /*
                 * For code that processes {, the behavior will be ... if I do
                 * not encounter a closing } - then I ignore the {
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
                        list.add(createAccessLogElement(name.toString(),
                                pattern.charAt(j)));
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

    /**
     * create an AccessLogElement implementation which needs an element name
     */
    protected AccessLogElement createAccessLogElement(String name, char pattern) {
        switch (pattern) {
        case 'i':
            return new HeaderElement(name);
        case 'c':
            return new CookieElement(name);
        case 'o':
            return new ResponseHeaderElement(name);
        case 'p':
            return new PortElement(name);
        case 'r':
            return new RequestAttributeElement(name);
        case 's':
            return new SessionAttributeElement(name);
        case 't':
            return new DateAndTimeElement(name);
        default:
            return new StringElement("???");
        }
    }

    /**
     * create an AccessLogElement implementation
     */
    protected AccessLogElement createAccessLogElement(char pattern) {
        switch (pattern) {
        case 'a':
            return new RemoteAddrElement();
        case 'A':
            return new LocalAddrElement();
        case 'b':
            return new ByteSentElement(true);
        case 'B':
            return new ByteSentElement(false);
        case 'D':
            return new ElapsedTimeElement(true);
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
            return new ElapsedTimeElement(false);
        case 'u':
            return new UserElement();
        case 'U':
            return new RequestURIElement();
        case 'v':
            return new LocalServerNameElement();
        case 'I':
            return new ThreadNameElement();
        default:
            return new StringElement("???" + pattern + "???");
        }
    }
}
