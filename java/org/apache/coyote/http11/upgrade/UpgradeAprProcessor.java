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

import javax.servlet.http.ProtocolHandler;

import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.util.net.SocketWrapper;

public class UpgradeAprProcessor extends UpgradeProcessor<Long> {

    private static final int INFINITE_TIMEOUT = -1;

    public UpgradeAprProcessor(SocketWrapper<Long> wrapper,
            ProtocolHandler httpUpgradeProcessor) {
        super(httpUpgradeProcessor,
                new AprUpgradeServletInputStream(wrapper),
                new AprUpgradeServletOutputStream(wrapper.getSocket().longValue()));

        Socket.timeoutSet(wrapper.getSocket().longValue(), INFINITE_TIMEOUT);
    }


    // ----------------------------------------------------------- Inner classes

    private static class AprUpgradeServletOutputStream
            extends UpgradeServletOutputStream {

        private final long socket;

        public AprUpgradeServletOutputStream(long socket) {
            this.socket = socket;
        }

        @Override
        protected void doWrite(int b) throws IOException {
            Socket.send(socket, new byte[] {(byte) b}, 0, 1);
        }

        @Override
        protected void doWrite(byte[] b, int off, int len) throws IOException {
            Socket.send(socket, b, off, len);
        }

        @Override
        protected void doFlush() throws IOException {
            // NO-OP
        }
    }
}
