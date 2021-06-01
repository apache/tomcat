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
package org.apache.juli;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * Implementation of <b>Handler</b> that appends log messages to a file
 * named {prefix}{date}{suffix} in a configured directory.
 *
 * <p>The following configuration properties are available:</p>
 *
 * <ul>
 *   <li><code>directory</code> - The directory where to create the log file.
 *    If the path is not absolute, it is relative to the current working
 *    directory of the application. The Apache Tomcat configuration files usually
 *    specify an absolute path for this property,
 *    <code>${catalina.base}/logs</code>
 *    Default value: <code>logs</code></li>
 *   <li><code>rotatable</code> - If <code>true</code>, the log file will be
 *    rotated on the first write past midnight and the filename will be
 *    <code>{prefix}{date}{suffix}</code>, where date is yyyy-MM-dd. If <code>false</code>,
 *    the file will not be rotated and the filename will be <code>{prefix}{suffix}</code>.
 *    Default value: <code>true</code></li>
 *   <li><code>prefix</code> - The leading part of the log file name.
 *    Default value: <code>juli.</code></li>
 *   <li><code>suffix</code> - The trailing part of the log file name. Default value: <code>.log</code></li>
 *   <li><code>bufferSize</code> - Configures buffering. The value of <code>0</code>
 *    uses system default buffering (typically an 8K buffer will be used). A
 *    value of <code>&lt;0</code> forces a writer flush upon each log write. A
 *    value <code>&gt;0</code> uses a BufferedOutputStream with the defined
 *    value but note that the system default buffering will also be
 *    applied. Default value: <code>-1</code></li>
 *   <li><code>encoding</code> - Character set used by the log file. Default value:
 *    empty string, which means to use the system default character set.</li>
 *   <li><code>level</code> - The level threshold for this Handler. See the
 *    <code>java.util.logging.Level</code> class for the possible levels.
 *    Default value: <code>ALL</code></li>
 *   <li><code>filter</code> - The <code>java.util.logging.Filter</code>
 *    implementation class name for this Handler. Default value: unset</li>
 *   <li><code>formatter</code> - The <code>java.util.logging.Formatter</code>
 *    implementation class name for this Handler. Default value:
 *    <code>java.util.logging.SimpleFormatter</code></li>
 *   <li><code>maxDays</code> - The maximum number of days to keep the log
 *    files. If the specified value is <code>&lt;=0</code> then the log files
 *    will be kept on the file system forever, otherwise they will be kept the
 *    specified maximum days. Default value: <code>-1</code>.</li>
 * </ul>
 */
public class FileHandler extends Handler {

    public static final int DEFAULT_MAX_DAYS = -1;
    public static final int DEFAULT_BUFFER_SIZE = -1;


    private static final ExecutorService DELETE_FILES_SERVICE =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                private static final String NAME_PREFIX = "FileHandlerLogFilesCleaner-";
                private final boolean isSecurityEnabled;
                private final ThreadGroup group;
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                {
                    SecurityManager s = System.getSecurityManager();
                    if (s == null) {
                        this.isSecurityEnabled = false;
                        this.group = Thread.currentThread().getThreadGroup();
                    } else {
                        this.isSecurityEnabled = true;
                        this.group = s.getThreadGroup();
                    }
                }

                @Override
                public Thread newThread(Runnable r) {
                    ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    try {
                        // Threads should not be created by the webapp classloader
                        if (isSecurityEnabled) {
                            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                                Thread.currentThread()
                                        .setContextClassLoader(getClass().getClassLoader());
                                return null;
                            });
                        } else {
                            Thread.currentThread()
                                    .setContextClassLoader(getClass().getClassLoader());
                        }
                        Thread t = new Thread(group, r,
                                NAME_PREFIX + threadNumber.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    } finally {
                        if (isSecurityEnabled) {
                            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                                Thread.currentThread().setContextClassLoader(loader);
                                return null;
                            });
                        } else {
                            Thread.currentThread().setContextClassLoader(loader);
                        }
                    }
                }
            });

    // ------------------------------------------------------------ Constructor


    public FileHandler() {
        this(null, null, null);
    }


    public FileHandler(String directory, String prefix, String suffix) {
        this(directory, prefix, suffix, null);
    }


    public FileHandler(String directory, String prefix, String suffix, Integer maxDays) {
        this(directory, prefix, suffix, maxDays, null, null);
    }


    public FileHandler(String directory, String prefix, String suffix, Integer maxDays,
            Boolean rotatable, Integer bufferSize) {
        this.directory = directory;
        this.prefix = prefix;
        this.suffix = suffix;
        this.maxDays = maxDays;
        this.rotatable = rotatable;
        this.bufferSize = bufferSize;
        configure();
        openWriter();
        clean();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The as-of date for the currently open log file, or a zero-length
     * string if there is no open log file.
     */
    private volatile String date = "";


    /**
     * The directory in which log files are created.
     */
    private String directory;


    /**
     * The prefix that is added to log file filenames.
     */
    private String prefix;


    /**
     * The suffix that is added to log file filenames.
     */
    private String suffix;


    /**
     * Determines whether the log file is rotatable
     */
    private Boolean rotatable;


    /**
     * Maximum number of days to keep the log files
     */
    private Integer maxDays;


    /**
     * The PrintWriter to which we are currently logging, if any.
     */
    private volatile PrintWriter writer = null;


    /**
     * Lock used to control access to the writer.
     */
    protected final ReadWriteLock writerLock = new ReentrantReadWriteLock();


    /**
     * Log buffer size.
     */
    private Integer bufferSize;


    /**
     * Represents a file name pattern of type {prefix}{date}{suffix}.
     * The date is YYYY-MM-DD
     */
    private Pattern pattern;


    // --------------------------------------------------------- Public Methods


    /**
     * Format and publish a <code>LogRecord</code>.
     *
     * @param  record  description of the log event
     */
    @Override
    public void publish(LogRecord record) {

        if (!isLoggable(record)) {
            return;
        }

        // Construct the timestamp we will use, if requested
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        String tsDate = ts.toString().substring(0, 10);

        writerLock.readLock().lock();
        try {
            // If the date has changed, switch log files
            if (rotatable.booleanValue() && !date.equals(tsDate)) {
                // Upgrade to writeLock before we switch
                writerLock.readLock().unlock();
                writerLock.writeLock().lock();
                try {
                    // Make sure another thread hasn't already done this
                    if (!date.equals(tsDate)) {
                        closeWriter();
                        date = tsDate;
                        openWriter();
                        clean();
                    }
                } finally {
                    // Downgrade to read-lock. This ensures the writer remains valid
                    // until the log message is written
                    writerLock.readLock().lock();
                    writerLock.writeLock().unlock();
                }
            }

            String result = null;
            try {
                result = getFormatter().format(record);
            } catch (Exception e) {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
                return;
            }

            try {
                if (writer != null) {
                    writer.write(result);
                    if (bufferSize.intValue() < 0) {
                        writer.flush();
                    }
                } else {
                    reportError("FileHandler is closed or not yet initialized, unable to log ["
                            + result + "]", null, ErrorManager.WRITE_FAILURE);
                }
            } catch (Exception e) {
                reportError(null, e, ErrorManager.WRITE_FAILURE);
            }
        } finally {
            writerLock.readLock().unlock();
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * Close the currently open log file (if any).
     */
    @Override
    public void close() {
        closeWriter();
    }

    protected void closeWriter() {

        writerLock.writeLock().lock();
        try {
            if (writer == null) {
                return;
            }
            writer.write(getFormatter().getTail(this));
            writer.flush();
            writer.close();
            writer = null;
            date = "";
        } catch (Exception e) {
            reportError(null, e, ErrorManager.CLOSE_FAILURE);
        } finally {
            writerLock.writeLock().unlock();
        }
    }


    /**
     * Flush the writer.
     */
    @Override
    public void flush() {

        writerLock.readLock().lock();
        try {
            if (writer == null) {
                return;
            }
            writer.flush();
        } catch (Exception e) {
            reportError(null, e, ErrorManager.FLUSH_FAILURE);
        } finally {
            writerLock.readLock().unlock();
        }

    }


    /**
     * Configure from <code>LogManager</code> properties.
     */
    private void configure() {

        Timestamp ts = new Timestamp(System.currentTimeMillis());
        date = ts.toString().substring(0, 10);

        String className = this.getClass().getName(); //allow classes to override

        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        // Retrieve configuration of logging file name
        if (rotatable == null) {
            rotatable = Boolean.valueOf(getProperty(className + ".rotatable", "true"));
        }
        if (directory == null) {
            directory = getProperty(className + ".directory", "logs");
        }
        if (prefix == null) {
            prefix = getProperty(className + ".prefix", "juli.");
        }
        if (suffix == null) {
            suffix = getProperty(className + ".suffix", ".log");
        }

        // https://bz.apache.org/bugzilla/show_bug.cgi?id=61232
        boolean shouldCheckForRedundantSeparator =
                !rotatable.booleanValue() && !prefix.isEmpty() && !suffix.isEmpty();
        // assuming separator is just one char, if there are use cases with
        // more, the notion of separator might be introduced
        if (shouldCheckForRedundantSeparator &&
                (prefix.charAt(prefix.length() - 1) == suffix.charAt(0))) {
            suffix = suffix.substring(1);
        }

        pattern = Pattern.compile("^(" + Pattern.quote(prefix) + ")\\d{4}-\\d{1,2}-\\d{1,2}("
                + Pattern.quote(suffix) + ")$");

        if (maxDays == null) {
            String sMaxDays = getProperty(className + ".maxDays", String.valueOf(DEFAULT_MAX_DAYS));
            try {
                maxDays = Integer.valueOf(sMaxDays);
            } catch (NumberFormatException ignore) {
                maxDays = Integer.valueOf(DEFAULT_MAX_DAYS);
            }
        }

        if (bufferSize == null) {
            String sBufferSize = getProperty(className + ".bufferSize",
                    String.valueOf(DEFAULT_BUFFER_SIZE));
            try {
                bufferSize = Integer.valueOf(sBufferSize);
            } catch (NumberFormatException ignore) {
                bufferSize = Integer.valueOf(DEFAULT_BUFFER_SIZE);
            }
        }

        // Get encoding for the logging file
        String encoding = getProperty(className + ".encoding", null);
        if (encoding != null && encoding.length() > 0) {
            try {
                setEncoding(encoding);
            } catch (UnsupportedEncodingException ex) {
                // Ignore
            }
        }

        // Get logging level for the handler
        setLevel(Level.parse(getProperty(className + ".level", "" + Level.ALL)));

        // Get filter configuration
        String filterName = getProperty(className + ".filter", null);
        if (filterName != null) {
            try {
                setFilter((Filter) cl.loadClass(filterName).getConstructor().newInstance());
            } catch (Exception e) {
                // Ignore
            }
        }

        // Set formatter
        String formatterName = getProperty(className + ".formatter", null);
        if (formatterName != null) {
            try {
                setFormatter((Formatter) cl.loadClass(
                        formatterName).getConstructor().newInstance());
            } catch (Exception e) {
                // Ignore and fallback to defaults
                setFormatter(new OneLineFormatter());
            }
        } else {
            setFormatter(new OneLineFormatter());
        }

        // Set error manager
        setErrorManager(new ErrorManager());
    }


    private String getProperty(String name, String defaultValue) {
        String value = LogManager.getLogManager().getProperty(name);
        if (value == null) {
            value = defaultValue;
        } else {
            value = value.trim();
        }
        return value;
    }


    /**
     * Open the new log file for the date specified by <code>date</code>.
     */
    protected void open() {
        openWriter();
    }

    protected void openWriter() {

        // Create the directory if necessary
        File dir = new File(directory);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            reportError("Unable to create [" + dir + "]", null, ErrorManager.OPEN_FAILURE);
            writer = null;
            return;
        }

        // Open the current log file
        writerLock.writeLock().lock();
        FileOutputStream fos = null;
        OutputStream os = null;
        try {
            File pathname = new File(dir.getAbsoluteFile(), prefix
                    + (rotatable.booleanValue() ? date : "") + suffix);
            File parent = pathname.getParentFile();
            if (!parent.mkdirs() && !parent.isDirectory()) {
                reportError("Unable to create [" + parent + "]", null, ErrorManager.OPEN_FAILURE);
                writer = null;
                return;
            }
            String encoding = getEncoding();
            fos = new FileOutputStream(pathname, true);
            os = bufferSize.intValue() > 0 ? new BufferedOutputStream(fos, bufferSize.intValue()) : fos;
            writer = new PrintWriter(
                    (encoding != null) ? new OutputStreamWriter(os, encoding)
                                       : new OutputStreamWriter(os), false);
            writer.write(getFormatter().getHead(this));
        } catch (Exception e) {
            reportError(null, e, ErrorManager.OPEN_FAILURE);
            writer = null;
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e1) {
                    // Ignore
                }
            }
        } finally {
            writerLock.writeLock().unlock();
        }
    }

    private void clean() {
        if (maxDays.intValue() <= 0) {
            return;
        }
        DELETE_FILES_SERVICE.submit(() -> {
            try (DirectoryStream<Path> files = streamFilesForDelete()) {
                for (Path file : files) {
                    Files.delete(file);
                }
            } catch (IOException e) {
                reportError("Unable to delete log files older than [" + maxDays + "] days", null,
                        ErrorManager.GENERIC_FAILURE);
            }
        });
    }

    private DirectoryStream<Path> streamFilesForDelete() throws IOException {
        LocalDate maxDaysOffset = LocalDate.now().minus(maxDays.intValue(), ChronoUnit.DAYS);
        return Files.newDirectoryStream(new File(directory).toPath(), path -> {
            boolean result = false;
            String date = obtainDateFromPath(path);
            if (date != null) {
                try {
                    LocalDate dateFromFile = LocalDate.from(DateTimeFormatter.ISO_LOCAL_DATE.parse(date));
                    result = dateFromFile.isBefore(maxDaysOffset);
                } catch (DateTimeException e) {
                    // no-op
                }
            }
            return result;
        });
    }

    private String obtainDateFromPath(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return null;
        }
        String date = fileName.toString();
        if (pattern.matcher(date).matches()) {
            date = date.substring(prefix.length());
            return date.substring(0, date.length() - suffix.length());
        } else {
            return null;
        }
    }
}
