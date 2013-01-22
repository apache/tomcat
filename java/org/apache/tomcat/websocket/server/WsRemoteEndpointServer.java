/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import javax.servlet.ServletOutputStream;

import org.apache.tomcat.websocket.Constants;
import org.apache.tomcat.websocket.WsRemoteEndpointBase;
import org.apache.tomcat.websocket.WsSession;

/**
 * This is the server side {@link javax.websocket.RemoteEndpoint} implementation
 * - i.e. what the server uses to send data to the client. Communication is over
 * a {@link ServletOutputStream}.
 */
public class WsRemoteEndpointServer extends WsRemoteEndpointBase {

    private final WsSession wsSession;
    private final ServletOutputStream sos;
    private final Object messageWriteLock = new Object();

    private volatile CyclicBarrier writeBarrier = new CyclicBarrier(2);


    public WsRemoteEndpointServer(WsSession wsSession,
            ServletOutputStream sos) {
        this.wsSession = wsSession;
        this.sos = sos;
    }


    protected void onWritePossible() {
        try {
            writeBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    @Override
    protected void writeMessage(int opCode, ByteBuffer header,
            ByteBuffer message) {
        // Could sync on sos but don't as other (user or container) code may
        // sync on this creating the potential for deadlocks.
        synchronized (messageWriteLock) {
            doBlockingWrite(header);
            doBlockingWrite(message);
            try {
                sos.flush();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (Constants.OPCODE_CLOSE == opCode) {
                try {
                    sos.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        if (opCode == Constants.OPCODE_CLOSE) {
            // Connection is closing - ensure no threads are stuck waiting on
            // the write barrier
            writeBarrier.reset();
        }
    }


    private void doBlockingWrite(ByteBuffer data) {
        if (!sos.canWrite()) {
            try {
                writeBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                wsSession.getLocalEndpoint().onError(wsSession, e);
            }
        }
        try {
            sos.write(data.array(), data.arrayOffset(), data.limit());
        } catch (IOException e) {
            wsSession.getLocalEndpoint().onError(wsSession, e);
        }
    }
}
