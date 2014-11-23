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
package org.apache.coyote.ajp;

import java.io.IOException;
import java.nio.channels.SelectionKey;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * Processes AJP requests using NIO.
 */
public class AjpNioProcessor extends AbstractAjpProcessor<NioChannel> {

    private static final Log log = LogFactory.getLog(AjpNioProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }


    public AjpNioProcessor(int packetSize, NioEndpoint endpoint) {

        super(packetSize, endpoint);

        response.setOutputBuffer(new SocketOutputBuffer());
    }


    @Override
    protected void registerForEvent(boolean read, boolean write) {
        final NioChannel socket = socketWrapper.getSocket();
        final NioEndpoint.NioSocketWrapper attach =
                (NioEndpoint.NioSocketWrapper) socket.getAttachment(false);
        if (attach == null) {
            return;
        }
        SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
        if (read) {
            attach.interestOps(attach.interestOps() | SelectionKey.OP_READ);
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        }
        if (write) {
            attach.interestOps(attach.interestOps() | SelectionKey.OP_WRITE);
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        }
    }


    @Override
    protected void setupSocket(SocketWrapperBase<NioChannel> socketWrapper)
            throws IOException {
        // NO-OP
    }
}
