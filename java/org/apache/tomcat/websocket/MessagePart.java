/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.nio.ByteBuffer;

import javax.websocket.SendHandler;

/**
 * Represents a part of a WebSocket message.
 */
public class MessagePart {
    /** Whether this is the final part. */
    private final boolean fin;
    /** Reserved bits. */
    private final int rsv;
    /** Operation code. */
    private final byte opCode;
    /** Payload data. */
    private final ByteBuffer payload;
    /** Intermediate send handler. */
    private final SendHandler intermediateHandler;
    /** End send handler. */
    private volatile SendHandler endHandler;
    /** Blocking write timeout expiry. */
    private final long blockingWriteTimeoutExpiry;

    /**
     * Constructor.
     * @param fin whether this is the final part
     * @param rsv reserved bits
     * @param opCode operation code
     * @param payload payload data
     * @param intermediateHandler intermediate send handler
     * @param endHandler end send handler
     * @param blockingWriteTimeoutExpiry blocking write timeout expiry
     */
    MessagePart(boolean fin, int rsv, byte opCode, ByteBuffer payload, SendHandler intermediateHandler,
            SendHandler endHandler, long blockingWriteTimeoutExpiry) {
        this.fin = fin;
        this.rsv = rsv;
        this.opCode = opCode;
        this.payload = payload;
        this.intermediateHandler = intermediateHandler;
        this.endHandler = endHandler;
        this.blockingWriteTimeoutExpiry = blockingWriteTimeoutExpiry;
    }

    /**
     * Check if this is the final part.
     * @return true if final
     */
    public boolean isFin() {
        return fin;
    }

    /**
     * Get the reserved bits.
     * @return the reserved bits
     */
    public int getRsv() {
        return rsv;
    }

    /**
     * Get the operation code.
     * @return the operation code
     */
    public byte getOpCode() {
        return opCode;
    }

    /**
     * Get the payload.
     * @return the payload
     */
    public ByteBuffer getPayload() {
        return payload;
    }

    /**
     * Get the intermediate handler.
     * @return the intermediate handler
     */
    public SendHandler getIntermediateHandler() {
        return intermediateHandler;
    }

    /**
     * Get the end handler.
     * @return the end handler
     */
    public SendHandler getEndHandler() {
        return endHandler;
    }

    /**
     * Set the end handler.
     * @param endHandler the end handler
     */
    public void setEndHandler(SendHandler endHandler) {
        this.endHandler = endHandler;
    }

    /**
     * Get the blocking write timeout expiry.
     * @return the blocking write timeout expiry
     */
    public long getBlockingWriteTimeoutExpiry() {
        return blockingWriteTimeoutExpiry;
    }
}

