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
package org.apache.catalina.ha.tcp;

import org.apache.catalina.tribes.Member;

/**
 * Data about a message send operation.
 * @param message the message that was sent
 * @param destination the destination of the message
 * @param exception the exception, if any, when attempting to send the message
 */
public record SendMessageData(Object message, Member destination, Exception exception) {

    /**
     * Returns the destination of the message.
     * @return the destination
     */
    public Member getDestination() {
        return destination;
    }

    /**
     * Returns the exception that occurred when attempting to send the message.
     * @return the exception
     */
    public Exception getException() {
        return exception;
    }

    /**
     * Returns the message that was sent.
     * @return the message
     */
    public Object getMessage() {
        return message;
    }

}
