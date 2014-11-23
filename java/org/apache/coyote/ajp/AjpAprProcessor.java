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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * Processes AJP requests.
 *
 * @author Remy Maucherat
 * @author Henri Gomez
 * @author Dan Milstein
 * @author Keith Wannamaker
 * @author Kevin Seguin
 * @author Costin Manolache
 */
public class AjpAprProcessor extends AbstractAjpProcessor<Long> {

    private static final Log log = LogFactory.getLog(AjpAprProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }


    public AjpAprProcessor(int packetSize, AprEndpoint endpoint) {
        super(packetSize, endpoint);
    }


    @Override
    protected void registerForEvent(boolean read, boolean write) {
        ((AprEndpoint) endpoint).getPoller().add(
                socketWrapper.getSocket().longValue(), -1, read, write);
    }


    @Override
    protected void setupSocket(SocketWrapperBase<Long> socketWrapper) {
        // NO-OP
    }
}
