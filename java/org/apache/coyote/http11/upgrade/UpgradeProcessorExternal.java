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
package org.apache.coyote.http11.upgrade;

import java.io.IOException;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;

import org.apache.coyote.UpgradeToken;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class UpgradeProcessorExternal extends UpgradeProcessorBase {

    private static final Log log = LogFactory.getLog(UpgradeProcessorExternal.class);
    private static final StringManager sm = StringManager.getManager(UpgradeProcessorExternal.class);

    private final UpgradeServletInputStream upgradeServletInputStream;
    private final UpgradeServletOutputStream upgradeServletOutputStream;
    private final UpgradeInfo upgradeInfo;

    public UpgradeProcessorExternal(SocketWrapperBase<?> wrapper, UpgradeToken upgradeToken,
            UpgradeGroupInfo upgradeGroupInfo) {
        super(upgradeToken);
        this.upgradeInfo = new UpgradeInfo();
        upgradeGroupInfo.addUpgradeInfo(upgradeInfo);
        this.upgradeServletInputStream = new UpgradeServletInputStream(this, wrapper, upgradeInfo);
        this.upgradeServletOutputStream = new UpgradeServletOutputStream(this, wrapper, upgradeInfo);

        /*
         * Leave timeouts in the hands of the upgraded protocol.
         */
        wrapper.setReadTimeout(INFINITE_TIMEOUT);
        wrapper.setWriteTimeout(INFINITE_TIMEOUT);
    }


    @Override
    protected Log getLog() {
        return log;
    }


    // --------------------------------------------------- AutoCloseable methods

    @Override
    public void close() throws Exception {
        upgradeServletInputStream.close();
        upgradeServletOutputStream.close();
        // Triggers update of stats from UpgradeInfo to UpgradeGroupInfo
        upgradeInfo.setGroupInfo(null);
    }


    // --------------------------------------------------- WebConnection methods

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return upgradeServletInputStream;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return upgradeServletOutputStream;
    }


    // ------------------------------------------- Implemented Processor methods

    @Override
    public final SocketState dispatch(SocketEvent status) {
        if (status == SocketEvent.OPEN_READ) {
            upgradeServletInputStream.onDataAvailable();
        } else if (status == SocketEvent.OPEN_WRITE) {
            upgradeServletOutputStream.onWritePossible();
        } else if (status == SocketEvent.STOP) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeProcessor.stop"));
            }
            try {
                upgradeServletInputStream.close();
            } catch (IOException ioe) {
                log.debug(sm.getString("upgradeProcessor.isCloseFail", ioe));
            }
            try {
                upgradeServletOutputStream.close();
            } catch (IOException ioe) {
                log.debug(sm.getString("upgradeProcessor.osCloseFail", ioe));
            }
            return SocketState.CLOSED;
        } else {
            // Unexpected state
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeProcessor.unexpectedState"));
            }
            return SocketState.CLOSED;
        }
        if (upgradeServletInputStream.isClosed() &&
                upgradeServletOutputStream.isClosed()) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("upgradeProcessor.requiredClose",
                        Boolean.valueOf(upgradeServletInputStream.isClosed()),
                        Boolean.valueOf(upgradeServletOutputStream.isClosed())));
            }
            return SocketState.CLOSED;
        }
        return SocketState.UPGRADED;
    }


    // ----------------------------------------- Unimplemented Processor methods

    @Override
    public final void setSslSupport(SSLSupport sslSupport) {
        // NO-OP
    }


    @Override
    public void pause() {
        // NOOP
    }
}
