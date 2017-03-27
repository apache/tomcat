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
package org.apache.tomcat.util.net;

public abstract class SendfileDataBase {

    /**
     * Is the current request being processed on a keep-alive connection? This
     * determines if the socket is closed once the send file completes or if
     * processing continues with the next request on the connection or waiting
     * for that next request to arrive.
     */
    public SendfileKeepAliveState keepAliveState = SendfileKeepAliveState.NONE;

    /**
     * The full path to the file that contains the data to be written to the
     * socket.
     */
    public final String fileName;

    /**
     * The position of the next byte in the file to be written to the socket.
     * This is initialised to the start point and then updated as the file is
     * written.
     */
    public long pos;

    /**
     * The number of bytes remaining to be written from the file (from the
     * current {@link #pos}. This is initialised to the end point - the start
     * point and then updated as the file is written.
     */
    public long length;

    public SendfileDataBase(String filename, long pos, long length) {
        this.fileName = filename;
        this.pos = pos;
        this.length = length;
    }
}
