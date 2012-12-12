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
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import javax.servlet.ServletOutputStream;
import javax.websocket.EncodeException;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;

public class WsRemoteEndpoint implements RemoteEndpoint {

    private final ServletOutputStream sos;

    public WsRemoteEndpoint(ServletOutputStream sos) {
        this.sos = sos;
    }

    public void onWritePossible() {
        // TODO
    }

    @Override
    public void sendString(String text) throws IOException {
        // TODO Auto-generated method stub
    }


    @Override
    public void sendBytes(ByteBuffer data) throws IOException {
        // TODO Auto-generated method stub
    }


    @Override
    public void sendPartialString(String fragment, boolean isLast)
            throws IOException {
        // TODO Auto-generated method stub
    }


    @Override
    public void sendPartialBytes(ByteBuffer partialByte, boolean isLast)
            throws IOException {
        // TODO Auto-generated method stub
    }


    @Override
    public OutputStream getSendStream() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Writer getSendWriter() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void sendObject(Object o) throws IOException, EncodeException {
        // TODO Auto-generated method stub
    }


    @Override
    public void sendStringByCompletion(String text, SendHandler completion) {
        // TODO Auto-generated method stub
    }


    @Override
    public Future<SendResult> sendStringByFuture(String text) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Future<SendResult> sendBytesByFuture(ByteBuffer data) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void sendBytesByCompletion(ByteBuffer data, SendHandler completion) {
        // TODO Auto-generated method stub
    }


    @Override
    public Future<SendResult> sendObjectByFuture(Object obj) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void sendObjectByCompletion(Object obj, SendHandler completion) {
        // TODO Auto-generated method stub
    }


    @Override
    public void sendPing(ByteBuffer applicationData) {
        // TODO Auto-generated method stub
    }


    @Override
    public void sendPong(ByteBuffer applicationData) {
        // TODO Auto-generated method stub
    }
}
