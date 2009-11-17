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
package org.apache.tomcat.util.http.fileupload;

import java.io.PrintStream;
import java.io.PrintWriter;


/**
 * Exception for errors encountered while processing the request.
 *
 * @author <a href="mailto:jmcnally@collab.net">John McNally</a>
 * @version $Id$
 */
public class FileUploadException extends Exception {
    /**
     * Serial version UID, being used, if the exception
     * is serialized.
     */
    private static final long serialVersionUID = 8881893724388807504L;
    /**
     * The exceptions cause. We overwrite the cause of
     * the super class, which isn't available in Java 1.3.
     */
    private final Throwable cause;

    /**
     * Constructs a new <code>FileUploadException</code> without message.
     */
    public FileUploadException() {
        this(null, null);
    }

    /**
     * Constructs a new <code>FileUploadException</code> with specified detail
     * message.
     *
     * @param msg the error message.
     */
    public FileUploadException(final String msg) {
        this(msg, null);
    }

    /**
     * Creates a new <code>FileUploadException</code> with the given
     * detail message and cause.
     * @param msg The exceptions detail message.
     * @param cause The exceptions cause.
     */
    public FileUploadException(String msg, Throwable cause) {
        super(msg);
        this.cause = cause;
    }

    /**
     * Prints this throwable and its backtrace to the specified print stream.
     *
     * @param stream <code>PrintStream</code> to use for output
     */
    public void printStackTrace(PrintStream stream) {
        super.printStackTrace(stream);
        if (cause != null) {
            stream.println("Caused by:");
            cause.printStackTrace(stream);
        }
    }

    /**
     * Prints this throwable and its backtrace to the specified
     * print writer.
     *
     * @param writer <code>PrintWriter</code> to use for output
     */
    public void printStackTrace(PrintWriter writer) {
        super.printStackTrace(writer);
        if (cause != null) {
            writer.println("Caused by:");
            cause.printStackTrace(writer);
        }
    }

    public Throwable getCause() {
        return cause;
    }
}
