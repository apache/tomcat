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
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class StreamProcessor extends AbstractProcessor implements Runnable {

    private static final Log log = LogFactory.getLog(StreamProcessor.class);
    private static final StringManager sm = StringManager.getManager(StreamProcessor.class);

    private final Stream stream;

    private volatile SSLSupport sslSupport;


    public StreamProcessor(Stream stream, Adapter adapter, SocketWrapperBase<?> socketWrapper) {
        super(stream.getCoyoteRequest(), stream.getCoyoteResponse());
        this.stream = stream;
        setAdapter(adapter);
        setSocketWrapper(socketWrapper);
    }


    @Override
    public void run() {
        try {
            adapter.service(request, response);
            // Ensure the response is complete
            response.action(ActionCode.CLOSE, null);
        } catch (Exception e) {
            // TODO
            e.printStackTrace();
        }
    }


    @Override
    public void action(ActionCode actionCode, Object param) {
        switch (actionCode) {
        case COMMIT: {
            if (!response.isCommitted()) {
                response.setCommitted(true);
                stream.writeHeaders();
            }
            break;
        }
        case CLOSE: {
            // Tell the output buffer there will be no more data
            stream.getOutputBuffer().finished();
            // Then flush it
            action(ActionCode.CLIENT_FLUSH, null);
            break;
        }
        case CLIENT_FLUSH: {
            action(ActionCode.COMMIT, null);
            stream.flushData();
            break;
        }
        case REQ_HOST_ADDR_ATTRIBUTE: {
            request.remoteAddr().setString(socketWrapper.getRemoteAddr());
            break;
        }
        case IS_ERROR: {
            ((AtomicBoolean) param).set(getErrorState().isError());
            break;
        }

        //case REQ_HOST_ATTRIBUTE: {
        //    request.remoteHost().setString(socketWrapper.getRemoteHost());
        //    break;
        //}
        default:
            // TODO
            log.debug("TODO: Action: " + actionCode);
        }
    }


    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }


    @Override
    public void recycle() {
        // StreamProcessor instances are not re-used.
        // Clear fields that can be cleared to aid GC and trigger NPEs if this
        // is reused
        setSocketWrapper(null);
        setAdapter(null);
        setClientCertProvider(null);
    }


    @Override
    public boolean isUpgrade() {
        return false;
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    public SocketState process(SocketWrapperBase<?> socket) throws IOException {
        // Should never happen
        throw new IllegalStateException(sm.getString("streamProcessor.httpupgrade.notsupported"));
    }


    @Override
    public SocketState dispatch(SocketStatus status) {
        // Should never happen
        throw new IllegalStateException(sm.getString("streamProcessor.httpupgrade.notsupported"));
    }


    @Override
    public HttpUpgradeHandler getHttpUpgradeHandler() {
        // Should never happen
        throw new IllegalStateException(sm.getString("streamProcessor.httpupgrade.notsupported"));
    }


    @Override
    public ByteBuffer getLeftoverInput() {
        // Should never happen
        throw new IllegalStateException(sm.getString("streamProcessor.httpupgrade.notsupported"));
    }
}
