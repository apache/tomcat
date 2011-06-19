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


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;


/**
 * <p>Implementation of the <b>Valve</b> interface that generates a web server
 * access log with the detailed line contents matching a configurable pattern.
 * The syntax of the available patterns is similar to that supported by the
 * Apache <code>mod_log_config</code> module.  As an additional feature,
 * automatic rollover of log files when the date changes is also supported.</p>
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
 * <li><b>%h</b> - Remote host name
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
 * It is modeled after the apache syntax:
 * <ul>
 * <li><code>%{xxx}i</code> for incoming headers
 * <li><code>%{xxx}o</code> for outgoing response headers
 * <li><code>%{xxx}c</code> for a specific cookie
 * <li><code>%{xxx}r</code> xxx is an attribute in the ServletRequest
 * <li><code>%{xxx}s</code> xxx is an attribute in the HttpSession
 * </ul>
 * </p>
 *
 * <p>
 * Conditional logging is also supported. This can be done with the
 * <code>condition</code> property.
 * If the value returned from ServletRequest.getAttribute(condition)
 * yields a non-null value. The logging will be skipped.
 * </p>
 *
 * @author Craig R. McClanahan
 * @author Jason Brittain
 * @author Remy Maucherat
 * @author Takayuki Kaneko
 * @author Peter Rossbach
 * 
 * @version $Id$
 */

public class AccessLogValve extends ValveBase implements AccessLog {

    private static final Log log = LogFactory.getLog(AccessLogValve.class);

    //------------------------------------------------------ Constructor
    public AccessLogValve() {
        super(true);
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * The as-of date for the currently open log file, or a zero-length
     * string if there is no open log file.
     */
    private volatile String dateStamp = "";


    /**
     * The directory in which log files are created.
     */
    private String directory = "logs";


    /**
     * The descriptive information about this implementation.
     */
    protected static final String info =
        "org.apache.catalina.valves.AccessLogValve/2.1";


    /**
     * The set of month abbreviations for log messages.
     */
    protected static final String months[] =
    { "Jan", "Feb", "Mar", "Apr", "May", "Jun",
      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };


    /**
     * enabled this component
     */
    protected boolean enabled = true;

    /**
     * The pattern used to format our access log lines.
     */
    protected String pattern = null;


    /**
     * The prefix that is added to log file filenames.
     */
    protected String prefix = "access_log.";


    /**
     * Should we rotate our log file? Default is true (like old behavior)
     */
    protected boolean rotatable = true;


    /**
     * Buffered logging.
     */
    private boolean buffered = true;


    /**
     * The suffix that is added to log file filenames.
     */
    protected String suffix = "";


    /**
     * The PrintWriter to which we are currently logging, if any.
     */
    protected PrintWriter writer = null;


    /**
     * A date formatter to format a Date into a date in the format
     * "yyyy-MM-dd".
     */
    protected SimpleDateFormat fileDateFormatter = null;


    /**
     * The system timezone.
     */
    private static final TimeZone timezone;

    
    /**
     * The time zone offset relative to GMT in text form when daylight saving
     * is not in operation.
     */
    private static final String timeZoneNoDST;


    /**
     * The time zone offset relative to GMT in text form when daylight saving
     * is in operation.
     */
    private static final String timeZoneDST;
    
    
    /**
     * The current log file we are writing to. Helpful when checkExists
     * is true.
     */
    protected File currentLogFile = null;
    private static class AccessDateStruct {
        private Date currentDate = new Date();
        private String currentDateString = null;
        private SimpleDateFormat dayFormatter = new SimpleDateFormat("dd");
        private SimpleDateFormat monthFormatter = new SimpleDateFormat("MM");
        private SimpleDateFormat yearFormatter = new SimpleDateFormat("yyyy");
        private SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");
        public AccessDateStruct() {
            TimeZone tz = TimeZone.getDefault();
            dayFormatter.setTimeZone(tz);
            monthFormatter.setTimeZone(tz);
            yearFormatter.setTimeZone(tz);
            timeFormatter.setTimeZone(tz);
        }
    }
    
    /**
     * The system time when we last updated the Date that this valve
     * uses for log lines.
     */
    private static final ThreadLocal<AccessDateStruct> currentDateStruct =
            new ThreadLocal<AccessDateStruct>() {
        @Override
        protected AccessDateStruct initialValue() {
            return new AccessDateStruct();
        }
    };
    /**
     * Resolve hosts.
     */
    private boolean resolveHosts = false;


    /**
     * Instant when the log daily rotation was last checked.
     */
    private volatile long rotationLastChecked = 0L;

    /**
     * Do we check for log file existence? Helpful if an external
     * agent renames the log file so we can automagically recreate it.
     */
    private boolean checkExists = false;
    
    
    /**
     * Are we doing conditional logging. default false.
     */
    protected String condition = null;


    /**
     * Date format to place in log file name. Use at your own risk!
     */
    protected String fileDateFormat = null;
    
    /**
     * Array of AccessLogElement, they will be used to make log message.
     */
    protected AccessLogElement[] logElements = null;

    /**
     * @see #setRequestAttributesEnabled(boolean)
     */
    protected boolean requestAttributesEnabled = false;

    // ------------------------------------------------------------- Properties

    /**
     * @return Returns the enabled.
     */
    public boolean getEnabled() {
        return enabled;
    }

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
     * @param enabled
     *            The enabled to set.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Return the directory in which we create log files.
     */
    public String getDirectory() {
        return (directory);
    }


    /**
     * Set the directory in which we create log files.
     *
     * @param directory The new log file directory
     */
    public void setDirectory(String directory) {
        this.directory = directory;
    }


    /**
     * Return descriptive information about this implementation.
     */
    @Override
    public String getInfo() {
        return (info);
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
        if (pattern == null)
            this.pattern = "";
        else if (pattern.equals(Constants.AccessLog.COMMON_ALIAS))
            this.pattern = Constants.AccessLog.COMMON_PATTERN;
        else if (pattern.equals(Constants.AccessLog.COMBINED_ALIAS))
            this.pattern = Constants.AccessLog.COMBINED_PATTERN;
        else
            this.pattern = pattern;
        logElements = createLogElements();
    }


    /**
     * Check for file existence before logging.
     */
    public boolean isCheckExists() {

        return checkExists;

    }


    /**
     * Set whether to check for log file existence before logging.
     *
     * @param checkExists true meaning to check for file existence.
     */
    public void setCheckExists(boolean checkExists) {

        this.checkExists = checkExists;

    }
    
    
    /**
     * Return the log file prefix.
     */
    public String getPrefix() {
        return (prefix);
    }


    /**
     * Set the log file prefix.
     *
     * @param prefix The new log file prefix
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }


    /**
     * Should we rotate the logs
     */
    public boolean isRotatable() {
        return rotatable;
    }


    /**
     * Set the value is we should we rotate the logs
     *
     * @param rotatable true is we should rotate.
     */
    public void setRotatable(boolean rotatable) {
        this.rotatable = rotatable;
    }


    /**
     * Is the logging buffered
     */
    public boolean isBuffered() {
        return buffered;
    }


    /**
     * Set the value if the logging should be buffered
     *
     * @param buffered true if buffered.
     */
    public void setBuffered(boolean buffered) {
        this.buffered = buffered;
    }


    /**
     * Return the log file suffix.
     */
    public String getSuffix() {
        return (suffix);
    }


    /**
     * Set the log file suffix.
     *
     * @param suffix The new log file suffix
     */
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }


    /**
     * Set the resolve hosts flag.
     *
     * @param resolveHosts The new resolve hosts value
     */
    public void setResolveHosts(boolean resolveHosts) {
        this.resolveHosts = resolveHosts;
    }


    /**
     * Get the value of the resolve hosts flag.
     */
    public boolean isResolveHosts() {
        return resolveHosts;
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
     *  Return the date format date based log rotation.
     */
    public String getFileDateFormat() {
        return fileDateFormat;
    }


    /**
     *  Set the date format date based log rotation.
     */
    public void setFileDateFormat(String fileDateFormat) {
        this.fileDateFormat =  fileDateFormat;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     */
    @Override
    public synchronized void backgroundProcess() {
        if (getState().isAvailable() && getEnabled() && writer != null &&
                buffered) {
            writer.flush();
        }
    }    

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
        if (!getState().isAvailable() || !getEnabled() ||
                logElements == null || condition != null
                && null != request.getRequest().getAttribute(condition)) {
            return;
        }

        Date date = getDate();
        StringBuilder result = new StringBuilder(128);

        for (int i = 0; i < logElements.length; i++) {
            logElements[i].addElement(result, date, request, response, time);
        }

        log(result.toString());
    }


    /**
     * Rename the existing log file to something else. Then open the
     * old log file name up once again. Intended to be called by a JMX
     * agent.
     *
     *
     * @param newFileName The file name to move the log file entry to
     * @return true if a file was rotated with no error
     */
    public synchronized boolean rotate(String newFileName) {

        if (currentLogFile != null) {
            File holder = currentLogFile;
            close();
            try {
                holder.renameTo(new File(newFileName));
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                log.error(sm.getString("accessLogValve.rotateFail"), e);
            }

            /* Make sure date is correct */
            dateStamp = fileDateFormatter.format(
                    new Date(System.currentTimeMillis()));

            open();
            return true;
        } else {
            return false;
        }

    }

    // -------------------------------------------------------- Private Methods


    /**
     * Close the currently open log file (if any)
     */
    private synchronized void close() {
        if (writer == null) {
            return;
        }
        writer.flush();
        writer.close();
        writer = null;
        dateStamp = "";
        currentLogFile = null;
    }


    /**
     * Log the specified message to the log file, switching files if the date
     * has changed since the previous log call.
     *
     * @param message Message to be logged
     */
    public void log(String message) {
        if (rotatable) {
            // Only do a logfile switch check once a second, max.
            long systime = System.currentTimeMillis();
            if ((systime - rotationLastChecked) > 1000) {
                synchronized(this) {
                    if ((systime - rotationLastChecked) > 1000) {
                        rotationLastChecked = systime;
    
                        String tsDate;
                        // Check for a change of date
                        tsDate = fileDateFormatter.format(new Date(systime));
    
                        // If the date has changed, switch log files
                        if (!dateStamp.equals(tsDate)) {
                            close();
                            dateStamp = tsDate;
                            open();
                        }
                    }
                }
            }
        }
        
        /* In case something external rotated the file instead */
        if (checkExists) {
            synchronized (this) {
                if (currentLogFile != null && !currentLogFile.exists()) {
                    try {
                        close();
                    } catch (Throwable e) {
                        ExceptionUtils.handleThrowable(e);
                        log.info(sm.getString("accessLogValve.closeFail"), e);
                    }

                    /* Make sure date is correct */
                    dateStamp = fileDateFormatter.format(
                            new Date(System.currentTimeMillis()));

                    open();
                }
            }
        }

        // Log this message
        synchronized(this) {
            if (writer != null) {
                writer.println(message);
                if (!buffered) {
                    writer.flush();
                }
            }
        }

    }


    /**
     * Return the month abbreviation for the specified month, which must
     * be a two-digit String.
     *
     * @param month Month number ("01" .. "12").
     */
    private static String lookup(String month) {
        int index;
        try {
            index = Integer.parseInt(month) - 1;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            index = 0;  // Can not happen, in theory
        }
        return (months[index]);
    }


    /**
     * Open the new log file for the date specified by <code>dateStamp</code>.
     */
    protected synchronized void open() {
        // Create the directory if necessary
        File dir = new File(directory);
        if (!dir.isAbsolute())
            dir = new File(System.getProperty(Globals.CATALINA_BASE_PROP), directory);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                log.error(sm.getString("accessLogValve.openDirFail", dir));
            }
        }

        // Open the current log file
        try {
            String pathname;
            // If no rotate - no need for dateStamp in fileName
            if (rotatable) {
                pathname = dir.getAbsolutePath() + File.separator + prefix
                        + dateStamp + suffix;
            } else {
                pathname = dir.getAbsolutePath() + File.separator + prefix
                        + suffix;
            }
            writer = new PrintWriter(new BufferedWriter(new FileWriter(
                    pathname, true), 128000), false);
            
            currentLogFile = new File(pathname);
        } catch (IOException e) {
            writer = null;
            currentLogFile = null;
        }
    }
 
    /**
     * This method returns a Date object that is accurate to within one second.
     * If a thread calls this method to get a Date and it's been less than 1
     * second since a new Date was created, this method simply gives out the
     * same Date again so that the system doesn't spend time creating Date
     * objects unnecessarily.
     * 
     * @return Date
     */
    private Date getDate() {
        // Only create a new Date once per second, max.
        long systime = System.currentTimeMillis();
        AccessDateStruct struct = currentDateStruct.get(); 
        if ((systime - struct.currentDate.getTime()) > 1000) {
            struct.currentDate.setTime(systime);
            struct.currentDateString = null;
        }
        return struct.currentDate;
    }


    private static String getTimeZone(Date date) {
        if (timezone.inDaylightTime(date)) {
            return timeZoneDST;
        } else {
            return timeZoneNoDST;
        }
    }
    
    
    private static String calculateTimeZoneOffset(long offset) {
        StringBuilder tz = new StringBuilder();
        if ((offset < 0)) {
            tz.append("-");
            offset = -offset;
        } else {
            tz.append("+");
        }

        long hourOffset = offset / (1000 * 60 * 60);
        long minuteOffset = (offset / (1000 * 60)) % 60;

        if (hourOffset < 10)
            tz.append("0");
        tz.append(hourOffset);

        if (minuteOffset < 10)
            tz.append("0");
        tz.append(minuteOffset);

        return tz.toString();
    }

    static {
        // Initialize the timeZone
        timezone = TimeZone.getDefault();
        timeZoneNoDST = calculateTimeZoneOffset(timezone.getRawOffset());
        int offset = timezone.getDSTSavings();
        timeZoneDST = calculateTimeZoneOffset(timezone.getRawOffset() + offset);
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

        // Initialize the Date formatters
        String format = getFileDateFormat();
        if (format == null || format.length() == 0) {
            format = "yyyy-MM-dd";
            setFileDateFormat(format);
        }
        fileDateFormatter = new SimpleDateFormat(format);
        fileDateFormatter.setTimeZone(timezone);
        dateStamp = fileDateFormatter.format(currentDateStruct.get().currentDate);
        open();
        
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
        close();
    }
    
    /**
     * AccessLogElement writes the partial message into the buffer.
     */
    protected interface AccessLogElement {
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time);

    }
    
    /**
     * write thread name - %I
     */
    protected static class ThreadNameElement implements AccessLogElement {
        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
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
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            buf.append(LOCAL_ADDR_VALUE);
        }
    }
    
    /**
     * write remote IP address - %a
     */
    protected class RemoteAddrElement implements AccessLogElement {
        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            if (requestAttributesEnabled) {
                Object addr = request.getAttribute(REMOTE_ADDR_ATTRIBUTE);
                if (addr == null) {
                    buf.append(request.getRemoteAddr());
                } else {
                    buf.append(addr);
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
        public void addElement(StringBuilder buf, Date date, Request request,
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
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            buf.append('-');
        }
    }
    
    /**
     * write request protocol - %H
     */
    protected class ProtocolElement implements AccessLogElement {
        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            if (requestAttributesEnabled) {
                Object proto = request.getAttribute(PROTOCOL_ATTRIBUTE);
                if (proto == null) {
                    buf.append(request.getProtocol());
                } else {
                    buf.append(proto);
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
        public void addElement(StringBuilder buf, Date date, Request request,
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
     * write date and time, in Common Log Format - %t
     */
    protected class DateAndTimeElement implements AccessLogElement {


        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            AccessDateStruct struct = currentDateStruct.get();
            if (struct.currentDateString == null) {
                StringBuilder current = new StringBuilder(32);
                current.append('[');
                current.append(struct.dayFormatter.format(date));
                current.append('/');
                current.append(lookup(struct.monthFormatter.format(date)));
                current.append('/');
                current.append(struct.yearFormatter.format(date));
                current.append(':');
                current.append(struct.timeFormatter.format(date));
                current.append(' ');
                current.append(getTimeZone(date));
                current.append(']');
                struct.currentDateString = current.toString();
            }
            buf.append(struct.currentDateString);
        }
    }

    /**
     * write first line of the request (method and request URI) - %r
     */
    protected static class RequestElement implements AccessLogElement {
        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
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
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            if (response != null) {
                buf.append(response.getStatus());
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * write local port on which this request was received - %p
     */
    protected class LocalPortElement implements AccessLogElement {
        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            if (requestAttributesEnabled) {
                Object port = request.getAttribute(SERVER_PORT_ATTRIBUTE);
                if (port == null) {
                    buf.append(request.getServerPort());
                } else {
                    buf.append(port);
                }
            } else {
                buf.append(request.getServerPort());
            }
        }
    }

    /**
     * write bytes sent, excluding HTTP headers - %b, %B
     */
    protected static class ByteSentElement implements AccessLogElement {
        private boolean conversion;

        /**
         * if conversion is true, write '-' instead of 0 - %b
         */
        public ByteSentElement(boolean conversion) {
            this.conversion = conversion;
        }

        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            // Don't need to flush since trigger for log message is after the
            // response has been committed
            long length = response.getBytesWritten(false);
            if (length <= 0 && conversion) {
                buf.append('-');
            } else {
                buf.append(length);
            }
        }
    }

    /**
     * write request method (GET, POST, etc.) - %m
     */
    protected static class MethodElement implements AccessLogElement {
        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
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
        private boolean millis;

        /**
         * if millis is true, write time in millis - %D
         * if millis is false, write time in seconds - %T
         */
        public ElapsedTimeElement(boolean millis) {
            this.millis = millis;
        }

        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            if (millis) {
                buf.append(time);
            } else {
                // second
                buf.append(time / 1000);
                buf.append('.');
                int remains = (int) (time % 1000);
                buf.append(remains / 100);
                remains = remains % 100;
                buf.append(remains / 10);
                buf.append(remains % 10);
            }
        }
    }
    
    /**
     * write Query string (prepended with a '?' if it exists) - %q
     */
    protected static class QueryElement implements AccessLogElement {
        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            String query = null;
            if (request != null)
                query = request.getQueryString();
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
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            if (request != null) {
                if (request.getSession(false) != null) {
                    buf.append(request.getSessionInternal(false)
                            .getIdInternal());
                } else {
                    buf.append('-');
                }
            } else {
                buf.append('-');
            }
        }
    }

    /**
     * write requested URL path - %U
     */
    protected static class RequestURIElement implements AccessLogElement {
        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
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
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            buf.append(request.getServerName());
        }
    }
    
    /**
     * write any string
     */
    protected static class StringElement implements AccessLogElement {
        private String str;

        public StringElement(String str) {
            this.str = str;
        }

        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            buf.append(str);
        }
    }

    /**
     * write incoming headers - %{xxx}i
     */
    protected static class HeaderElement implements AccessLogElement {
        private String header;

        public HeaderElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
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
        private String header;

        public CookieElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
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
        private String header;

        public ResponseHeaderElement(String header) {
            this.header = header;
        }
        
        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
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
        private String header;

        public RequestAttributeElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
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
        private String header;

        public SessionAttributeElement(String header) {
            this.header = header;
        }

        @Override
        public void addElement(StringBuilder buf, Date date, Request request,
                Response response, long time) {
            Object value = null;
            if (null != request) {
                HttpSession sess = request.getSession(false);
                if (null != sess)
                    value = sess.getAttribute(header);
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
        List<AccessLogElement> list = new ArrayList<AccessLogElement>();
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
     * create an AccessLogElement implementation which needs header string
     */
    private AccessLogElement createAccessLogElement(String header, char pattern) {
        switch (pattern) {
        case 'i':
            return new HeaderElement(header);
        case 'c':
            return new CookieElement(header);
        case 'o':
            return new ResponseHeaderElement(header);
        case 'r':
            return new RequestAttributeElement(header);
        case 's':
            return new SessionAttributeElement(header);            
        default:
            return new StringElement("???");
        }
    }

    /**
     * create an AccessLogElement implementation
     */
    private AccessLogElement createAccessLogElement(char pattern) {
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
        case 'h':
            return new HostElement();
        case 'H':
            return new ProtocolElement();
        case 'l':
            return new LogicalUserNameElement();
        case 'm':
            return new MethodElement();
        case 'p':
            return new LocalPortElement();
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
