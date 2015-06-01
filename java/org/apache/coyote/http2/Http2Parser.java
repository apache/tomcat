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
package org.apache.coyote.http2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

class Http2Parser {

    private static final Log log = LogFactory.getLog(Http2Parser.class);
    private static final StringManager sm = StringManager.getManager(Http2Parser.class);

    private static final byte[] CLIENT_PREFACE_START =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n ".getBytes(StandardCharsets.ISO_8859_1);

    private final Input input;
    private final byte[] frameHeaderBuffer = new byte[9];

    private volatile boolean readPreface = false;

    Http2Parser(Input input) {
        this.input = input;
    }


    /**
     * Read and process a single frame. Once the start of a frame is read, the
     * remainder will be read using blocking IO.
     *
     * @param block Should this method block until a frame is available is no
     *              frame is available immediately?
     *
     * @throws IOException If an IO error occurs while trying to read a frame
     */
    public void readFrame(boolean block) throws IOException {
        input.fill(block, frameHeaderBuffer);

        // TODO: This is incomplete
    }


    /**
     * Read and validate the connection preface from input using blocking IO.
     *
     * @return <code>true</code> if a valid preface was read, otherwise false.
     */
    boolean readConnectionPreface() {
        if (readPreface) {
            return true;
        }

        byte[] data = new byte[CLIENT_PREFACE_START.length];
        try {
            input.fill(true, data);
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http2Parser.preface.io"), ioe);
            }
            return false;
        }

        for (int i = 0; i < CLIENT_PREFACE_START.length; i++) {
            if (CLIENT_PREFACE_START[i] != data[i]) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("http2Parser.preface.invalid",
                            new String(data, StandardCharsets.ISO_8859_1)));
                }
                return false;
            }
        }

        readPreface = true;
        return true;
    }


    /**
     * Interface that must be implemented by the source of data for the parser.
     */
    static interface Input {

        /**
         * Fill the given array with data unless non-blocking is requested and
         * no data is available. If any data is available then the buffer will
         * be filled with blocking I/O.
         *
         * @param block Should the first read into the provided buffer be a
         *              blocking read or not.
         * @param data  Buffer to fill
         *
         * @return <code>true</code> if the buffer was filled otherwise
         *         <code>false</code>
         *
         * @throws IOException If an I/O occurred while obtaining data with
         *                     which to fill the buffer
         */
        boolean fill(boolean block, byte[] data) throws IOException;
    }
}
