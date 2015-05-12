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

import javax.servlet.http.WebConnection;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * This represents an HTTP/2 connection from a client to Tomcat. It is designed
 * on the basis that there will never be more than one thread performing I/O at
 * a time.
 */
public class Http2UpgradeHandler implements InternalHttpUpgradeHandler {

    private static final Log log = LogFactory.getLog(Http2UpgradeHandler.class);

    private SocketWrapperBase<?> socketWrapper;
    private volatile boolean initialized = false;


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

        // TODO This is for debug purposes to make sure ALPN is working.
        log.fatal("TODO: Handle SocketStatus: " + status);

        try {
            socketWrapper.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return SocketState.CLOSED;
    }


    @Override
    public void destroy() {
        // NO-OP
    }
}
