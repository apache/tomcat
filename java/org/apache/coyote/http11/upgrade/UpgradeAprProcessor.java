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

import org.apache.coyote.http11.Http11AprProcessor;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Implementation note: The need to extend Http11Processor could probably be
 * removed if the Processor interface was expanded to cover all of the methods
 * required by the AbstractProtocol. That would simplify the code and further
 * reduce the size of instances of this class.
 */
public class UpgradeAprProcessor extends Http11AprProcessor
        implements UpgradeProcessor {

    long socket;

    public UpgradeAprProcessor(SocketWrapper<Long> wrapper,
            UpgradeInbound inbound) {
        this.socket = wrapper.getSocket().longValue();

        this.upgradeInbound = inbound;
        upgradeInbound.setUpgradeProcessor(this);
        upgradeInbound.setUpgradeOutbound(new UpgradeOutbound(this));
        // Remove the default - no need for it here
        this.compressableMimeTypes = null;
    }


    @Override
    public SocketState upgradeDispatch() throws IOException {
        return upgradeInbound.onData();
    }


    /*
     * Output methods
     */
    @Override
    public void flush() throws IOException {
        // NOOP
    }


    @Override
    public void write(int b) throws IOException {
        Socket.send(socket, new byte[] {(byte) b}, 0, 1);
    }


    /*
     * Input methods
     */
    @Override
    public int read() throws IOException {
        byte[] bytes = new byte[1];
        Socket.recv(socket, bytes, 0, 1);
        return bytes[0];
    }


    @Override
    public int read(byte[] bytes) throws IOException {
        return Socket.recv(socket, bytes, 0, bytes.length);
    }


    /*
     * None of the following NO-OP methods are strictly necessary - assuming the
     * there are no bugs in the connector code that cause upgraded connections
     * to be treated as Http11, Comet or Async. These NO-OP methods are here for
     * safety and to aid debugging during development.
     */

    @Override
    public SocketState event(SocketStatus status) throws IOException {
        // TODO Log an error
        return SocketState.CLOSED;
    }


    @Override
    public SocketState process(SocketWrapper<Long> socketWrapper)
            throws IOException {
        // TODO Log an error
        return SocketState.CLOSED;
    }


    @Override
    public SocketState asyncDispatch(SocketStatus status) {
        // TODO Log an error
        return SocketState.CLOSED;
    }


    @Override
    public SocketState asyncPostProcess() {
        // TODO Log an error
        return SocketState.CLOSED;
    }

}
