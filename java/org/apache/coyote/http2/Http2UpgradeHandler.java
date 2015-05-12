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

import javax.servlet.http.WebConnection;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * This represents an HTTP/2 connection from a client to Tomcat. It is designed
 * on the basis that there will never be more than one thread performing I/O at
 * a time.
 * <br>
 * Currently, it appears that Firefox needs to be configured with
 * network.http.spdy.enforce-tls-profile=false in order for FireFox to be able
 * to connect. I'm not sure what is going wrong here since as far as I have
 * found that only requires TLSv1.2. openssl s_client and Wireshark confirm that
 * TLSv1.2 is used and it still doesn't work if I limit the HTTPS connector to
 * TLSv1.2. There looks to be some other restriction being applied.
 *
 */
public class Http2UpgradeHandler implements InternalHttpUpgradeHandler {

    private static final Log log = LogFactory.getLog(Http2UpgradeHandler.class);
    private static final StringManager sm = StringManager.getManager(Http2UpgradeHandler.class);
    private static final byte[] CLIENT_PREFACE_START_EXPECTED;

    private volatile SocketWrapperBase<?> socketWrapper;
    private volatile boolean initialized = false;
    private volatile byte[] clientPrefaceStartData = new byte[CLIENT_PREFACE_START_EXPECTED.length];
    private volatile int clientPrefaceStartBytesRead = 0;
    private volatile boolean readFirstFrame = false;


    static {
        CLIENT_PREFACE_START_EXPECTED =
                "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
    }


    @Override
    public void init(WebConnection unused) {
        initialized = true;
    }


    @Override
    public void setSocketWrapper(SocketWrapperBase<?> wrapper) {
        this.socketWrapper = wrapper;
    }


    @Override
    public SocketState upgradeDispatch(SocketStatus status) {
        if (!initialized) {
            // WebConnection is not used so passing null here is fine
            init(null);
        }

        if (clientPrefaceStartBytesRead < CLIENT_PREFACE_START_EXPECTED.length) {
            readClientPrefaceStart();
            if (clientPrefaceStartBytesRead == -1) {
                // A fatal (for this connection) error occurred
                close();
                return SocketState.CLOSED;
            }
            // Preface start has been read and validated. No need to keep this
            // buffer hanging around in memory.
            clientPrefaceStartData = null;
        }

        // TODO This is for debug purposes to make sure ALPN is working.
        log.fatal("TODO: Handle SocketStatus: " + status);

        close();
        return SocketState.CLOSED;
    }


    @Override
    public void destroy() {
        // NO-OP
    }


    private void close() {
        try {
            socketWrapper.close();
        } catch (IOException ioe) {
            log.debug(sm.getString("upgradeHandler.socketCloseFailed"), ioe);
        }
    }


    private void readClientPrefaceStart() {
        int read = 0;
        try {
            read = socketWrapper.read(false, clientPrefaceStartData, clientPrefaceStartBytesRead,
                    clientPrefaceStartData.length - clientPrefaceStartBytesRead);
        } catch (IOException ioe) {
            log.error(sm.getString("upgradeHandler.prefaceErrorIo"), ioe);
            clientPrefaceStartBytesRead = -1;
            return;
        }

        if (read == -1) {
            log.error(sm.getString("upgradeHandler.prefaceErrorEos",
                    Integer.toString(clientPrefaceStartBytesRead)));
            clientPrefaceStartBytesRead = -1;
            return;
        }

        for (int i = clientPrefaceStartBytesRead; i < (clientPrefaceStartBytesRead + read); i++) {
            if (clientPrefaceStartData[i] != CLIENT_PREFACE_START_EXPECTED[i]) {
                log.error(sm.getString("upgradeHandler.prefaceErrorMismatch",
                        new String(clientPrefaceStartData, StandardCharsets.ISO_8859_1)));
                clientPrefaceStartBytesRead = -1;
                return;
            }
        }
        clientPrefaceStartBytesRead += read;
    }
}
