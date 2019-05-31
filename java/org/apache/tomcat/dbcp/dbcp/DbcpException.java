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

package org.apache.tomcat.dbcp.dbcp;


/**
 * <p>Subclass of <code>RuntimeException</code> that can be used to wrap
 * a <code>SQLException</code> using the "root cause" pattern of JDK 1.4
 * exceptions, but without requiring a 1.4 runtime environment.</p>
 *
 * @author Jonathan Fuerth
 * @author Dan Fraser
 *
 * @deprecated This will be removed in a future version of DBCP.
 **/
@Deprecated
public class DbcpException extends RuntimeException {

    private static final long serialVersionUID = 2477800549022838103L;

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new runtime exception with <code>null</code> as its
     * detail message.
     */
    public DbcpException() {

        super();

    }


    /**
     * Construct a new runtime exception with the specified detail message.
     *
     * @param message The detail message for this exception
     */
    public DbcpException(String message) {

        this(message, null);

    }


    /**
     * Construct a new runtime exception with the specified detail message
     * and cause.
     *
     * @param message The detail message for this exception
     * @param cause The root cause for this exception
     */
    public DbcpException(String message, Throwable cause) {

        super(message);
        this.cause = cause;

    }


    /**
     * Construct a new runtime exception with the specified cause and a
     * detail message of <code>(cause == null &#63; null : cause&#46;toString())</code>.
     *
     * @param cause The root cause for this exception
     */
    public DbcpException(Throwable cause) {

        super((cause == null) ? (String) null : cause.toString());
        this.cause = cause;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The root cause of this exception (typically an
     * <code>SQLException</code> but this is not required).
     */
    protected Throwable cause = null;


    // --------------------------------------------------------- Public Methods


    /**
     * Return the root cause of this exception (if any).
     */
    @Override
    public Throwable getCause() {

        return (this.cause);

    }


}
