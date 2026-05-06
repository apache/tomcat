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
package org.apache.catalina.ha.deploy;

import java.io.Serial;

import org.apache.catalina.ha.ClusterMessageBase;
import org.apache.catalina.tribes.Member;

/**
 * Contains the data for a file being transferred over TCP, this is essentially a fragment of a file, read and written
 * by the FileMessageFactory.
 */

public class FileMessage extends ClusterMessageBase {
    @Serial
    private static final long serialVersionUID = 2L;

    private int messageNumber;
    private byte[] data;
    private int dataLength;

    private long totalNrOfMsgs;
    private final String fileName;
    private final String contextName;

    /**
     * Creates a new FileMessage for transferring a file.
     *
     * @param source The member that is the source of this message
     * @param fileName The name of the file being transferred
     * @param contextName The name of the context associated with this file
     */
    public FileMessage(Member source, String fileName, String contextName) {
        this.address = source;
        this.fileName = fileName;
        this.contextName = contextName;
    }

    /**
     * Returns the message number within the file transfer sequence.
     *
     * @return The message number
     */
    public int getMessageNumber() {
        return messageNumber;
    }

    /**
     * Sets the message number within the file transfer sequence.
     *
     * @param messageNumber The message number
     */
    public void setMessageNumber(int messageNumber) {
        this.messageNumber = messageNumber;
    }

    /**
     * Returns the total number of messages in the file transfer.
     *
     * @return The total number of messages
     */
    public long getTotalNrOfMsgs() {
        return totalNrOfMsgs;
    }

    /**
     * Sets the total number of messages in the file transfer.
     *
     * @param totalNrOfMsgs The total number of messages
     */
    public void setTotalNrOfMsgs(long totalNrOfMsgs) {
        this.totalNrOfMsgs = totalNrOfMsgs;
    }

    /**
     * Returns the data payload of this message.
     *
     * @return The data byte array
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Sets the data payload and its length for this message.
     *
     * @param data The data byte array
     * @param length The length of valid data in the array
     */
    public void setData(byte[] data, int length) {
        this.data = data;
        this.dataLength = length;
    }

    /**
     * Returns the length of the valid data in the data array.
     *
     * @return The data length
     */
    public int getDataLength() {
        return dataLength;
    }

    @Override
    public String getUniqueId() {
        return getFileName() + "#-#" + getMessageNumber() + "#-#" + System.currentTimeMillis();
    }


    /**
     * Returns the name of the file being transferred.
     *
     * @return The file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the context name associated with this file transfer.
     *
     * @return The context name
     */
    public String getContextName() {
        return contextName;
    }
}
