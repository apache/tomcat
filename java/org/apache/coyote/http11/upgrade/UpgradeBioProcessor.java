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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.apache.coyote.http11.Http11Processor;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Implementation note: The need to extend Http11Processor could probably be
 * removed if the Processor interface was expanded to cover all of the methods
 * required by the AbstractProtocol. That would simplify the code and further
 * reduce the size of instances of this class.
 */
public class UpgradeBioProcessor extends Http11Processor
        implements UpgradeProcessor{

    private InputStream inputStream;
    private OutputStream outputStream;

    public UpgradeBioProcessor(SocketWrapper<Socket> wrapper,
            UpgradeInbound inbound) throws IOException {
        this.inputStream = wrapper.getSocket().getInputStream();
        this.outputStream = wrapper.getSocket().getOutputStream();
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
        outputStream.flush();
    }


    @Override
    public void write(int b) throws IOException {
        outputStream.write(b);
    }


    /*
     * Input methods
     */
    @Override
    public int read() throws IOException {
        return inputStream.read();
    }


    @Override
    public int read(byte[] bytes) throws IOException {
        return inputStream.read(bytes);
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
    public SocketState process(SocketWrapper<Socket> socketWrapper)
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
