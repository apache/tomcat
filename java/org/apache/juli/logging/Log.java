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
package org.apache.juli.logging;

/**
 * <p>A simple logging interface abstracting logging APIs.  In order to be
 * instantiated successfully by {@link LogFactory}, classes that implement
 * this interface must have a constructor that takes a single String
 * parameter representing the "name" of this Log.</p>
 *
 * <p> The six logging levels used by <code>Log</code> are (in order):</p>
 * <ol>
 * <li>trace (the least serious)</li>
 * <li>debug</li>
 * <li>info</li>
 * <li>warn</li>
 * <li>error</li>
 * <li>fatal (the most serious)</li>
 * </ol>
 * <p>The mapping of these log levels to the concepts used by the underlying
 * logging system is implementation dependent.
 * The implementation should ensure, though, that this ordering behaves
 * as expected.</p>
 *
 * <p>Performance is often a logging concern.
 * By examining the appropriate property,
 * a component can avoid expensive operations (producing information
 * to be logged).</p>
 *
 * <p> For example,
 * <code>
 *    if (log.isDebugEnabled()) {
 *        ... do something expensive ...
 *        log.debug(theResult);
 *    }
 * </code>
 * </p>
 *
 * <p>Configuration of the underlying logging system will generally be done
 * external to the Logging APIs, through whatever mechanism is supported by
 * that system.</p>
 *
 * @author <a href="mailto:sanders@apache.org">Scott Sanders</a>
 * @author Rod Waldhoff
 */
public interface Log {


    // ----------------------------------------------------- Logging Properties


    /**
     * <p> Is debug logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than debug. </p>
     *
     * @return <code>true</code> if debug level logging is enabled, otherwise
     *         <code>false</code>
     */
    boolean isDebugEnabled();


    /**
     * <p> Is error logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than error. </p>
     *
     * @return <code>true</code> if error level logging is enabled, otherwise
     *         <code>false</code>
     */
    boolean isErrorEnabled();


    /**
     * <p> Is fatal logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than fatal. </p>
     *
     * @return <code>true</code> if fatal level logging is enabled, otherwise
     *         <code>false</code>
     */
    boolean isFatalEnabled();


    /**
     * <p> Is info logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than info. </p>
     *
     * @return <code>true</code> if info level logging is enabled, otherwise
     *         <code>false</code>
     */
    boolean isInfoEnabled();


    /**
     * <p> Is trace logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than trace. </p>
     *
     * @return <code>true</code> if trace level logging is enabled, otherwise
     *         <code>false</code>
     */
    boolean isTraceEnabled();


    /**
     * <p> Is warn logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than warn. </p>
     *
     * @return <code>true</code> if warn level logging is enabled, otherwise
     *         <code>false</code>
     */
    boolean isWarnEnabled();


    // -------------------------------------------------------- Logging Methods


    /**
     * <p> Log a message with trace log level. </p>
     *
     * @param message log this message
     */
    void trace(Object message);


    /**
     * <p> Log an error with trace log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void trace(Object message, Throwable t);


    /**
     * <p> Log a message with debug log level. </p>
     *
     * @param message log this message
     */
    void debug(Object message);


    /**
     * <p> Log an error with debug log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void debug(Object message, Throwable t);


    /**
     * <p> Log a message with info log level. </p>
     *
     * @param message log this message
     */
    void info(Object message);


    /**
     * <p> Log an error with info log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void info(Object message, Throwable t);


    /**
     * <p> Log a message with warn log level. </p>
     *
     * @param message log this message
     */
    void warn(Object message);


    /**
     * <p> Log an error with warn log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void warn(Object message, Throwable t);


    /**
     * <p> Log a message with error log level. </p>
     *
     * @param message log this message
     */
    void error(Object message);


    /**
     * <p> Log an error with error log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void error(Object message, Throwable t);


    /**
     * <p> Log a message with fatal log level. </p>
     *
     * @param message log this message
     */
    void fatal(Object message);


    /**
     * <p> Log an error with fatal log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    void fatal(Object message, Throwable t);


}
