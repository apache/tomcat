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
package org.apache.catalina.tribes.group;

import java.io.Serializable;

import org.apache.catalina.tribes.Member;

/**
 * A response object holds a message from a responding partner.
 */
public class Response {
    private Member source;
    private Serializable message;

    /**
     * Default constructor.
     */
    public Response() {
    }

    /**
     * Constructor with source and message.
     *
     * @param source The source member
     * @param message The message
     */
    public Response(Member source, Serializable message) {
        this.source = source;
        this.message = message;
    }

    /**
     * Set the source member.
     *
     * @param source The source member
     */
    public void setSource(Member source) {
        this.source = source;
    }

    /**
     * Set the message.
     *
     * @param message The message
     */
    public void setMessage(Serializable message) {
        this.message = message;
    }

    /**
     * Get the source member.
     *
     * @return The source member
     */
    public Member getSource() {
        return source;
    }

    /**
     * Get the message.
     *
     * @return The message
     */
    public Serializable getMessage() {
        return message;
    }
}