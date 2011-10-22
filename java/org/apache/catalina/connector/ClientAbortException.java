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
package org.apache.catalina.connector;

import java.io.IOException;

/**
 * Wrap an IOException identifying it as being caused by an abort
 * of a request by a remote client.
 *
 * @author Glenn L. Nielsen
 * @version $Id$
 */

public final class ClientAbortException extends IOException {

    private static final long serialVersionUID = 1L;


    //------------------------------------------------------------ Constructors

    /**
     * Construct a new ClientAbortException with no other information.
     */
    public ClientAbortException() {

        this(null, null);

    }


    /**
     * Construct a new ClientAbortException for the specified message.
     *
     * @param message Message describing this exception
     */
    public ClientAbortException(String message) {

        this(message, null);

    }


    /**
     * Construct a new ClientAbortException for the specified throwable.
     *
     * @param throwable Throwable that caused this exception
     */
    public ClientAbortException(Throwable throwable) {

        this(null, throwable);

    }


    /**
     * Construct a new ClientAbortException for the specified message
     * and throwable.
     *
     * @param message Message describing this exception
     * @param throwable Throwable that caused this exception
     */
    public ClientAbortException(String message, Throwable throwable) {

        super();
        this.message = message;
        this.throwable = throwable;

    }


    //------------------------------------------------------ Instance Variables


    /**
     * The error message passed to our constructor (if any)
     */
    protected String message = null;


    /**
     * The underlying exception or error passed to our constructor (if any)
     */
    protected Throwable throwable = null;


    //---------------------------------------------------------- Public Methods


    /**
     * Returns the message associated with this exception, if any.
     */
    @Override
    public String getMessage() {

        return (message);

    }


    /**
     * Returns the cause that caused this exception, if any.
     */
    @Override
    public Throwable getCause() {

        return (throwable);

    }


    /**
     * Return a formatted string that describes this exception.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ClientAbortException:  ");
        if (message != null) {
            sb.append(message);
            if (throwable != null) {
                sb.append(":  ");
            }
        }
        if (throwable != null) {
            sb.append(throwable.toString());
        }
        return (sb.toString());

    }


}
