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

import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.util.net.SocketWrapper;

public class UpgradeAprProcessor extends UpgradeProcessor<Long> {

    private final long socket;


    public UpgradeAprProcessor(SocketWrapper<Long> wrapper,
            UpgradeInbound upgradeInbound) {
        super(upgradeInbound);

        this.socket = wrapper.getSocket().longValue();
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


    @Override
    public void write(byte[]b, int off, int len) throws IOException {
        Socket.send(socket, b, off, len);
    }


    /*
     * Input methods
     */
    @Override
    public int read() throws IOException {
        byte[] bytes = new byte[1];
        int result = Socket.recv(socket, bytes, 0, 1);
        if (result == -1) {
            return -1;
        } else {
            return bytes[0] & 0xFF;
        }
    }


    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        return Socket.recv(socket, bytes, off, len);
    }
}
