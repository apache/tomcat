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
import java.nio.ByteBuffer;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.WebConnection;

import org.apache.coyote.AbstractProcessorLight;
import org.apache.coyote.Request;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketWrapperBase;

public abstract class UpgradeProcessorBase extends AbstractProcessorLight implements WebConnection {

    protected static final int INFINITE_TIMEOUT = -1;

    private final HttpUpgradeHandler httpUpgradeHandler;

    public UpgradeProcessorBase(SocketWrapperBase<?> wrapper, ByteBuffer leftOverInput,
            HttpUpgradeHandler httpUpgradeHandler) {
        this.httpUpgradeHandler = httpUpgradeHandler;
        wrapper.unRead(leftOverInput);
    }


    // ------------------------------------------- Implemented Processor methods

    @Override
    public final boolean isUpgrade() {
        return true;
    }


    @Override
    public HttpUpgradeHandler getHttpUpgradeHandler() {
        return httpUpgradeHandler;
    }


    @Override
    public final void recycle() {
        // Currently a NO-OP as upgrade processors are not recycled.
    }


    // ---------------------------- Processor methods that are NO-OP for upgrade

    @Override
    public final SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException {
        return null;
    }


    @Override
    public void errorDispatch() {
        // NO-OP
    }


    @Override
    public final SocketState asyncPostProcess() {
        return null;
    }


    @Override
    public final boolean isAsync() {
        return false;
    }


    @Override
    public final Request getRequest() {
        return null;
    }


    @Override
    public ByteBuffer getLeftoverInput() {
        return null;
    }


    @Override
    public void timeoutAsync(long now) {
        // NO-OP
    }
}
