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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * Processes AJP requests using NIO2.
 */
public class AjpNio2Processor extends AbstractAjpProcessor<Nio2Channel> {

    private static final Log log = LogFactory.getLog(AjpNio2Processor.class);
    @Override
    protected Log getLog() {
        return log;
    }

    public AjpNio2Processor(int packetSize, Nio2Endpoint endpoint0) {
        super(packetSize, endpoint0);
    }

    @Override
    public void recycle(boolean socketClosing) {
        super.recycle(socketClosing);
    }

    @Override
    protected void registerForEvent(boolean read, boolean write) {
        // Nothing to do here, the appropriate operations should
        // already be pending
    }


    @Override
    protected void setupSocket(SocketWrapperBase<Nio2Channel> socketWrapper)
            throws IOException {
        // NO-OP
    }
}
