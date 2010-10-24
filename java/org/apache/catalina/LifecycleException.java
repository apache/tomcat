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


package org.apache.catalina;


/**
 * General purpose exception that is thrown to indicate a lifecycle related
 * problem.  Such exceptions should generally be considered fatal to the
 * operation of the application containing this component.
 *
 * @author Craig R. McClanahan
 * @version $Id$
 */

public final class LifecycleException extends Exception {

    private static final long serialVersionUID = 1L;

    //------------------------------------------------------------ Constructors


    /**
     * Construct a new LifecycleException with no other information.
     */
    public LifecycleException() {

        this(null, null);

    }


    /**
     * Construct a new LifecycleException for the specified message.
     *
     * @param message Message describing this exception
     */
    public LifecycleException(String message) {

        this(message, null);

    }


    /**
     * Construct a new LifecycleException for the specified throwable.
     *
     * @param throwable Throwable that caused this exception
     */
    public LifecycleException(Throwable throwable) {

        this(null, throwable);

    }


    /**
     * Construct a new LifecycleException for the specified message
     * and throwable.
     *
     * @param message Message describing this exception
     * @param throwable Throwable that caused this exception
     */
    public LifecycleException(String message, Throwable throwable) {

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
     * Returns the throwable that caused this exception, if any.
     */
    public Throwable getThrowable() {

        return (throwable);

    }


    /**
     * Return a formatted string that describes this exception.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("LifecycleException:  ");
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
