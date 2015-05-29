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
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * Parser for the initial part of the client connection preface received by the
 * server.
 */
public class ConnectionPrefaceParser {

    private static final Log log = LogFactory.getLog(ConnectionPrefaceParser.class);
    private static final StringManager sm = StringManager.getManager(ConnectionPrefaceParser.class);

    private static final byte[] EXPECTED =
            "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

    private volatile byte[] data = new byte[EXPECTED.length];
    private volatile int pos = 0;
    private volatile boolean error = false;


    public boolean parse(SocketWrapperBase<?> socketWrapper, boolean block) {
        int read = 0;
        try {
            read = socketWrapper.read(block, data, pos, EXPECTED.length - pos);
        } catch (IOException ioe) {
            log.error(sm.getString("connectionPrefaceParser.ioError"), ioe);
            error = true;
            return false;
        }

        if (read == -1) {
            log.error(sm.getString("connectionPrefaceParser.eos", Integer.toString(pos)));
            error = true;
            return false;
        }

        for (int i = pos; i < (pos + read); i++) {
            if (data[i] != EXPECTED[i]) {
                log.error(sm.getString("connectionPrefaceParser.mismatch",
                        new String(data, StandardCharsets.ISO_8859_1)));
                error = true;
                return false;
            }
        }

        pos += read;
        return pos == EXPECTED.length;
    }


    public boolean isError() {
        return error;
    }
}
