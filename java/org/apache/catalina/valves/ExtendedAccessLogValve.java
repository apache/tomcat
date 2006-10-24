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


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;



/**
 * An implementation of the W3c Extended Log File Format. See
 * http://www.w3.org/TR/WD-logfile.html for more information about the format.
 *
 * The following fields are supported:
 * <ul>
 * <li><code>c-dns</code>:  Client hostname</li>
 * <li><code>c-ip</code>:  Client ip address</li>
 * <li><code>bytes</code>:  bytes served</li>
 * <li><code>cs-method</code>:  request method</li>
 * <li><code>cs-uri</code>:  The full uri requested</li>
 * <li><code>cs-uri-query</code>:  The query string</li>
 * <li><code>cs-uri-stem</code>:  The uri without query string</li>
 * <li><code>date</code>:  The date in yyyy-mm-dd  format for GMT</li>
 * <li><code>s-dns</code>: The server dns entry </li>
 * <li><code>s-ip</code>:  The server ip address</li>
 * <li><code>cs(XXX)</code>:  The value of header XXX from client to server</li>
 * <li><code>sc(XXX)</code>: The value of header XXX from server to client </li>
 * <li><code>sc-status</code>:  The status code</li>
 * <li><code>time</code>:  Time the request was served</li>
 * <li><code>time-taken</code>:  Time (in seconds) taken to serve the request</li>
 * <li><code>x-A(XXX)</code>: Pull XXX attribute from the servlet context </li>
 * <li><code>x-C(XXX)</code>: Pull the first cookie of the name XXX </li>
 * <li><code>x-R(XXX)</code>: Pull XXX attribute from the servlet request </li>
 * <li><code>x-S(XXX)</code>: Pull XXX attribute from the session </li>
 * <li><code>x-P(...)</code>:  Call request.getParameter(...)
 *                             and URLencode it. Helpful to capture
 *                             certain POST parameters.
 * </li>
 * <li>For any of the x-H(...) the following method will be called from the
 *                HttpServletRequestObject </li>
 * <li><code>x-H(authType)</code>: getAuthType </li>
 * <li><code>x-H(characterEncoding)</code>: getCharacterEncoding </li>
 * <li><code>x-H(contentLength)</code>: getContentLength </li>
 * <li><code>x-H(locale)</code>:  getLocale</li>
 * <li><code>x-H(protocol)</code>: getProtocol </li>
 * <li><code>x-H(remoteUser)</code>:  getRemoteUser</li>
 * <li><code>x-H(requestedSessionId)</code>: getGequestedSessionId</li>
 * <li><code>x-H(requestedSessionIdFromCookie)</code>:
 *                  isRequestedSessionIdFromCookie </li>
 * <li><code>x-H(requestedSessionIdValid)</code>:
 *                  isRequestedSessionIdValid</li>
 * <li><code>x-H(scheme)</code>:  getScheme</li>
 * <li><code>x-H(secure)</code>:  isSecure</li>
 * </ul>
 *
 *
 *
 * <p>
 * Log rotation can be on or off. This is dictated by the rotatable
 * property.
 * </p>
 *
 * <p>
 * For UNIX users, another field called <code>checkExists</code>is also
 * available. If set to true, the log file's existence will be checked before
 * each logging. This way an external log rotator can move the file
 * somewhere and tomcat will start with a new file.
 * </p>
 *
 * <p>
 * For JMX junkies, a public method called </code>rotate</code> has
 * been made available to allow you to tell this instance to move
 * the existing log file to somewhere else start writing a new log file.
 * </p>
 *
 * <p>
 * Conditional logging is also supported. This can be done with the
 * <code>condition</code> property.
 * If the value returned from ServletRequest.getAttribute(condition)
 * yields a non-null value. The logging will be skipped.
 * </p>
 *
 * <p>
 * For extended attributes coming from a getAttribute() call,
 * it is you responsibility to ensure there are no newline or
 * control characters.
 * </p>
 *
 *
 * @author Tim Funk
 * @version $Revision$ $Date$
 */

public class ExtendedAccessLogValve
    extends ValveBase
    implements Lifecycle {


    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new instance of this class with default property values.
     */
    public ExtendedAccessLogValve() {

        super();


    }


    // ----------------------------------------------------- Instance Variables
    private static Log log = LogFactory.getLog(ExtendedAccessLogValve.class);


    /**
     * The descriptive information about this implementation.
     */
    protected static final String info =
        "org.apache.catalina.valves.ExtendedAccessLogValve/1.0";


    /**
     * The lifecycle event support for this component.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);



    /**
     * The string manager for this package.
     */
    private StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * Has this component been started yet?
     */
    private boolean started = false;


    /**
     * The as-of date for the currently open log file, or a zero-length
     * string if there is no open log file.
     */
    private String dateStamp = "";


    /**
     * The PrintWriter to which we are currently logging, if any.
     */
    private PrintWriter writer = null;


    /**
     * The formatter for the date contained in the file name.
     */
    private SimpleDateFormat fileDateFormatter = null;


    /**
     * A date formatter to format a Date into a date in the format
     * "yyyy-MM-dd".
     */
    private SimpleDateFormat dateFormatter = null;


    /**
     * A date formatter to format a Date into a time in the format
     * "kk:mm:ss" (kk is a 24-hour representation of the hour).
     */
    private SimpleDateFormat timeFormatter = null;


    /**
     * Time taken formatter for 3 decimal places.
     */
     private DecimalFormat timeTakenFormatter = null;


    /**
     * My ip address. Look it up once and remember it. Dump this if we can
     * determine another reliable way to get server ip address since this
     * server may have many ip's.
     */
    private String myIpAddress = null;


    /**
     * My dns name. Look it up once and remember it. Dump this if we can
     * determine another reliable way to get server name address since this
     * server may have many ip's.
     */
    private String myDNSName = null;


    /**
     * Holder for all of the fields to log after the pattern is decoded.
     */
    private FieldInfo[] fieldInfos;


    /**
     * The current log file we are writing to. Helpful when checkExists
     * is true.
     */
    private File currentLogFile = null;



    /**
     * The system time when we last updated the Date that this valve
     * uses for log lines.
     */
    private Date currentDate = null;


    /**
     * Instant when the log daily rotation was last checked.
     */
    private long rotationLastChecked = 0L;


    /**
     * The directory in which log files are created.
     */
    private String directory = "logs";


    /**
     * The pattern used to format our access log lines.
     */
    private String pattern = null;


    /**
     * The prefix that is added to log file filenames.
     */
    private String prefix = "access_log.";


    /**
     * Should we rotate our log file? Default is true (like old behavior)
     */
    private boolean rotatable = true;


    /**
     * The suffix that is added to log file filenames.
     */
    private String suffix = "";


    /**
     * Are we doing conditional logging. default false.
     */
    private String condition = null;


    /**
     * Do we check for log file existence? Helpful if an external
     * agent renames the log file so we can automagically recreate it.
     */
    private boolean checkExists = false;


    /**
     * Date format to place in log file name. Use at your own risk!
     */
    private String fileDateFormat = null;


    // ------------------------------------------------------------- Properties


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
     * @param pattern The new pattern pattern
     */
    public void setPattern(String pattern) {

        FieldInfo[] f= decodePattern(pattern);
        if (f!=null) {
            this.pattern = pattern;
            this.fieldInfos = f;
        }
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
     * Return true if logs are automatically rotated.
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
     * Return whether the attribute name to look for when
     * performing conditional loggging. If null, every
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
     * Log a message summarizing the specified request and response, according
     * to the format specified by the <code>pattern</code> property.
     *
     * @param request Request being processed
     * @param response Response being processed
     *
     * @exception IOException if an input/output error has occurred
     * @exception ServletException if a servlet error has occurred
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Pass this request on to the next valve in our pipeline
        long endTime;
        long runTime;
        long startTime=System.currentTimeMillis();

        getNext().invoke(request, response);

        endTime = System.currentTimeMillis();
        runTime = endTime-startTime;

        if (fieldInfos==null || condition!=null &&
              null!=request.getRequest().getAttribute(condition)) {
            return;
        }


        Date date = getDate(endTime);
        StringBuffer result = new StringBuffer();

        for (int i=0; fieldInfos!=null && i<fieldInfos.length; i++) {
            switch(fieldInfos[i].type) {
                case FieldInfo.DATA_CLIENT:
                    if (FieldInfo.FIELD_IP==fieldInfos[i].location)
                        result.append(request.getRequest().getRemoteAddr());
                    else if (FieldInfo.FIELD_DNS==fieldInfos[i].location)
                        result.append(request.getRequest().getRemoteHost());
                    else
                        result.append("?WTF?"); /* This should never happen! */
                    break;
                case FieldInfo.DATA_SERVER:
                    if (FieldInfo.FIELD_IP==fieldInfos[i].location)
                        result.append(myIpAddress);
                    else if (FieldInfo.FIELD_DNS==fieldInfos[i].location)
                        result.append(myDNSName);
                    else
                        result.append("?WTF?"); /* This should never happen! */
                    break;
                case FieldInfo.DATA_REMOTE:
                    result.append('?'); /* I don't know how to handle these! */
                    break;
                case FieldInfo.DATA_CLIENT_TO_SERVER:
                    result.append(getClientToServer(fieldInfos[i], request));
                    break;
                case FieldInfo.DATA_SERVER_TO_CLIENT:
                    result.append(getServerToClient(fieldInfos[i], response));
                    break;
                case FieldInfo.DATA_SERVER_TO_RSERVER:
                    result.append('-');
                    break;
                case FieldInfo.DATA_RSERVER_TO_SERVER:
                    result.append('-');
                    break;
                case FieldInfo.DATA_APP_SPECIFIC:
                    result.append(getAppSpecific(fieldInfos[i], request));
                    break;
                case FieldInfo.DATA_SPECIAL:
                    if (FieldInfo.SPECIAL_DATE==fieldInfos[i].location)
                        result.append(dateFormatter.format(date));
                    else if (FieldInfo.SPECIAL_TIME_TAKEN==fieldInfos[i].location)
                        result.append(timeTakenFormatter.format(runTime/1000d));
                    else if (FieldInfo.SPECIAL_TIME==fieldInfos[i].location)
                        result.append(timeFormatter.format(date));
                    else if (FieldInfo.SPECIAL_BYTES==fieldInfos[i].location) {
                        int length = response.getContentCount();
                        if (length > 0)
                            result.append(length);
                        else
                            result.append("-");
                    } else if (FieldInfo.SPECIAL_CACHED==fieldInfos[i].location)
                        result.append('-'); /* I don't know how to evaluate this! */
                    else
                        result.append("?WTF?"); /* This should never happen! */
                    break;
                default:
                    result.append("?WTF?"); /* This should never happen! */
            }

            if (fieldInfos[i].postWhiteSpace!=null) {
                result.append(fieldInfos[i].postWhiteSpace);
            }
        }
        log(result.toString(), date);

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

        if (currentLogFile!=null) {
            File holder = currentLogFile;
            close();
            try {
                holder.renameTo(new File(newFileName));
            } catch(Throwable e){
                log.error("rotate failed", e);
            }

            /* Make sure date is correct */
            currentDate = new Date(System.currentTimeMillis());
            dateStamp = fileDateFormatter.format(currentDate);

            open();
            return true;
        } else {
            return false;
        }

    }

    // -------------------------------------------------------- Private Methods


    /**
     *  Return the client to server data.
     *  @param fieldInfo The field to decode.
     *  @param request The object we pull data from.
     *  @return The appropriate value.
     */
     private String getClientToServer(FieldInfo fieldInfo, Request request) {

        switch(fieldInfo.location) {
            case FieldInfo.FIELD_METHOD:
                return request.getMethod();
            case FieldInfo.FIELD_URI:
                if (null==request.getQueryString())
                    return request.getRequestURI();
                else
                    return request.getRequestURI() + "?" + request.getQueryString();
            case FieldInfo.FIELD_URI_STEM:
                return request.getRequestURI();
            case FieldInfo.FIELD_URI_QUERY:
                if (null==request.getQueryString())
                    return "-";
                return request.getQueryString();
            case FieldInfo.FIELD_HEADER:
                return wrap(request.getHeader(fieldInfo.value));
            default:
                ;
        }

        return "-";

    }


    /**
     *  Return the server to client data.
     *  @param fieldInfo The field to decode.
     *  @param response The object we pull data from.
     *  @return The appropriate value.
     */
    private String getServerToClient(FieldInfo fieldInfo, Response response) {
        switch(fieldInfo.location) {
            case FieldInfo.FIELD_STATUS:
                return "" + response.getStatus();
            case FieldInfo.FIELD_COMMENT:
                return "?"; /* Not coded yet*/
            case FieldInfo.FIELD_HEADER:
                return wrap(response.getHeader(fieldInfo.value));
            default:
                ;
        }

        return "-";

    }


    /**
     * Get app specific data.
     * @param fieldInfo The field to decode
     * @param request Where we will pull the data from.
     * @return The appropriate value
     */
    private String getAppSpecific(FieldInfo fieldInfo, Request request) {

        switch(fieldInfo.xType) {
            case FieldInfo.X_PARAMETER:
                return wrap(urlEncode(request.getParameter(fieldInfo.value)));
            case FieldInfo.X_REQUEST:
                return wrap(request.getAttribute(fieldInfo.value));
            case FieldInfo.X_SESSION:
                HttpSession session = null;
                if (request!=null){
                    session = request.getSession(false);
                    if (session!=null)
                        return wrap(session.getAttribute(fieldInfo.value));
                }
                break;
            case FieldInfo.X_COOKIE:
                Cookie[] c = request.getCookies();
                for (int i=0; c != null && i < c.length; i++){
                    if (fieldInfo.value.equals(c[i].getName())){
                        return wrap(c[i].getValue());
                    }
                 }
            case FieldInfo.X_APP:
                return wrap(request.getContext().getServletContext()
                                .getAttribute(fieldInfo.value));
            case FieldInfo.X_SERVLET_REQUEST:
                if (fieldInfo.location==FieldInfo.X_LOC_AUTHTYPE) {
                    return wrap(request.getAuthType());
                } else if (fieldInfo.location==FieldInfo.X_LOC_REMOTEUSER) {
                    return wrap(request.getRemoteUser());
                } else if (fieldInfo.location==
                            FieldInfo.X_LOC_REQUESTEDSESSIONID) {
                    return wrap(request.getRequestedSessionId());
                } else if (fieldInfo.location==
                            FieldInfo.X_LOC_REQUESTEDSESSIONIDFROMCOOKIE) {
                    return wrap(""+request.isRequestedSessionIdFromCookie());
                } else if (fieldInfo.location==
                            FieldInfo.X_LOC_REQUESTEDSESSIONIDVALID) {
                    return wrap(""+request.isRequestedSessionIdValid());
                } else if (fieldInfo.location==FieldInfo.X_LOC_CONTENTLENGTH) {
                    return wrap(""+request.getContentLength());
                } else if (fieldInfo.location==
                            FieldInfo.X_LOC_CHARACTERENCODING) {
                    return wrap(request.getCharacterEncoding());
                } else if (fieldInfo.location==FieldInfo.X_LOC_LOCALE) {
                    return wrap(request.getLocale());
                } else if (fieldInfo.location==FieldInfo.X_LOC_PROTOCOL) {
                    return wrap(request.getProtocol());
                } else if (fieldInfo.location==FieldInfo.X_LOC_SCHEME) {
                    return wrap(request.getScheme());
                } else if (fieldInfo.location==FieldInfo.X_LOC_SECURE) {
                    return wrap(""+request.isSecure());
                }
                break;
            default:
                ;
        }

        return "-";

    }


    /**
     *  urlEncode the given string. If null or empty, return null.
     */
    private String urlEncode(String value) {
        if (null==value || value.length()==0) {
            return null;
        }
        return URLEncoder.encode(value);
    }


    /**
     *  Wrap the incoming value into quotes and escape any inner
     *  quotes with double quotes.
     *
     *  @param value - The value to wrap quotes around
     *  @return '-' if empty of null. Otherwise, toString() will
     *     be called on the object and the value will be wrapped
     *     in quotes and any quotes will be escaped with 2
     *     sets of quotes.
     */
    private String wrap(Object value) {

        String svalue;
        // Does the value contain a " ? If so must encode it
        if (value==null || "-".equals(value))
            return "-";


        try {
            svalue = value.toString();
            if ("".equals(svalue))
                return "-";
        } catch(Throwable e){
            /* Log error */
            return "-";
        }

        /* Wrap all quotes in double quotes. */
        StringBuffer buffer = new StringBuffer(svalue.length()+2);
        buffer.append('"');
        int i=0;
        while (i<svalue.length()) {
            int j = svalue.indexOf('"', i);
            if (j==-1) {
                buffer.append(svalue.substring(i));
                i=svalue.length();
            } else {
                buffer.append(svalue.substring(i, j+1));
                buffer.append('"');
                i=j+2;
            }
        }

        buffer.append('"');
        return buffer.toString();

    }


    /**
     * Close the currently open log file (if any)
     */
    private synchronized void close() {

        if (writer == null)
            return;
        writer.flush();
        writer.close();
        writer = null;
        currentLogFile = null;

    }


    /**
     * Log the specified message to the log file, switching files if the date
     * has changed since the previous log call.
     *
     * @param message Message to be logged
     * @param date the current Date object (so this method doesn't need to
     *        create a new one)
     */
    private void log(String message, Date date) {

        if (rotatable){
            // Only do a logfile switch check once a second, max.
            long systime = System.currentTimeMillis();
            if ((systime - rotationLastChecked) > 1000) {

                // We need a new currentDate
                currentDate = new Date(systime);
                rotationLastChecked = systime;

                // Check for a change of date
                String tsDate = fileDateFormatter.format(currentDate);

                // If the date has changed, switch log files
                if (!dateStamp.equals(tsDate)) {
                    synchronized (this) {
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
        if (checkExists){
            synchronized (this) {
                if (currentLogFile!=null && !currentLogFile.exists()) {
                    try {
                        close();
                    } catch (Throwable e){
                        log.info("at least this wasn't swallowed", e);
                    }

                    /* Make sure date is correct */
                    currentDate = new Date(System.currentTimeMillis());
                    dateStamp = fileDateFormatter.format(currentDate);

                    open();
                }
            }
        }

        // Log this message
        if (writer != null) {
            writer.println(message);
        }

    }


    /**
     * Open the new log file for the date specified by <code>dateStamp</code>.
     */
    private synchronized void open() {

        // Create the directory if necessary
        File dir = new File(directory);
        if (!dir.isAbsolute())
            dir = new File(System.getProperty("catalina.base"), directory);
        dir.mkdirs();

        // Open the current log file
        try {
            String pathname;

            // If no rotate - no need for dateStamp in fileName
            if (rotatable){
                pathname = dir.getAbsolutePath() + File.separator +
                            prefix + dateStamp + suffix;
            } else {
                pathname = dir.getAbsolutePath() + File.separator +
                            prefix + suffix;
            }

            currentLogFile = new File(pathname);
            writer = new PrintWriter(new FileWriter(pathname, true), true);
            if (currentLogFile.length()==0) {
                writer.println("#Fields: " + pattern);
                writer.println("#Version: 1.0");
                writer.println("#Software: " + ServerInfo.getServerInfo());
            }


        } catch (IOException e) {
            writer = null;
            currentLogFile = null;
        }

    }


    /**
     * This method returns a Date object that is accurate to within one
     * second.  If a thread calls this method to get a Date and it's been
     * less than 1 second since a new Date was created, this method
     * simply gives out the same Date again so that the system doesn't
     * spend time creating Date objects unnecessarily.
     */
    private Date getDate(long systime) {
        /* Avoid extra call to System.currentTimeMillis(); */
        if (0==systime) {
            systime = System.currentTimeMillis();
        }

        // Only create a new Date once per second, max.
        if ((systime - currentDate.getTime()) > 1000) {
            currentDate.setTime(systime);
        }

        return currentDate;

    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Add a lifecycle event listener to this component.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {

        lifecycle.addLifecycleListener(listener);

    }


    /**
     * Get the lifecycle listeners associated with this lifecycle. If this
     * Lifecycle has no listeners registered, a zero-length array is returned.
     */
    public LifecycleListener[] findLifecycleListeners() {

        return lifecycle.findLifecycleListeners();

    }


    /**
     * Remove a lifecycle event listener from this component.
     *
     * @param listener The listener to add
     */
    public void removeLifecycleListener(LifecycleListener listener) {

        lifecycle.removeLifecycleListener(listener);

    }


    /**
     * Prepare for the beginning of active use of the public methods of this
     * component.  This method should be called after <code>configure()</code>,
     * and before any of the public methods of the component are utilized.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {

        // Validate and update our current component state
        if (started)
            throw new LifecycleException
                (sm.getString("extendedAccessLogValve.alreadyStarted"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // Initialize the timeZone, Date formatters, and currentDate
        TimeZone tz = TimeZone.getTimeZone("GMT");
        dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
        dateFormatter.setTimeZone(tz);
        timeFormatter = new SimpleDateFormat("HH:mm:ss");
        timeFormatter.setTimeZone(tz);
        currentDate = new Date(System.currentTimeMillis());
        if (fileDateFormat==null || fileDateFormat.length()==0)
            fileDateFormat = "yyyy-MM-dd";
        fileDateFormatter = new SimpleDateFormat(fileDateFormat);
        dateStamp = fileDateFormatter.format(currentDate);
        timeTakenFormatter = new DecimalFormat("0.000");

        /* Everybody say ick ... ick */
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            myIpAddress = inetAddress.getHostAddress();
            myDNSName = inetAddress.getHostName();
        } catch(Throwable e){
            myIpAddress="127.0.0.1";
            myDNSName="localhost";
        }

        open();

    }


    /**
     * Gracefully terminate the active use of the public methods of this
     * component.  This method should be the last one called on a given
     * instance of this component.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString("extendedAccessLogValve.notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        close();

    }


    /**
     * Decode the given pattern. Is public so a pattern may
     * allows to be validated.
     * @param fields The pattern to decode
     * @return null on error.  Otherwise array of decoded fields
     */
    public FieldInfo[] decodePattern(String fields) {

        if (log.isDebugEnabled())
            log.debug("decodePattern, fields=" + fields);

        LinkedList list = new LinkedList();

        //Ignore leading whitespace.
        int i=0;
        for (;i<fields.length() && Character.isWhitespace(fields.charAt(i));i++);

        if (i>=fields.length()) {
            log.info("fields was just empty or whitespace");
            return null;
        }

        int j;
        while(i<fields.length()) {
            if (log.isDebugEnabled())
                log.debug("fields.substring(i)=" + fields.substring(i));

            FieldInfo currentFieldInfo = new FieldInfo();


            if (fields.startsWith("date",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SPECIAL;
                currentFieldInfo.location = FieldInfo.SPECIAL_DATE;
                i+="date".length();
            } else if (fields.startsWith("time-taken",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SPECIAL;
                currentFieldInfo.location = FieldInfo.SPECIAL_TIME_TAKEN;
                i+="time-taken".length();
            } else if (fields.startsWith("time",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SPECIAL;
                currentFieldInfo.location = FieldInfo.SPECIAL_TIME;
                i+="time".length();
            } else if (fields.startsWith("bytes",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SPECIAL;
                currentFieldInfo.location = FieldInfo.SPECIAL_BYTES;
                i+="bytes".length();
            } else if (fields.startsWith("cached",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SPECIAL;
                currentFieldInfo.location = FieldInfo.SPECIAL_CACHED;
                i+="cached".length();
            } else if (fields.startsWith("c-ip",i)) {
                currentFieldInfo.type = FieldInfo.DATA_CLIENT;
                currentFieldInfo.location = FieldInfo.FIELD_IP;
                i+="c-ip".length();
            } else if (fields.startsWith("c-dns",i)) {
                currentFieldInfo.type = FieldInfo.DATA_CLIENT;
                currentFieldInfo.location = FieldInfo.FIELD_DNS;
                i+="c-dns".length();
            } else if (fields.startsWith("s-ip",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SERVER;
                currentFieldInfo.location = FieldInfo.FIELD_IP;
                i+="s-ip".length();
            } else if (fields.startsWith("s-dns",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SERVER;
                currentFieldInfo.location = FieldInfo.FIELD_DNS;
                i+="s-dns".length();
            } else if (fields.startsWith("cs",i)) {
                i = decode(fields, i+2, currentFieldInfo,
                            FieldInfo.DATA_CLIENT_TO_SERVER);
                if (i<0)
                    return null;
            } else if (fields.startsWith("sc",i)) {
                i = decode(fields, i+2, currentFieldInfo,
                            FieldInfo.DATA_SERVER_TO_CLIENT);
                if (i<0)
                    return null;
            } else if (fields.startsWith("sr",i)) {
                i = decode(fields, i+2, currentFieldInfo,
                            FieldInfo.DATA_SERVER_TO_RSERVER);
                if (i<0)
                    return null;
            } else if (fields.startsWith("rs",i)) {
                i = decode(fields, i+2, currentFieldInfo,
                            FieldInfo.DATA_RSERVER_TO_SERVER);
                if (i<0)
                    return null;
            } else if (fields.startsWith("x",i)) {
                i = decodeAppSpecific(fields, i, currentFieldInfo);
            } else {
                // Unable to decode ...
                log.error("unable to decode with rest of chars being: " +
                            fields.substring(i));
                return null;
            }

            // By this point we should have the field, get the whitespace
            j=i;
            for (;j<fields.length() && Character.isWhitespace(fields.charAt(j));j++);

            if (j>=fields.length()) {
                if (j==i) {
                    // Special case - end of string
                    currentFieldInfo.postWhiteSpace = "";
                } else {
                    currentFieldInfo.postWhiteSpace = fields.substring(i);
                    i=j;
                }
            } else {
                currentFieldInfo.postWhiteSpace = fields.substring(i,j);
                i=j;
            }

            list.add(currentFieldInfo);
        }

        i=0;
        FieldInfo[] f = new FieldInfo[list.size()];
        for (Iterator k = list.iterator(); k.hasNext();)
             f[i++] = (FieldInfo)k.next();

        if (log.isDebugEnabled())
            log.debug("finished decoding with length of: " + i);

        return f;
    }

    /**
     * Decode the cs or sc fields.
     * Returns negative on error.
     *
     * @param fields The pattern to decode
     * @param i The string index where we are decoding.
     * @param fieldInfo Where to store the results
     * @param type The type we are decoding.
     * @return -1 on error. Otherwise the new String index.
     */
    private int decode(String fields, int i, FieldInfo fieldInfo, short type) {

        if (fields.startsWith("-status",i)) {
            fieldInfo.location = FieldInfo.FIELD_STATUS;
            i+="-status".length();
        } else if (fields.startsWith("-comment",i)) {
            fieldInfo.location = FieldInfo.FIELD_COMMENT;
            i+="-comment".length();
        } else if (fields.startsWith("-uri-query",i)) {
            fieldInfo.location = FieldInfo.FIELD_URI_QUERY;
            i+="-uri-query".length();
        } else if (fields.startsWith("-uri-stem",i)) {
            fieldInfo.location = FieldInfo.FIELD_URI_STEM;
            i+="-uri-stem".length();
        } else if (fields.startsWith("-uri",i)) {
            fieldInfo.location = FieldInfo.FIELD_URI;
            i+="-uri".length();
        } else if (fields.startsWith("-method",i)) {
            fieldInfo.location = FieldInfo.FIELD_METHOD;
            i+="-method".length();
        } else if (fields.startsWith("(",i)) {
            fieldInfo.location = FieldInfo.FIELD_HEADER;
            i++;                                  /* Move past the ( */
            int j = fields.indexOf(')', i);
            if (j==-1) {                          /* Not found */
                log.error("No closing ) found for in decode");
                return -1;
            }
            fieldInfo.value = fields.substring(i,j);
            i=j+1;                                // Move pointer past ) */
        } else {
            log.error("The next characters couldn't be decoded: " + fields.substring(i));
            return -1;
        }

        fieldInfo.type = type;
        return i;

    }


    /**
      * Decode app specific log entry.
      *
      * Special fields are of the form:
      * x-C(...) - For cookie
      * x-A(...) - Value in servletContext
      * x-S(...) - Value in session
      * x-R(...) - Value in servletRequest
      * @param fields The pattern to decode
      * @param i The string index where we are decoding.
      * @param fieldInfo Where to store the results
      * @return -1 on error. Otherwise the new String index.
      */
    private int decodeAppSpecific(String fields, int i, FieldInfo fieldInfo) {

        fieldInfo.type = FieldInfo.DATA_APP_SPECIFIC;
        /* Move past 'x-' */
        i+=2;

        if (i>=fields.length()) {
            log.error("End of line reached before decoding x- param");
            return -1;
        }

        switch(fields.charAt(i)) {
            case 'A':
                fieldInfo.xType = FieldInfo.X_APP;
                break;
            case 'C':
                fieldInfo.xType = FieldInfo.X_COOKIE;
                break;
            case 'R':
                fieldInfo.xType = FieldInfo.X_REQUEST;
                break;
            case 'S':
                fieldInfo.xType = FieldInfo.X_SESSION;
                break;
            case 'H':
                fieldInfo.xType = FieldInfo.X_SERVLET_REQUEST;
                break;
            case 'P':
                fieldInfo.xType = FieldInfo.X_PARAMETER;
                break;
            default:
                return -1;
        }

        /* test that next char is a ( */
        if (i+1!=fields.indexOf('(',i)) {
            log.error("x param in wrong format. Needs to be 'x-#(...)' read the docs!");
            return -1;
        }
        i+=2; /* Move inside of the () */

        /* Look for ending ) and return error if not found. */
        int j = fields.indexOf(')',i);
        if (j==-1) {
            log.error("x param in wrong format. No closing ')'!");
            return -1;
        }

        fieldInfo.value = fields.substring(i,j);

        if (fieldInfo.xType == FieldInfo.X_SERVLET_REQUEST) {
            if ("authType".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_AUTHTYPE;
            } else if ("remoteUser".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_REMOTEUSER;
            } else if ("requestedSessionId".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_REQUESTEDSESSIONID;
            } else if ("requestedSessionIdFromCookie".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_REQUESTEDSESSIONIDFROMCOOKIE;
            } else if ("requestedSessionIdValid".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_REQUESTEDSESSIONIDVALID;
            } else if ("contentLength".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_CONTENTLENGTH;
            } else if ("characterEncoding".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_CHARACTERENCODING;
            } else if ("locale".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_LOCALE;
            } else if ("protocol".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_PROTOCOL;
            } else if ("scheme".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_SCHEME;
            } else if ("secure".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_SECURE;
            } else {
                log.error("x param for servlet request, couldn't decode value: " +
                            fieldInfo.location);
                return -1;
            }
        }

        return j+1;

    }


}

/**
 * A simple helper for decoding the pattern.
 */
class FieldInfo {
    /*
       The goal of the constants listed below is to make the construction of the log
       entry as quick as possible via numerci decodings of the methods to call instead
       of performing many String comparisons on each logging request.
    */

    /* Where the data is located. */
    static final short DATA_CLIENT = 0;
    static final short DATA_SERVER = 1;
    static final short DATA_REMOTE = 2;
    static final short DATA_CLIENT_TO_SERVER = 3;
    static final short DATA_SERVER_TO_CLIENT = 4;
    static final short DATA_SERVER_TO_RSERVER = 5; /* Here to honor the spec. */
    static final short DATA_RSERVER_TO_SERVER = 6; /* Here to honor the spec. */
    static final short DATA_APP_SPECIFIC = 7;
    static final short DATA_SPECIAL = 8;

    /* The type of special fields. */
    static final short SPECIAL_DATE         = 1;
    static final short SPECIAL_TIME_TAKEN   = 2;
    static final short SPECIAL_TIME         = 3;
    static final short SPECIAL_BYTES        = 4;
    static final short SPECIAL_CACHED       = 5;

    /* Where to pull the data for prefixed values */
    static final short FIELD_IP            = 1;
    static final short FIELD_DNS           = 2;
    static final short FIELD_STATUS        = 3;
    static final short FIELD_COMMENT       = 4;
    static final short FIELD_METHOD        = 5;
    static final short FIELD_URI           = 6;
    static final short FIELD_URI_STEM      = 7;
    static final short FIELD_URI_QUERY     = 8;
    static final short FIELD_HEADER        = 9;


    /* Application Specific parameters */
    static final short X_REQUEST = 1; /* For x app specific */
    static final short X_SESSION = 2; /* For x app specific */
    static final short X_COOKIE  = 3; /* For x app specific */
    static final short X_APP     = 4; /* For x app specific */
    static final short X_SERVLET_REQUEST = 5; /* For x app specific */
    static final short X_PARAMETER = 6; /* For x app specific */

    static final short X_LOC_AUTHTYPE                       = 1;
    static final short X_LOC_REMOTEUSER                     = 2;
    static final short X_LOC_REQUESTEDSESSIONID             = 3;
    static final short X_LOC_REQUESTEDSESSIONIDFROMCOOKIE   = 4;
    static final short X_LOC_REQUESTEDSESSIONIDVALID        = 5;
    static final short X_LOC_CONTENTLENGTH                  = 6;
    static final short X_LOC_CHARACTERENCODING              = 7;
    static final short X_LOC_LOCALE                         = 8;
    static final short X_LOC_PROTOCOL                       = 9;
    static final short X_LOC_SCHEME                         = 10;
    static final short X_LOC_SECURE                         = 11;



    /** The field type */
    short type;

    /** Where to pull the data from? Icky variable name. */
    short location;

    /** The x- specific place to pull the data from. */
    short xType;

    /** The field value if needed. Needed for headers and app specific. */
    String value;

    /** Any white space after this field? Put it here. */
    String postWhiteSpace = null;

}
