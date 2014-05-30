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
package websocket.echo;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

import javax.websocket.OnMessage;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/websocket/echoAsyncAnnotation")
public class EchoAsyncAnnotation {

    StringBuilder sb = null;
    ByteArrayOutputStream bytes = null;

    @OnMessage
    public void echoTextMessage(Session session, String msg, boolean last)
            throws IOException {
        if (sb == null) {
            sb = new StringBuilder();
        }
        sb.append(msg);
        if (last) {
            //System.out.println("Write: " + sb.length());
            session.getAsyncRemote().sendText(sb.toString());
            sb = null;
        }
    }

    @OnMessage
    public void echoBinaryMessage(byte[] msg, Session session, boolean last)
            throws IOException {
        if (bytes == null) {
            bytes = new ByteArrayOutputStream();
        }
        bytes.write(msg);
        //System.out.println("Got: " + msg.length + " " + last + " " + bytes.size());
        if (last) {
            //System.out.println("Write bytes: " + bytes.size());
            session.getAsyncRemote().sendBinary(ByteBuffer.wrap(bytes.toByteArray()));
            bytes = null;
        }
    }

    /**
     * Process a received pong. This is a NO-OP.
     *
     * @param pm    Ignored.
     */
    @OnMessage
    public void echoPongMessage(PongMessage pm) {
        // NO-OP
    }
}
