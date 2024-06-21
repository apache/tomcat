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
package org.apache.coyote;

import java.io.IOException;

/**
 * Extend IOException to identify it as being caused by a bad request from a remote client.
 */
public class BadRequestException extends IOException {

    private static final long serialVersionUID = 1L;


    // ------------------------------------------------------------ Constructors

    /**
     * Construct a new BadRequestException with no other information.
     */
    public BadRequestException() {
        super();
    }


    /**
     * Construct a new BadRequestException for the specified message.
     *
     * @param message Message describing this exception
     */
    public BadRequestException(String message) {
        super(message);
    }


    /**
     * Construct a new BadRequestException for the specified throwable.
     *
     * @param throwable Throwable that caused this exception
     */
    public BadRequestException(Throwable throwable) {
        super(throwable);
    }


    /**
     * Construct a new BadRequestException for the specified message and throwable.
     *
     * @param message   Message describing this exception
     * @param throwable Throwable that caused this exception
     */
    public BadRequestException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
