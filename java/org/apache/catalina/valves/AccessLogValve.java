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
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.catalina.AccessLog;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.B2CConverter;


/**
 * <p>Implementation of the <b>Valve</b> interface that generates a web server
 * access log with the detailed line contents matching a configurable pattern.
 * The syntax of the available patterns is similar to that supported by the
 * <a href="http://httpd.apache.org/">Apache HTTP Server</a>
 * <code>mod_log_config</code> module.  As an additional feature,
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
 * Log rotation can be on or off. This is dictated by the
 * <code>rotatable</code> property.
 * </p>
 *
 * <p>
 * For UNIX users, another field called <code>checkExists</code> is also
 * available. If set to true, the log file's existence will be checked before
 * each logging. This way an external log rotator can move the file
 * somewhere and Tomcat will start with a new file.
 * </p>
 *
 * <p>
 * For JMX junkies, a public method called <code>rotate</code> has
 * been made available to allow you to tell this instance to move
 * the existing log file to somewhere else and start writing a new log file.
 * </p>
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
 *
 * @version $Id$
 */

public class AccessLogValve extends AbstractAccessLogValve implements AccessLog {

    private static final Log log = LogFactory.getLog(AccessLogValve.class);

    //------------------------------------------------------ Constructor
    public AccessLogValve() {
        super();
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
     * The prefix that is added to log file filenames.
     */
    protected String prefix = "access_log";


    /**
     * Should we rotate our log file? Default is true (like old behavior)
     */
    protected boolean rotatable = true;

    /**
     * Should we defer inclusion of the date stamp in the file
     * name until rotate time? Default is false.
     */
    protected boolean renameOnRotate = false;


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
     * A date formatter to format a Date using the format
     * given by <code>fileDateFormat</code>.
     */
    protected SimpleDateFormat fileDateFormatter = null;


    /**
     * The current log file we are writing to. Helpful when checkExists
     * is true.
     */
    protected File currentLogFile = null;

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
     * Date format to place in log file name.
     */
    protected String fileDateFormat = ".yyyy-MM-dd";

    /**
     * Character set used by the log file. If it is <code>null</code>, the
     * system default character set will be used. An empty string will be
     * treated as <code>null</code> when this property is assigned.
     */
    protected String encoding = null;

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
     * Should we defer inclusion of the date stamp in the file
     * name until rotate time
     */
    public boolean isRenameOnRotate() {
        return renameOnRotate;
    }


    /**
     * Set the value if we should defer inclusion of the date
     * stamp in the file name until rotate time
     *
     * @param renameOnRotate true if defer inclusion of date stamp
     */
    public void setRenameOnRotate(boolean renameOnRotate) {
        this.renameOnRotate = renameOnRotate;
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
     *  Return the date format date based log rotation.
     */
    public String getFileDateFormat() {
        return fileDateFormat;
    }


    /**
     *  Set the date format date based log rotation.
     */
    public void setFileDateFormat(String fileDateFormat) {
        String newFormat;
        if (fileDateFormat == null) {
            newFormat = "";
        } else {
            newFormat = fileDateFormat;
        }
        this.fileDateFormat = newFormat;

        synchronized (this) {
            fileDateFormatter = new SimpleDateFormat(newFormat, Locale.US);
            fileDateFormatter.setTimeZone(TimeZone.getDefault());
        }
    }

    /**
     * Return the character set name that is used to write the log file.
     *
     * @return Character set name, or <code>null</code> if the system default
     *  character set is used.
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Set the character set that is used to write the log file.
     *
     * @param encoding The name of the character set.
     */
    public void setEncoding(String encoding) {
        if (encoding != null && encoding.length() > 0) {
            this.encoding = encoding;
        } else {
            this.encoding = null;
        }
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
     * Rotate the log file if necessary.
     */
    public void rotate() {
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
                            close(true);
                            dateStamp = tsDate;
                            open();
                        }
                    }
                }
            }
        }
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
            close(false);
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
     * Create a File object based on the current log file name.
     * Directories are created as needed but the underlying file
     * is not created or opened.
     *
     * @param useDateStamp include the timestamp in the file name.
     * @return the log file object
     */
    private File getLogFile(boolean useDateStamp) {

        // Create the directory if necessary
        File dir = new File(directory);
        if (!dir.isAbsolute()) {
            dir = new File(getContainer().getCatalinaBase(), directory);
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            log.error(sm.getString("accessLogValve.openDirFail", dir));
        }

        // Calculate the current log file name
        File pathname;
        if (useDateStamp) {
            pathname = new File(dir.getAbsoluteFile(), prefix + dateStamp
                    + suffix);
        } else {
            pathname = new File(dir.getAbsoluteFile(), prefix + suffix);
        }
        File parent = pathname.getParentFile();
        if (!parent.mkdirs() && !parent.isDirectory()) {
            log.error(sm.getString("accessLogValve.openDirFail", parent));
        }
        return pathname;
    }

    /**
     * Move a current but rotated log file back to the unrotated
     * one. Needed if date stamp inclusion is deferred to rotation
     * time.
     */
    private void restore() {
        File newLogFile = getLogFile(false);
        File rotatedLogFile = getLogFile(true);
        if (rotatedLogFile.exists() && !newLogFile.exists() &&
            !rotatedLogFile.equals(newLogFile)) {
            try {
                if (!rotatedLogFile.renameTo(newLogFile)) {
                    log.error(sm.getString("accessLogValve.renameFail", rotatedLogFile, newLogFile));
                }
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                log.error(sm.getString("accessLogValve.renameFail", rotatedLogFile, newLogFile), e);
            }
        }
    }


    /**
     * Close the currently open log file (if any)
     *
     * @param rename Rename file to final name after closing
     */
    private synchronized void close(boolean rename) {
        if (writer == null) {
            return;
        }
        writer.flush();
        writer.close();
        if (rename && renameOnRotate) {
            File newLogFile = getLogFile(true);
            if (!newLogFile.exists()) {
                try {
                    if (!currentLogFile.renameTo(newLogFile)) {
                        log.error(sm.getString("accessLogValve.renameFail", currentLogFile, newLogFile));
                    }
                } catch (Throwable e) {
                    ExceptionUtils.handleThrowable(e);
                    log.error(sm.getString("accessLogValve.renameFail", currentLogFile, newLogFile), e);
                }
            } else {
                log.error(sm.getString("accessLogValve.alreadyExists", currentLogFile, newLogFile));
            }
        }
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
    public void log(CharArrayWriter message) {

        rotate();

        /* In case something external rotated the file instead */
        if (checkExists) {
            synchronized (this) {
                if (currentLogFile != null && !currentLogFile.exists()) {
                    try {
                        close(false);
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
        try {
            synchronized(this) {
                if (writer != null) {
                    message.writeTo(writer);
                    writer.println("");
                    if (!buffered) {
                        writer.flush();
                    }
                }
            }
        } catch (IOException ioe) {
            log.warn(sm.getString(
                    "accessLogValve.writeFail", message.toString()), ioe);
        }
    }


    /**
     * Open the new log file for the date specified by <code>dateStamp</code>.
     */
    protected synchronized void open() {
        // Open the current log file
        // If no rotate - no need for dateStamp in fileName
        File pathname = getLogFile(rotatable && !renameOnRotate);

        Charset charset = null;
        if (encoding != null) {
            try {
                charset = B2CConverter.getCharset(encoding);
            } catch (UnsupportedEncodingException ex) {
                log.error(sm.getString(
                        "accessLogValve.unsupportedEncoding", encoding), ex);
            }
        }
        if (charset == null) {
            charset = StandardCharsets.ISO_8859_1;
        }

        try {
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(pathname, true), charset), 128000),
                    false);

            currentLogFile = pathname;
        } catch (IOException e) {
            writer = null;
            currentLogFile = null;
            log.error(sm.getString("accessLogValve.openFail", pathname), e);
        }
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
        fileDateFormatter = new SimpleDateFormat(format, Locale.US);
        fileDateFormatter.setTimeZone(TimeZone.getDefault());
        dateStamp = fileDateFormatter.format(new Date(System.currentTimeMillis()));
        if (rotatable && renameOnRotate) {
            restore();
        }
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
        close(false);
    }
}
